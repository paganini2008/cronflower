package com.github.cronflow.springapp.server.jpa;

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
        String id = TaskDagEntity.idOf(application, graph);
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
