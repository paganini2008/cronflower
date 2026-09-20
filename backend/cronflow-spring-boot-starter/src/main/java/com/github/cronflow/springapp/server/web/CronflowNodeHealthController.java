package com.github.cronflow.springapp.server.web;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.cronflow.springapp.server.CronflowServerProperties;

/**
 * Per-node health for the console: each scheduler node's liveness plus its live JVM memory (heap used
 * / committed / max), which the aggregate {@code /actuator/health} does not carry.
 *
 * <p>
 * {@code /nodes/self-health} reports the node that answers it. {@code /nodes/health} fans out to every
 * cluster member's self-health (over the same in-network host:port the proxy and executors use) and
 * returns one row per node, so the dashboard can show the whole cluster's memory at a glance. Self is
 * reached like any other member, so a single code path covers the local and remote nodes.
 *
 * @Description: CronflowNodeHealthController
 * @Author: Fred Feng
 * @Date: 20/09/2026
 * @Version 1.0.0
 */
@RestController
public class CronflowNodeHealthController {

    private static final Logger log = LoggerFactory.getLogger(CronflowNodeHealthController.class);

    private final GossipCluster cluster;
    private final CronflowServerProperties properties;
    private final String cronflowPrefix;
    private final RestClient restClient;

    public CronflowNodeHealthController(GossipCluster cluster,
            CronflowServerProperties cronflowProperties) {
        this.cluster = cluster;
        this.properties = cronflowProperties;
        this.cronflowPrefix = normalizePrefix(cronflowProperties.getApiPrefix());
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.restClient = RestClient.builder().requestFactory(f).build();
    }

    /** Deployment metadata for the console header: the environment label (dev / prod) and app name. */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Node self = cluster.self();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("env", properties.getEnv());
        m.put("application", self != null ? self.name() : null);
        return m;
    }

    /** This node's own liveness + live JVM heap. Open (a machine endpoint the aggregator calls). */
    @GetMapping("/nodes/self-health")
    public Map<String, Object> selfHealth() {
        Node self = cluster.self();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", self != null ? self.id() : null);
        out.put("name", self != null ? self.name() : null);
        out.put("host", self != null ? self.host() : null);
        out.put("httpPort", self != null ? parseInt(self.metadata("server.port")) : null);
        out.put("leader", self != null && cluster.leader() != null
                && self.id().equals(cluster.leader().id()));
        out.put("status", "UP");
        out.put("memory", memory());
        out.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        out.put("processors", Runtime.getRuntime().availableProcessors());
        return out;
    }

    /** One row per cluster member: its self-health, or a DOWN row when it cannot be reached. */
    @GetMapping("/nodes/health")
    public List<Map<String, Object>> nodesHealth() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Node leader = cluster.leader();
        for (Node n : cluster.members()) {
            Integer httpPort = parseInt(n.metadata("server.port"));
            String base = "http://" + n.host() + ":" + httpPort + cronflowPrefix;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body =
                        restClient.get().uri(base + "/nodes/self-health").retrieve().body(Map.class);
                if (body != null) {
                    rows.add(body);
                    continue;
                }
                rows.add(down(n, leader, "empty response"));
            } catch (RuntimeException e) {
                log.debug("cronflow: node {} self-health unreachable: {}", n.host(), e.toString());
                rows.add(down(n, leader, e.getClass().getSimpleName()));
            }
        }
        return rows;
    }

    private static Map<String, Object> down(Node n, Node leader, String error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", n.id());
        row.put("name", n.name());
        row.put("host", n.host());
        row.put("httpPort", parseInt(n.metadata("server.port")));
        row.put("leader", leader != null && n.id().equals(leader.id()));
        row.put("status", "DOWN");
        row.put("error", error);
        return row;
    }

    private static Map<String, Object> memory() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        long used = heap.getUsed();
        long committed = heap.getCommitted();
        long max = heap.getMax();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usedMb", toMb(used));
        m.put("committedMb", toMb(committed));
        m.put("maxMb", max > 0 ? toMb(max) : null);
        m.put("usagePct", max > 0 ? Math.round(used * 1000.0 / max) / 10.0 : null);
        return m;
    }

    private static long toMb(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizePrefix(String prefix) {
        String p = prefix == null ? "" : prefix.trim();
        if (p.isEmpty() || p.equals("/")) {
            return "";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
