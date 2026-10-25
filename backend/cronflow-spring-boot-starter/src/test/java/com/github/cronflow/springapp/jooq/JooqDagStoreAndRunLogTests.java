package com.github.cronflow.springapp.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import com.github.cronflow.springapp.pojo.DagNodeView;
import com.github.cronflow.springapp.pojo.DagRunView;
import com.github.cronflow.springapp.pojo.StoredDag;

/**
 * Exercises the jOOQ DAG storage against an in-memory H2 database (never a networked one). The stores
 * create their own portable schema, so no DDL script is needed.
 */
class JooqDagStoreAndRunLogTests {

    private static DataSource h2(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    void storeInsertsUpdatesAndLoads() {
        JooqDagStore store = new JooqDagStore(h2("jooq_store"));
        store.save("app", "g1", "{\"graph\":\"g1\"}", "json");
        store.save("app", "g1", "{\"graph\":\"g1\",\"v\":2}", "json"); // update path
        store.save("app", "g2", "{\"graph\":\"g2\"}", "yaml");
        List<StoredDag> all = store.loadAll();
        assertThat(all).hasSize(2);
        StoredDag g1 = all.stream().filter(d -> d.graph().equals("g1")).findFirst().orElseThrow();
        assertThat(g1.definition()).contains("\"v\":2");
        assertThat(g1.application()).isEqualTo("app");
    }

    @Test
    void runLogRecordsAndReads() {
        JooqDagRunLog log = new JooqDagRunLog(h2("jooq_runlog"));
        LocalDateTime now = LocalDateTime.now();
        log.begin("r1", null, "app", "g", "task", now, "{}");
        log.node("r1", "g", "n1", 0, "SUCCESS", "{}", "{\"o\":1}", "exec", 5L, null);
        log.node("r1", "g", "n2", 1, "FAILED", "{}", null, "exec", 6L, "err");
        log.finish("r1", "SUCCESS", now.plusSeconds(1), 1000L, 2, null, null, "{\"done\":true}");
        log.begin("r2", "r1", "app", "child", "subgraph", now, "{}");
        log.finish("r2", "SUCCESS", now.plusSeconds(1), 5L, 1, null, null, "{}");

        DagRunView run = log.findRun("r1");
        assertThat(run).isNotNull();
        assertThat(run.status()).isEqualTo("SUCCESS");
        assertThat(run.nodeCount()).isEqualTo(2);
        assertThat(run.returnValue()).contains("done");
        assertThat(log.findRun("missing")).isNull();

        List<DagNodeView> nodes = log.nodesOf("r1");
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).node()).isEqualTo("n1");
        assertThat(nodes.get(1).errorDetail()).isEqualTo("err");

        assertThat(log.listRuns(null, null, null, 50, 0)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(log.listRuns("app", "g", "SUCCESS", 50, 0)).hasSize(1);
        assertThat(log.countRuns("app", "g", "SUCCESS")).isEqualTo(1);
        assertThat(log.countRuns(null, null, null)).isGreaterThanOrEqualTo(2);
        assertThat(log.childRuns("r1")).hasSize(1);
        assertThat(log.childRuns("none")).isEmpty();
    }
}
