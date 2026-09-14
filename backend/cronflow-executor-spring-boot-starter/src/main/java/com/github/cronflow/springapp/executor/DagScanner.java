package com.github.cronflow.springapp.executor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import com.github.cronsmith.springapp.executor.Task;

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

        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (RuntimeException e) {
                continue; // not instantiable / lazy-broken; skip like cronsmith's TaskRegistry does
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            Dag dag = AnnotatedElementUtils.findMergedAnnotation(targetClass, Dag.class);
            if (dag == null) {
                continue;
            }
            String graph = dag.name().isBlank() ? targetClass.getSimpleName() : dag.name();
            dags.add(weaveAnnotated(beanName, targetClass, dag, graph));
            triggers.addAll(bindings(beanName, targetClass, graph));
        }

        // Programmatic graphs. TODO: a programmatic way to declare their @Task trigger binding too.
        for (CronflowDag builder : applicationContext.getBeansOfType(CronflowDag.class).values()) {
            dags.add(builder.build());
        }

        log.info("cronflow: wove {} DAG definition(s), {} trigger binding(s)", dags.size(),
                triggers.size());
        return new ScanResult(dags, triggers);
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

    private DagDefinition weaveAnnotated(String beanName, Class<?> targetClass, Dag dag, String graph) {
        List<String> inputs = List.of(dag.inputs());
        List<DagDefinition.ChannelDef> channels = new ArrayList<>();
        for (Channel c : dag.channels()) {
            channels.add(new DagDefinition.ChannelDef(c.name(), c.reducer()));
        }

        List<DagDefinition.NodeDef> nodes = new ArrayList<>();
        List<DagDefinition.EdgeDef> edges = new ArrayList<>();
        List<DagDefinition.ConditionalDef> conditionals = new ArrayList<>();

        for (Method method : ReflectionUtils.getAllDeclaredMethods(targetClass)) {
            DagNode dn = AnnotatedElementUtils.findMergedAnnotation(method, DagNode.class);
            if (dn == null) {
                continue;
            }
            String node = dn.name().isBlank() ? method.getName() : dn.name();
            nodes.add(new DagDefinition.NodeDef(node, dn.entry(), dn.trigger(), dn.retries(), beanName,
                    method.getName(), dn.subgraph()));
            for (String t : dn.to()) {
                edges.add(new DagDefinition.EdgeDef(node, t, "ON_SUCCESS", null));
            }
            for (String t : dn.onFailure()) {
                edges.add(new DagDefinition.EdgeDef(node, t, "ON_FAILURE", null));
            }
            for (String t : dn.onComplete()) {
                edges.add(new DagDefinition.EdgeDef(node, t, "ON_COMPLETE", null));
            }
            if (dn.when().length > 0 || dn.otherwise().length > 0) {
                List<String> predicates = new ArrayList<>();
                Map<String, List<String>> branches = new LinkedHashMap<>();
                for (int i = 0; i < dn.when().length; i++) {
                    predicates.add(dn.when()[i].expr());
                    branches.put(String.valueOf(i), List.of(dn.when()[i].to()));
                }
                conditionals.add(new DagDefinition.ConditionalDef(List.of(node), "predicate", null,
                        predicates, branches, List.of(dn.otherwise())));
            }
        }
        return new DagDefinition(graph, inputs, channels, nodes, edges, conditionals);
    }

}
