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
package com.github.cronflow.springapp.server.jpa;

import com.github.cronflow.springapp.server.DagIds;
import com.github.cronflow.springapp.server.DagStore;
import com.github.cronflow.springapp.server.pojo.StoredDag;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * JPA-backed {@link DagStore}, using the {@link EntityManager} directly (as cronsmith's JpaTaskManager
 * does) so no Spring Data repository scanning has to be enabled in the host application.
 *
 * @Description: JpaDagStore
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class JpaDagStore implements DagStore {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void save(String application, String graph, String definition, String format) {
        String id = DagIds.taskDagId(application, graph);
        TaskDagEntity entity = entityManager.find(TaskDagEntity.class, id);
        if (entity == null) {
            entity = new TaskDagEntity();
            entity.setId(id);
            entity.setApplication(application);
            entity.setGraph(graph);
        }
        entity.setDefinition(definition);
        entity.setFormat(format);
        entity.setEnabled(true);
        entity.setLastModified(LocalDateTime.now());
        entityManager.merge(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredDag> loadAll() {
        return entityManager.createQuery("select d from TaskDagEntity d", TaskDagEntity.class)
                .getResultList().stream()
                .map(d -> new StoredDag(d.getApplication(), d.getGraph(), d.getDefinition(),
                        d.getFormat()))
                .toList();
    }

}
