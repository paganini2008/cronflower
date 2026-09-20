/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cronflow.springapp.security;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Login and role based authorization for the Cronflow server.
 *
 * <p>
 * Authentication is a stateless HMAC signed JWT (bearer token): a client posts credentials to
 * {@code /auth/login}, gets a token, and sends it as {@code Authorization: Bearer <token>} on every
 * call. Because the token is self contained and every node signs with the SAME secret
 * ({@code cronflow.security.jwt.secret}), any node behind the round-robin console proxy accepts it, so
 * there are no server side sessions to pin.
 *
 * <p>
 * Roles (authorities carry the {@code ROLE_} prefix):
 * <ul>
 * <li>{@code ADMIN} manages cronsmith, cronflow, system and dashboard (everything).</li>
 * <li>{@code SCHEDULER_ADMIN} manages cronsmith and views the dashboard.</li>
 * <li>{@code WORKFLOW_ADMIN} manages cronflow and views the dashboard.</li>
 * <li>{@code USER} views the dashboard only.</li>
 * </ul>
 *
 * <p>
 * Only the human console API is protected. The executor -> scheduler machine endpoints (register,
 * heartbeat, execution callback for both cronsmith and cronflow) stay OPEN, because they are driven by
 * the cronsmith / cronflow executor starters which are not modified here. A shared bearer for those
 * endpoints is left as an extension point: both ends would have to agree on it.
 *
 * @Description: SecurityConfig
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    static final String ADMIN = "ADMIN";
    static final String SCHEDULER_ADMIN = "SCHEDULER_ADMIN";
    static final String WORKFLOW_ADMIN = "WORKFLOW_ADMIN";
    static final String USER = "USER";

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final SecurityProperties properties;
    private final ResourceLoader resourceLoader;

    /** Business API prefixes; matched on the real request path (the console calls the full path). */
    private final String cronsmithPrefix;
    private final String cronflowPrefix;

    public SecurityConfig(SecurityProperties properties, ResourceLoader resourceLoader,
            @Value("${cronsmith.server.api-prefix:/cronsmith}") String cronsmithPrefix,
            @Value("${cronflow.server.api-prefix:/cronflow}") String cronflowPrefix) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.cronsmithPrefix = normalize(cronsmithPrefix, "/cronsmith");
        this.cronflowPrefix = normalize(cronflowPrefix, "/cronflow");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(cors -> {
        }).sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Allow the console to embed same-origin pages (the Swagger UI in System -> API) in a
                // frame; the default DENY blocks even same-origin framing.
                .headers(h -> h.frameOptions(fo -> fo.sameOrigin()));

        // Dev escape hatch: cronflow.security.enabled=false leaves the whole API open.
        if (!properties.isEnabled()) {
            http.authorizeHttpRequests(reg -> reg.anyRequest().permitAll());
            return http.build();
        }

        final String cs = cronsmithPrefix;
        final String cf = cronflowPrefix;
        http.authorizeHttpRequests(reg -> reg
                // CORS preflight.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Login and public docs / probes.
                .requestMatchers("/auth/login", "/auth/logout").permitAll()
                // Health + info + the Prometheus scrape endpoint are public (LB discovery, K8s probes,
                // and a Grafana/Prometheus scraper all pull them without a user token).
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                .permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Machine endpoints (executor -> scheduler) stay OPEN.
                .requestMatchers(HttpMethod.POST, cs + "/executors/register",
                        cs + "/executors/heartbeat", cs + "/executions/complete").permitAll()
                .requestMatchers(HttpMethod.POST, cf + "/dags/register", cf + "/dags/heartbeat")
                .permitAll()
                // The scheduled-trigger task (a data-only HTTP task) calls /fire with no user token.
                .requestMatchers(HttpMethod.POST, cf + "/dags/*/fire").permitAll()
                // Per-node self-health is fanned out to node-to-node with no user token.
                .requestMatchers(HttpMethod.GET, cf + "/nodes/self-health").permitAll()
                // System (actuator beyond health/info) is admin only.
                .requestMatchers("/actuator/**").hasRole(ADMIN)
                // cronsmith management (writes) -> admin or scheduler_admin.
                .requestMatchers(HttpMethod.POST, cs + "/**").hasAnyRole(ADMIN, SCHEDULER_ADMIN)
                .requestMatchers(HttpMethod.PUT, cs + "/**").hasAnyRole(ADMIN, SCHEDULER_ADMIN)
                .requestMatchers(HttpMethod.DELETE, cs + "/**").hasAnyRole(ADMIN, SCHEDULER_ADMIN)
                // Scheduling a DAG trigger creates a task in the scheduler's task list, so a
                // scheduler_admin may do it as well as a workflow_admin.
                .requestMatchers(HttpMethod.POST, cf + "/dags/*/schedule")
                .hasAnyRole(ADMIN, WORKFLOW_ADMIN, SCHEDULER_ADMIN)
                // cronflow management (writes) -> admin or workflow_admin.
                .requestMatchers(HttpMethod.POST, cf + "/**").hasAnyRole(ADMIN, WORKFLOW_ADMIN)
                .requestMatchers(HttpMethod.PUT, cf + "/**").hasAnyRole(ADMIN, WORKFLOW_ADMIN)
                .requestMatchers(HttpMethod.DELETE, cf + "/**").hasAnyRole(ADMIN, WORKFLOW_ADMIN)
                // Reads (dashboard, lists, run detail, /auth/me) -> any signed in user.
                .anyRequest().authenticated());

        http.oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthConverter())));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cronflow.server.cors-origins:*}") String origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(Arrays.asList(origins.split(",")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        // Bearer token travels in the Authorization header (not cookies), so credentials stay off and
        // the "*" origin pattern is allowed to work.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // Cronflower has no self-registration: accounts come from the user store XML
        // (cronflow.security.users-file). Fall back to the demo accounts only if it cannot be read, so
        // the app never boots with zero users.
        List<UserDetails> users = loadUsers(passwordEncoder);
        if (users.isEmpty()) {
            log.warn("cronflow security: no users loaded from '{}'; seeding demo accounts",
                    properties.getUsersFile());
            users = demoUsers(passwordEncoder);
        }
        log.info("cronflow security: loaded {} account(s) from {}", users.size(),
                properties.getUsersFile());
        return new InMemoryUserDetailsManager(users);
    }

    /**
     * Loads accounts from the user store XML ({@code cronflow.security.users-file}). Format:
     * {@code <users><user username=".." password=".." roles="admin,.."/></users>}. Returns an empty
     * list (never throws) if the resource is missing or unparseable, so the caller can fall back.
     */
    private List<UserDetails> loadUsers(PasswordEncoder encoder) {
        Resource resource = resourceLoader.getResource(properties.getUsersFile());
        if (!resource.exists()) {
            log.warn("cronflow security: user store '{}' not found", properties.getUsersFile());
            return List.of();
        }
        List<UserDetails> users = new ArrayList<>();
        try (InputStream in = resource.getInputStream()) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // XXE hardening: no DOCTYPE, no external entities.
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            Document doc = dbf.newDocumentBuilder().parse(in);
            NodeList nodes = doc.getElementsByTagName("user");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String username = el.getAttribute("username").trim();
                if (username.isEmpty()) {
                    continue;
                }
                users.add(buildUser(username, el.getAttribute("password"),
                        el.getAttribute("roles").split(","), encoder));
            }
        } catch (Exception e) {
            log.error("cronflow security: failed to parse user store '{}': {}",
                    properties.getUsersFile(), e.toString());
            return List.of();
        }
        return users;
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Derives a fixed length HS256 key from the configured secret via SHA-256, so any secret length is
     * accepted while the key always meets the 256 bit minimum.
     */
    private SecretKey secretKey() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // Last-resort fallback, used ONLY if the user store XML cannot be read (see userDetailsService),
    // so the app never boots with zero accounts. The real accounts come from users.xml.
    private static List<UserDetails> demoUsers(PasswordEncoder encoder) {
        return List.of(buildUser("admin", "admin123", new String[] {ADMIN}, encoder),
                buildUser("scheduler_admin", "scheduler_admin", new String[] {SCHEDULER_ADMIN}, encoder),
                buildUser("workflow_admin", "workflow_admin", new String[] {WORKFLOW_ADMIN}, encoder),
                buildUser("user", "user", new String[] {USER}, encoder));
    }

    // Encodes a raw password (or passes through an already-encoded {id} value) and maps roles to
    // ROLE_ authorities.
    private static UserDetails buildUser(String username, String password, String[] roles,
            PasswordEncoder encoder) {
        String pw = password == null ? "" : password;
        String encoded = pw.startsWith("{") ? pw : encoder.encode(pw);
        String[] authorities = Arrays.stream(roles).map(String::trim).filter(r -> !r.isEmpty())
                .map(r -> "ROLE_" + r.toUpperCase()).toArray(String[]::new);
        return User.withUsername(username).password(encoded).authorities(authorities).build();
    }

    private static String normalize(String prefix, String fallback) {
        String p = prefix == null ? "" : prefix.trim();
        if (p.isEmpty() || "/".equals(p)) {
            return fallback;
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
