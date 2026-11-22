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
package com.github.cronflow.springapp.ai;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import com.github.cronflow.springapp.dag.ClusterDagRegistry;
import com.github.cronflow.springapp.dag.DagDefinitionCodec;
import com.github.cronflow.springapp.dag.DagExecutorRegistry;
import com.github.cronflow.springapp.dag.DagStore;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires AI DAG generation, only when {@code cronflow.ai.enabled=true}. It builds one
 * {@link OpenAiCompatibleProvider} per configured provider (private Ollama, OpenAI cloud, or any
 * compatible server), the validator, the generator, the node catalog from the registered DAGs, and the
 * REST controller. With the feature off, none of these beans exist and the engine is unaffected.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cronflow.ai", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CronflowAiProperties.class)
public class CronflowAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DagValidator cronflowDagValidator() {
        return new DagValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiDagGenerator cronflowAiDagGenerator(CronflowAiProperties props, DagValidator validator,
            ObjectMapper mapper, ObjectProvider<RestClient.Builder> builder) {
        List<LlmProvider> providers = new ArrayList<>();
        props.getProviders().forEach((id, cfg) -> {
            RestClient.Builder b = builder.getIfAvailable(RestClient::builder).clone();
            providers.add(new OpenAiCompatibleProvider(id, cfg, b));
        });
        return new AiDagGenerator(providers, props, validator, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public NodeCatalogProvider cronflowNodeCatalogProvider(DagStore dagStore, DagDefinitionCodec codec) {
        return new StoredDagNodeCatalogProvider(dagStore, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiGenerationStore cronflowAiGenerationStore(ObjectProvider<DataSource> dataSource) {
        DataSource ds = dataSource.getIfAvailable();
        return ds != null ? new JooqAiGenerationStore(ds) : new InMemoryAiGenerationStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiDagController cronflowAiDagController(AiDagGenerator generator,
            NodeCatalogProvider catalogProvider, DagValidator validator, DagDefinitionCodec codec,
            ClusterDagRegistry clusterRegistry, DagExecutorRegistry registry, AiGenerationStore store) {
        return new AiDagController(generator, catalogProvider, validator, codec, clusterRegistry,
                registry, store);
    }
}
