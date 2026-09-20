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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login and authorization settings for the Cronflow server, bound from {@code cronflow.security.*}.
 *
 * <p>
 * The token is a stateless HMAC signed JWT: the same {@link Jwt#getSecret() secret} on every node lets
 * any node in the cluster accept a token minted by any other node, which is what the round-robin console
 * proxy needs. Cronflower has no self-registration: accounts are provisioned in an XML file
 * ({@link #getUsersFile() users-file}); the packaged {@code classpath:users.xml} is the dev default and
 * can be overridden by an external file without rebuilding.
 *
 * @Description: SecurityProperties
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
@ConfigurationProperties(prefix = "cronflow.security")
public class SecurityProperties {

    /** Master switch. When false, Spring Security is not wired and the whole API is open (dev only). */
    private boolean enabled = true;

    private final Jwt jwt = new Jwt();

    /**
     * Location of the user store XML (Spring resource syntax). Defaults to the packaged
     * {@code classpath:users.xml}; point it at an external file for a real deployment, e.g.
     * {@code file:/opt/cronflow/conf/users.xml}.
     */
    private String usersFile = "classpath:users.xml";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public String getUsersFile() {
        return usersFile;
    }

    public void setUsersFile(String usersFile) {
        this.usersFile = usersFile;
    }

    /** Bearer token (JWT) signing settings. */
    public static class Jwt {

        /**
         * HMAC (HS256) signing secret. MUST be the same on every scheduler node and MUST be overridden
         * in production (see application-prod.properties). Needs to be at least 32 bytes for HS256.
         */
        private String secret = "cronflow-dev-secret-change-me-please-0123456789abcdef";

        /** Token lifetime in minutes. */
        private long ttlMinutes = 720;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(long ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }
}
