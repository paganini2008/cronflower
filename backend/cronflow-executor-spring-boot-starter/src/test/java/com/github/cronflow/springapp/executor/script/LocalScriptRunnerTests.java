package com.github.cronflow.springapp.executor.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Exercises the local (host-process) runner end-to-end for shell and python: result.json, the input
 *  contract, timeout and non-zero exit. */
class LocalScriptRunnerTests {

    private final ObjectMapper mapper = new ObjectMapper();

    private LocalScriptRunner runner() {
        CronflowScriptProperties p = new CronflowScriptProperties();
        p.setEnabled(true);
        p.setRunner("local");
        return new LocalScriptRunner(p, mapper, null);
    }

    private static boolean has(String bin) {
        try {
            return new ProcessBuilder(bin, "--version").redirectErrorStream(true).start()
                    .waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void shellTaskReturnsResultAndStdout() {
        assumeTrue(has("bash"));
        ScriptSpec spec = ScriptSpec.task(ScriptLanguage.SHELL,
                "echo \"hi $SCRIPT_RUN_ID\"\n" + "echo '{\"result\":\"ok\"}' > result.json\n",
                "run-1", "demo/hello", 0, 10_000L, "world", null);
        ScriptResult r = runner().run(spec);
        assertThat(r.success()).isTrue();
        assertThat(r.exitCode()).isZero();
        assertThat(r.stdout()).contains("hi run-1");
        assertThat(r.resultValue()).isEqualTo("ok");
    }

    @Test
    void shellNonZeroExitIsFailure() {
        assumeTrue(has("bash"));
        ScriptSpec spec = ScriptSpec.task(ScriptLanguage.SHELL, "echo boom >&2\nexit 3\n",
                "run-2", "demo/fail", 0, 10_000L, null, null);
        ScriptResult r = runner().run(spec);
        assertThat(r.success()).isFalse();
        assertThat(r.exitCode()).isEqualTo(3);
        assertThat(r.stderr()).contains("boom");
        assertThat(r.failureDetail()).contains("exit=3");
    }

    @Test
    void shellTimeoutIsKilled() {
        assumeTrue(has("bash"));
        ScriptSpec spec = ScriptSpec.task(ScriptLanguage.SHELL, "sleep 10\n",
                "run-3", "demo/slow", 0, 400L, null, null);
        ScriptResult r = runner().run(spec);
        assertThat(r.timedOut()).isTrue();
        assertThat(r.success()).isFalse();
    }

    @Test
    void pythonNodeReadsChannelsWritesChannels() {
        assumeTrue(has("python3"));
        String src = "import json\n"
                + "d = json.load(open('input.json'))\n"
                + "v = d['channels']['input']\n"
                + "json.dump({'channels': {'score': 40, 'echoed': v}}, open('result.json','w'))\n"
                + "print('scored', v)\n";
        ScriptSpec spec = ScriptSpec.node(ScriptLanguage.PYTHON, src, "run-4", "flow/score",
                Map.of("input", "applicant-7"), 10_000L, null);
        ScriptResult r = runner().run(spec);
        assertThat(r.success()).isTrue();
        assertThat(r.stdout()).contains("scored applicant-7");
        assertThat(r.channelUpdates()).containsEntry("echoed", "applicant-7");
        assertThat(r.channelUpdates()).containsEntry("score", 40);
    }

    @Test
    void supportsReflectsAllowlist() {
        CronflowScriptProperties p = new CronflowScriptProperties();
        p.setLanguages(List.of("shell"));
        LocalScriptRunner r = new LocalScriptRunner(p, mapper, null);
        assertThat(r.supports(ScriptLanguage.SHELL)).isTrue();
        assertThat(r.supports(ScriptLanguage.PYTHON)).isFalse(); // not in allowlist
        assertThat(r.supports(ScriptLanguage.SQL)).isFalse(); // no datasource
    }
}
