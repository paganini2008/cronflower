package com.github.cronflow.springapp.executor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import com.github.cronsmith.springapp.executor.Task;
import com.github.cronflow.springapp.executor.pojo.DagDefinition;
import com.github.cronflow.springapp.executor.pojo.TriggerBinding;

/**
 * Weaves DAG definitions on the executor at startup, from both styles: the {@link Dag}/{@link DagNode}
 * annotations and programmatic {@link CronflowDag} beans. It also derives the task→DAG trigger
 * bindings by the simplest possible rule: a cronsmith {@code @Task} method that sits in a {@code @Dag}
 * class triggers that class's graph (its return value seeds the run). No separate trigger annotation.
 *
 * <p>
 * The executor is a callee: this only <b>describes</b> graphs and bindings — it never runs them.
 *
 * @Description: DagScanner
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class DagScanner {

    private static final Logger log = LoggerFactory.getLogger(DagScanner.class);

    private final ApplicationContext applicationContext;
    private final String applicationName;

    public DagScanner(ApplicationContext applicationContext, String applicationName) {
        this.applicationContext = applicationContext;
        this.applicationName = applicationName;
    }

    /** The weaving result: every graph definition, and every task→graph trigger binding. */
    public record ScanResult(List<DagDefinition> dags, List<TriggerBinding> triggers) {}

    public ScanResult scan() {
        List<DagDefinition> dags = new ArrayList<>();
        List<TriggerBinding> triggers = new ArrayList<>();
        Map<String, GraphAcc> graphs = new LinkedHashMap<>();

        // Pass 1: each @Dag class declares a graph (name, inputs, channels) and weaves its own @DagNode
        // methods into it. This is the single place a graph's shape lives.
        for (String beanName : beanNames()) {
            Class<?> targetClass = targetClass(beanName);
            if (targetClass == null) {
                continue;
            }
            Dag dag = AnnotatedElementUtils.findMergedAnnotation(targetClass, Dag.class);
            if (dag == null) {
                continue;
            }
            String graph = dag.name().isBlank() ? targetClass.getSimpleName() : dag.name();
            GraphAcc acc = graphs.computeIfAbsent(graph, GraphAcc::new);
            acc.declare(dag);
            for (Method method : ReflectionUtils.getAllDeclaredMethods(targetClass)) {
                DagNode dn = AnnotatedElementUtils.findMergedAnnotation(method, DagNode.class);
                if (dn != null) {
                    weaveNode(acc, dn, beanName, method);
                }
            }
            triggers.addAll(bindings(beanName, targetClass, graph));
        }

        // Pass 2: a @DagNode method in ANY bean that names a graph joins that graph — so one graph can
        // span several service beans (the node runs on the bean+method where it is declared).
        for (String beanName : beanNames()) {
            Class<?> targetClass = targetClass(beanName);
            if (targetClass == null) {
                continue;
            }
            for (Method method : ReflectionUtils.getAllDeclaredMethods(targetClass)) {
                DagNode dn = AnnotatedElementUtils.findMergedAnnotation(method, DagNode.class);
                if (dn == null || dn.graph().isBlank()) {
                    continue; // only external contributors carry an explicit graph()
                }
                GraphAcc acc = graphs.get(dn.graph());
                if (acc == null) {
                    log.warn("cronflow: @DagNode on {}.{} names unknown graph '{}' (no @Dag declares "
                            + "it) — skipped", beanName, method.getName(), dn.graph());
                    continue;
                }
                weaveNode(acc, dn, beanName, method); // dedup by node name inside weaveNode
            }
        }

        for (GraphAcc acc : graphs.values()) {
            dags.add(acc.toDefinition());
        }

        // Programmatic graphs (and their task→graph bindings declared via CronflowDag.triggeredBy).
        for (CronflowDag builder : applicationContext.getBeansOfType(CronflowDag.class).values()) {
            dags.add(builder.build());
            triggers.addAll(builder.triggerBindings());
        }

        log.info("cronflow: wove {} DAG definition(s), {} trigger binding(s)", dags.size(),
                triggers.size());
        return new ScanResult(dags, triggers);
    }

    private String[] beanNames() {
        return applicationContext.getBeanDefinitionNames();
    }

    private Class<?> targetClass(String beanName) {
        try {
            return AopUtils.getTargetClass(applicationContext.getBean(beanName));
        } catch (RuntimeException e) {
            return null; // not instantiable / lazy-broken; skip like cronsmith's TaskRegistry does
        }
    }

    /** A cronsmith @Task in a @Dag class triggers that graph (group/name taken from @Task). */
    private List<TriggerBinding> bindings(String beanName, Class<?> targetClass, String graph) {
        List<TriggerBinding> triggers = new ArrayList<>();
        for (Method method : ReflectionUtils.getAllDeclaredMethods(targetClass)) {
            Task task = AnnotatedElementUtils.findMergedAnnotation(method, Task.class);
            if (task == null) {
                continue;
            }
            String group = task.group().isBlank() ? applicationName : task.group();
            String name = task.name().isBlank() ? beanName + "." + method.getName() : task.name();
            triggers.add(new TriggerBinding(group, name, graph));
        }
        return triggers;
    }

    /** Weave one @DagNode method into a graph accumulator (deduping by node name). */
    private void weaveNode(GraphAcc acc, DagNode dn, String declaringBean, Method method) {
        String node = dn.name().isBlank() ? method.getName() : dn.name();
        if (!acc.addNodeName(node)) {
            return; // already contributed (e.g. by the @Dag class) — first declaration wins
        }
        // A node runs on its declaring bean+method, unless bean()/method() delegate it elsewhere.
        String nodeBean = dn.bean().isBlank() ? declaringBean : dn.bean();
        String nodeMethod = dn.method().isBlank() ? method.getName() : dn.method();
        acc.nodes.add(new DagDefinition.NodeDef(node, dn.entry(), dn.trigger(), dn.retries(), nodeBean,
                nodeMethod, dn.subgraph()));
        for (String t : dn.to()) {
            acc.edges.add(new DagDefinition.EdgeDef(node, t, "ON_SUCCESS", null));
        }
        for (String t : dn.onFailure()) {
            acc.edges.add(new DagDefinition.EdgeDef(node, t, "ON_FAILURE", null));
        }
        for (String t : dn.onComplete()) {
            acc.edges.add(new DagDefinition.EdgeDef(node, t, "ON_COMPLETE", null));
        }
        if (dn.when().length > 0 || dn.otherwise().length > 0) {
            List<String> predicates = new ArrayList<>();
            Map<String, List<String>> branches = new LinkedHashMap<>();
            for (int i = 0; i < dn.when().length; i++) {
                predicates.add(dn.when()[i].expr());
                branches.put(String.valueOf(i), List.of(dn.when()[i].to()));
            }
            acc.conditionals.add(new DagDefinition.ConditionalDef(List.of(node), "predicate", null,
                    predicates, branches, List.of(dn.otherwise())));
        }
        if (dn.shard().length > 0) {
            Shard s = dn.shard()[0];
            acc.shards.add(new DagDefinition.ShardDef(node, s.input(), s.output(), s.size()));
        }
    }

    /** Mutable accumulator for a graph woven from possibly several beans, then flattened to a definition. */
    private static final class GraphAcc {
        private final String graph;
        private List<String> inputs = List.of();
        private final List<DagDefinition.ChannelDef> channels = new ArrayList<>();
        private final List<DagDefinition.NodeDef> nodes = new ArrayList<>();
        private final List<DagDefinition.EdgeDef> edges = new ArrayList<>();
        private final List<DagDefinition.ConditionalDef> conditionals = new ArrayList<>();
        private final List<DagDefinition.ShardDef> shards = new ArrayList<>();
        private final Set<String> nodeNames = new LinkedHashSet<>();

        GraphAcc(String graph) {
            this.graph = graph;
        }

        /** Set the graph's inputs and channels from its @Dag class. */
        void declare(Dag dag) {
            this.inputs = List.of(dag.inputs());
            for (Channel c : dag.channels()) {
                // customReducer (a server-side Reducer bean) wins over the built-in enum when set.
                String reducer =
                        c.customReducer().isBlank() ? c.reducer().reducerName() : c.customReducer();
                channels.add(new DagDefinition.ChannelDef(c.name(), reducer));
            }
        }

        boolean addNodeName(String name) {
            return nodeNames.add(name);
        }

        DagDefinition toDefinition() {
            return new DagDefinition(graph, inputs, channels, nodes, edges, conditionals, shards);
        }
    }

}
