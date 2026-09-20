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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Login and identity endpoints for the Cronflow console. These live at the root ({@code /auth/**}),
 * outside the cronsmith / cronflow business API prefixes, and are the ONLY way a client obtains a bearer
 * token.
 *
 * <p>
 * The flow is stateless: {@code POST /auth/login} verifies the credentials and mints an HMAC signed JWT
 * carrying the user's roles; the client then sends it as {@code Authorization: Bearer <token>}.
 * {@code GET /auth/me} echoes the current identity from the token, and {@code POST /auth/logout} is a no
 * op the client calls before discarding the token (there is no server side session to invalidate).
 *
 * @Description: AuthController
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "auth", description = "Login and identity for the Cronflow console")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final long ttlMinutes;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
            SecurityProperties properties) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.ttlMinutes = properties.getJwt().getTtlMinutes();
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in and receive a bearer token")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.username(), request.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(AuthController::stripRolePrefix).toList();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttlMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("cronflow").issuedAt(now)
                .expiresAt(expiresAt).subject(auth.getName()).claim("roles", roles).build();
        // Sign with HS256 explicitly so the encoder selects the symmetric (HMAC) key.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token =
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", auth.getName(), roles,
                ttlMinutes * 60));
    }

    @GetMapping("/me")
    @Operation(summary = "Return the identity encoded in the current bearer token")
    public ResponseEntity<UserInfo> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return ResponseEntity.status(401).build();
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(a -> a.startsWith("ROLE_"))
                .map(AuthController::stripRolePrefix).toList();
        return ResponseEntity.ok(new UserInfo(jwt.getSubject(), roles));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout (stateless): the client discards its token")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    private static String stripRolePrefix(String authority) {
        return authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : authority;
    }

    /** Login credentials. */
    public record LoginRequest(String username, String password) {
    }

    /** Minted token plus the identity it carries. */
    public record LoginResponse(String token, String tokenType, String username, List<String> roles,
            long expiresInSeconds) {
    }

    /** Current identity echoed from the token. */
    public record UserInfo(String username, List<String> roles) {
    }
}
