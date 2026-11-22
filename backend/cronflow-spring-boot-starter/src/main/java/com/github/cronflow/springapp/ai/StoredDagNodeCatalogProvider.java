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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.cronflow.springapp.dag.DagDefinitionCodec;
import com.github.cronflow.springapp.dag.DagStore;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagDefinition.NodeDef;
import com.github.cronflow.springapp.pojo.StoredDag;

/**
 * Builds the node catalog from the DAGs already registered in the cluster: every distinct bean node
 * across the stored definitions becomes a catalog entry the model may reuse. This is the Slice A source.
 * A later slice adds explicitly registered node descriptions and script-node templates.
 */
public class StoredDagNodeCatalogProvider implements NodeCatalogProvider {

    private static final Logger log = LoggerFactory.getLogger(StoredDagNodeCatalogProvider.class);

    private final DagStore dagStore;
    private final DagDefinitionCodec codec;

    public StoredDagNodeCatalogProvider(DagStore dagStore, DagDefinitionCodec codec) {
        this.dagStore = dagStore;
        this.codec = codec;
    }

    @Override
    public NodeCatalog catalog() {
        Map<String, CatalogNode> byName = new LinkedHashMap<>();
        List<StoredDag> stored;
        try {
            stored = dagStore.loadAll();
        } catch (Exception e) {
            log.warn("cronflow.ai: could not load DAGs for the node catalog", e);
            return NodeCatalog.of(List.of());
        }
        for (StoredDag sd : stored) {
            DagDefinition dag;
            try {
                dag = codec.decode(sd.definition(), sd.format());
            } catch (Exception e) {
                log.warn("cronflow.ai: skipping unparseable DAG '{}'", sd.graph(), e);
                continue;
            }
            if (dag.nodes() == null) {
                continue;
            }
            for (NodeDef n : dag.nodes()) {
                if (n == null || n.name() == null || n.name().isBlank()) {
                    continue;
                }
                if (n.subgraph() != null && !n.subgraph().isBlank()) {
                    continue; // subgraph references, not leaf nodes
                }
                if (n.beanName() == null || n.beanName().isBlank()) {
                    continue; // only real bean nodes are reusable in Slice A
                }
                byName.putIfAbsent(n.name(), new CatalogNode(n.name(),
                        "Node from graph '" + dag.graph() + "'", n.beanName(), n.methodName(),
                        new ArrayList<>(), new ArrayList<>()));
            }
        }
        return NodeCatalog.of(new ArrayList<>(byName.values()));
    }
}
