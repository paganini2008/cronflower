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
package com.github.cronflow.springapp.executor.script;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the script script runner on an executor, only when {@code cronflow.script.enabled=true}. The runner
 * is Docker-first ({@code cronflow.script.runner=docker}, a per-run container) with a {@code local}
 * host-process option. The resulting {@link ScriptRunner} is auto-injected (via a {@code @Autowired}
 * setter) into the task and node execution services, so a SCRIPT task or script DAG node runs through it.
 * With the feature off, no runner bean exists and a script dispatch fails cleanly.
  *
 * @Description: CronflowScriptAutoConfiguration
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cronflow.script", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CronflowScriptProperties.class)
public class CronflowScriptAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqlScriptExecutor sqlScriptExecutor(CronflowScriptProperties props) {
        return new SqlScriptExecutor(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScriptRunner scriptRunner(CronflowScriptProperties props,
            ObjectProvider<ObjectMapper> mapper, SqlScriptExecutor sql) {
        ObjectMapper om = mapper.getIfAvailable(ObjectMapper::new);
        if ("local".equalsIgnoreCase(props.getRunner())) {
            return new LocalScriptRunner(props, om, sql);
        }
        return new DockerScriptRunner(props, om, sql);
    }
}
