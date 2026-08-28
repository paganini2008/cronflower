package com.github.cronsmith.springapp.scheduler.examples;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;
import com.github.cronsmith.springapp.scheduler.Task;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;
import com.github.cronsmith.springapp.scheduler.jooq.JooqTaskManager;

/**
 *
 * A runnable example of the persistent scheduler. It stores tasks in an embedded H2 database through
 * the JOOQ task manager, so the schedule would survive a restart against a real database. The task
 * is a plain {@link Task} whose class is stored by name, which is what lets it be rebuilt from a
 * stored row after a restart.
 *
 * <p>
 * Swap the H2 {@code DataSource} for PostgreSQL, MySQL or SQLite and nothing else changes; only run
 * the matching schema script from {@code db/} first.
 *
 * <p>
 * Run {@link #main(String[])} from your IDE, or:
 *
 * <pre>
 * mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.github.cronsmith.springapp.scheduler.examples.PersistentTaskExample
 * </pre>
 *
 * @Description: PersistentTaskExample
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class PersistentTaskExample {

    /**
     *
     * A plain business object, unaware of the scheduler, called by name. This is the shape real user
     * code takes; the scheduler reaches {@link #sendReport(String)} reflectively.
     *
     * @Description: ReportService
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class ReportService {

        public String sendReport(String parameter) {
            System.out.println("  [run] sending report for " + parameter);
            return "sent";
        }

    }

    /**
     *
     * A persistable task: it names the class and method to run rather than carrying a body, so it can
     * be stored as a row and rebuilt from it.
     *
     * @Description: ReportTask
     * @Author: Fred Feng
     * @Date: 24/08/2026
     * @Version 1.0.0
     */
    public static class ReportTask implements Task {

        @Override
        public TaskId getTaskId() {
            return TaskId.of("reports", "sales");
        }

        @Override
        public CronExpression getCronExpression() {
            return CRON.parse("* * * * * ?");   // every second, to keep the demo short
        }

        @Override
        public String getInitialParameter() {
            return "2026-Q3";
        }

        @Override
        public Object execute(String initialParameter) {
            return new ReportService().sendReport(initialParameter);
        }

    }

    public static void main(String[] args) throws Exception {
        // 1. A DataSource. Here an embedded H2; in production this is your connection pool.
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cronsmith_demo;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        // 2. Create the cs_* tables once, from the bundled schema script for this dialect.
        runSchema(dataSource, "db/cronsmith-schema-h2.sql");

        // 3. Point the scheduler at a JOOQ task manager over that DataSource.
        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.setTaskManager(new JooqTaskManager(dataSource));   // dialect detected automatically

        // 4. Register and run. The task is now a row in cs_task_detail and would be restored on the
        //    next start() if this process were restarted.
        scheduler.schedule(new ReportTask());
        scheduler.start();
        System.out.println("persistent scheduler started; running for 5 seconds...");
        Thread.sleep(5000L);

        // 5. Read the durable state back.
        System.out.println("runs recorded in the database: "
                + scheduler.getTaskManager().getTaskDetail(TaskId.of("reports", "sales"), false)
                        .getRunCount());
        scheduler.close();
        System.out.println("scheduler closed.");
    }

    /**
     * Runs a bundled DDL script: strips line comments and executes each statement.
     */
    private static void runSchema(JdbcDataSource dataSource, String resource) throws Exception {
        StringBuilder sql = new StringBuilder();
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf("--");
                sql.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
            }
        }
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String single : sql.toString().split(";")) {
                if (!single.trim().isEmpty()) {
                    statement.execute(single);
                }
            }
        }
    }

}
