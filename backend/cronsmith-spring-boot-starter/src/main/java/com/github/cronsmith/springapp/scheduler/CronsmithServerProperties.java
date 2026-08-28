package com.github.cronsmith.springapp.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.chaconneai.openspreader.serialization.SerializationType;
import lombok.Data;

/**
 * Configuration for the server (task trigger) side.
 *
 * @Description: CronsmithServerProperties
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@Data
@ConfigurationProperties("cronsmith.server")
public class CronsmithServerProperties {

    /** Master switch for the whole server side. */
    private boolean enabled = true;

    /**
     * Base path prefix for the cronsmith REST API (tasks, stats, cluster, executors, executions).
     * Applied via Spring MVC path-prefixing scoped to the cronsmith controllers only, so — unlike
     * {@code server.servlet.context-path} — it never moves Actuator ({@code /actuator}) or your own
     * controllers. Must start with {@code /}; blank or {@code /} serves the API at the root. The
     * console proxy and the executor's {@code cronsmith.client.server-api-prefix} must use the SAME
     * value.
     */
    private String apiPrefix = "/cronsmith";

    private final Storage storage = new Storage();

    private final Dispatch dispatch = new Dispatch();

    private final Scheduler scheduler = new Scheduler();

    /**
     * Windowed loading: how much of the schedule the leader keeps in memory. This is what lets a very
     * large number of tasks be supported — only the ones about to fire are held.
     */
    @Data
    public static class Scheduler {

        /**
         * Load only tasks due within this many minutes into the timing wheel; the rest stay in the
         * store and are claimed as they come due. {@code 0} loads the whole schedule (no windowing).
         */
        private int windowMinutes = 5;

        /** How often, in seconds, the leader claims newly-due tasks from the store. */
        private int claimIntervalSeconds = 15;

        /**
         * Group sharding — an opt-in optimization. When {@code true} <b>and</b> the storage is shared
         * ({@code cronsmith.server.storage.shared=true}), every node runs the scheduler and triggers
         * only the task groups that consistent-hash to it, spreading load and fail-over across the
         * cluster. When {@code false} (default), only the leader triggers, exactly as before. Turned on
         * without a shared store it warns and stays leader-only, since sharding relies on the shared
         * store's cluster-wide compare-and-set to keep a task from firing twice during re-sharding.
         */
        private boolean sharding = false;

        /**
         * The time zone the scheduler computes fire times in. It MUST be the same on every node —
         * default {@code UTC} — otherwise nodes in different zones (say a host and a UTC container)
         * disagree about when a task is due.
         */
        private String zone = "UTC";

    }

    /**
     * The cluster behaviour of the storage. The store <b>kind</b> itself is not configured here — it
     * is {@link com.github.cronsmith.springapp.scheduler.StoreType#IN_MEMORY} when there is no DataSource, else
     * auto-detected from the JDBC connection's product name — so a node-local store is never mistaken
     * for a shared one and the replicated/shared combination cannot be misconfigured. Within a DB,
     * JPA is used when an {@code EntityManagerFactory} is present, otherwise JOOQ — the classpath
     * decides, not configuration; if neither is available the server fails to start.
     */
    @Data
    public static class Storage {

        /**
         * How long a follower waits for the leader to answer a forwarded write, in milliseconds.
         * Set it longer than a leader election takes.
         */
        private long requestTimeoutMillis = 5000L;

        /**
         * Serialization for the write operations exchanged on the cluster channel. {@code JDK} needs
         * nothing extra; {@code KRYO} needs the Kryo library on the classpath. Uses openspreader's
         * {@code ObjectCodec}.
         */
        private SerializationType serialization = SerializationType.JDK;

    }

    /**
     * How the leader calls executors when a task fires.
     */
    @Data
    public static class Dispatch {

        /** Connect timeout, in milliseconds, for calls to an executor. */
        private int connectTimeoutMillis = 3000;

        /** Read timeout, in milliseconds, for calls to an executor. */
        private int readTimeoutMillis = 10000;

        /** An executor is dropped from the in-memory registry after this long without a heartbeat. */
        private long executorTtlMillis = 90000L;

        /**
         * Upper bound the leader waits for an executor's {@code completeTask} callback when the task
         * itself sets no timeout. A safety net against an executor that dies mid-run.
         */
        private long maxAwaitMillis = 300000L;

        /**
         * Extra HTTP headers to send on every call to an executor (dispatch and health check), e.g.
         * an {@code Authorization} token the executor requires.
         */
        private Map<String, String> headers = new LinkedHashMap<>();

        /**
         * How the leader picks which executor of an application runs a task:
         * {@code FIRST}/{@code LAST}/{@code ROUND_ROBIN} (default)/{@code RANDOM}/
         * {@code CONSISTENT_HASH} (sticky per task group)/{@code WEIGHTED} (by executor weight).
         */
        private RoutingStrategy routing = RoutingStrategy.ROUND_ROBIN;

    }

}
