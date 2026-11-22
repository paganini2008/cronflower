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

import java.util.regex.Pattern;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Masks secret-looking values in the Actuator {@code /env} and {@code /configprops} endpoints (which
 * the console's System > Settings reads). Spring's default sanitizer does not catch our property names
 * such as {@code cronflow.ai.providers.*.apiKey} or {@code cronflow.security.jwt.secret}, so an API key
 * or signing secret would otherwise be returned in clear text. This closes that at the source; the
 * Settings page also redacts client-side as defence in depth.
 *
 * @Description: ActuatorSanitizerConfig
 * @Author: Fred Feng
 * @Date: 22/11/2026
 * @Version 1.0.0
 */
@Configuration
public class ActuatorSanitizerConfig {

    private static final Pattern SECRET = Pattern.compile(
            "(?i).*(api[-_]?key|secret|password|passwd|token|credential|private[-_]?key|signing).*");

    @Bean
    public SanitizingFunction cronflowSecretSanitizer() {
        return (SanitizableData data) -> {
            String key = data.getKey();
            if (key != null && SECRET.matcher(key).matches()) {
                return data.withSanitizedValue();
            }
            return data;
        };
    }
}
