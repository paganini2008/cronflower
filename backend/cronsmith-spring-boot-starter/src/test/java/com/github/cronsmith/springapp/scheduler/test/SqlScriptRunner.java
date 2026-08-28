package com.github.cronsmith.springapp.scheduler.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * 
 * Runs a bundled DDL script against a data source. Deliberately small: it strips line comments and
 * splits on semicolons, which is all the schema scripts under {@code db/} need, and nothing more so
 * that its behaviour is obvious.
 * 
 * @Description: SqlScriptRunner
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
abstract class SqlScriptRunner {

    static void runScript(DataSource dataSource, String resource) throws SQLException, IOException {
        // Line comments are stripped from the whole script before it is split on semicolons, not
        // after: a comment may itself contain a semicolon, and splitting first would cut the
        // comment in half and leave its tail looking like SQL.
        String sql = stripComments(readResource(resource));
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String rawStatement : sql.split(";")) {
                String singleStatement = rawStatement.trim();
                if (!singleStatement.isEmpty()) {
                    statement.execute(singleStatement);
                }
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }

    private static String stripComments(String script) {
        StringBuilder result = new StringBuilder();
        for (String line : script.split("\\R")) {
            int comment = line.indexOf("--");
            result.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return result.toString();
    }

    private static String readResource(String resource) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("No such resource on the classpath: " + resource);
            }
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append('\n');
                }
            }
            return content.toString();
        }
    }

}
