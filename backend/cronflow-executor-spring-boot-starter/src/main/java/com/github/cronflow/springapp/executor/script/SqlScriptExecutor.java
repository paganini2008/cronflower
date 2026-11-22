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
package com.github.cronflow.springapp.executor.script;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a SQL script over JDBC against a named, pre-registered datasource (never a connection string from
 * the script). Statements are separated by {@code ;}; a query returns up to {@code maxRows} rows as JSON
 * under {@code result.result.rows}, an update returns its count. This is the Metabase and Redash model:
 * the boundary is the database account, so register read-only, limited accounts.
  *
 * @Description: SqlScriptExecutor
 * @Author: Fred Feng
 * @Date: 21/11/2026
 * @Version 1.0.0
 */
public class SqlScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(SqlScriptExecutor.class);

    private final CronflowScriptProperties props;

    public SqlScriptExecutor(CronflowScriptProperties props) {
        this.props = props;
    }

    /** Whether any datasource is configured, so SQL can run at all. */
    public boolean hasDatasource() {
        return props.getSql().getDatasources() != null && !props.getSql().getDatasources().isEmpty();
    }

    public ScriptResult run(ScriptSpec spec) {
        long start = System.currentTimeMillis();
        String dsName = spec.datasource() != null && !spec.datasource().isBlank()
                ? spec.datasource()
                : props.getSql().getDefaultDatasource();
        CronflowScriptProperties.Sql.Datasource ds = dsName == null ? null
                : props.getSql().getDatasources().get(dsName);
        if (ds == null) {
            return ScriptResults.error("No SQL datasource named '" + dsName + "'");
        }
        StringBuilder out = new StringBuilder();
        List<Object> statements = new ArrayList<>();
        try {
            if (ds.getDriver() != null && !ds.getDriver().isBlank()) {
                Class.forName(ds.getDriver());
            }
            try (Connection conn = DriverManager.getConnection(ds.getUrl(), ds.getUsername(),
                    ds.getPassword())) {
                conn.setReadOnly(ds.isReadOnly());
                for (String sql : splitStatements(spec.source())) {
                    try (Statement st = conn.createStatement()) {
                        st.setQueryTimeout(ds.getQueryTimeoutSeconds());
                        boolean isResultSet = st.execute(sql);
                        if (isResultSet) {
                            try (ResultSet rs = st.getResultSet()) {
                                List<Map<String, Object>> rows = readRows(rs, ds.getMaxRows());
                                statements.add(Map.of("rows", rows, "rowCount", rows.size()));
                                out.append("query -> ").append(rows.size()).append(" row(s)\n");
                            }
                        } else {
                            int n = st.getUpdateCount();
                            statements.add(Map.of("updated", n));
                            out.append("update -> ").append(n).append("\n");
                        }
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("result", Map.of("statements", statements));
            long dur = System.currentTimeMillis() - start;
            return new ScriptResult(true, 0, false, out.toString(), "", result, dur, null);
        } catch (Exception e) {
            log.warn("SQL script run failed for {} on datasource {}", spec.name(), dsName, e);
            return new ScriptResult(false, 1, false, out.toString(), e.toString(), null,
                    System.currentTimeMillis() - start, e.toString());
        }
    }

    private List<Map<String, Object>> readRows(ResultSet rs, int maxRows) throws Exception {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next() && rows.size() < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    /** Naive splitter on {@code ;} at statement ends. Good enough for simple scripts (slice 1). */
    private List<String> splitStatements(String source) {
        List<String> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (String part : source.split(";")) {
            String s = part.strip();
            if (!s.isBlank() && !s.startsWith("--")) {
                out.add(s);
            }
        }
        return out;
    }
}
