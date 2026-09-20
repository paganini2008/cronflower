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
package com.github.cronsmith.springapp.example;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.github.cronflow.springapp.executor.Channel;
import com.github.cronflow.springapp.executor.ChannelReducer;
import com.github.cronflow.springapp.executor.Dag;
import com.github.cronflow.springapp.executor.DagNode;
import com.github.cronflow.springapp.executor.DagState;
import com.github.cronsmith.springapp.executor.Task;

/**
 * A parameter-heavy demo DAG that showcases <b>computing channel reducers</b>: several parallel scorers
 * each write a partial value into shared channels, and the fan-in node reads the values the reducers
 * have COMPUTED (not just collected). Watch the per-node Input/Output on the run detail to see the
 * parameters flow and the channels accumulate.
 *
 * <pre>
 *                intake  (seeds "applicant", "trail")
 *              /    |    \                fan-out (parallel)
 *        credit  income  collateral      each adds to: score(+SUM_INT), factors(+JOIN_CSV),
 *              \    |    /                              maxWeight(+MAX), trail(+CONCAT_LIST)
 *                 decide                 fan-in (Trigger ALL): reads the COMPUTED score -> approve/reject
 * </pre>
 *
 * Reducers used: {@code score} = SUM_INT (40+30+20 = 90), {@code maxWeight} = MAX (40),
 * {@code factors} = JOIN_CSV ("credit,income,collateral"), {@code trail} = CONCAT_LIST (the step log).
 */
@Dag(name = "scoring-flow", inputs = {"input"}, channels = {
        @Channel(name = "score", reducer = ChannelReducer.SUM_INT),
        @Channel(name = "maxWeight", reducer = ChannelReducer.MAX),
        @Channel(name = "factors", reducer = ChannelReducer.JOIN_CSV),
        @Channel(name = "trail", reducer = ChannelReducer.CONCAT_LIST)})
@Component
public class ScoringFlow {

    /** Triggers the DAG every 30s; its return value is the DAG's initial input (the applicant id). */
    @Task(cron = "0/30 * * * * ?", description = "kick off the scoring-flow DAG")
    public String kickoff() {
        return "applicant-42";
    }

    /** Entry: reads the applicant id from the initial input (the kickoff @Task return arrives under the
     *  "input" channel) and seeds the audit trail. */
    @DagNode(entry = true, to = {"credit", "income", "collateral"})
    public Map<String, Object> intake(DagState state) {
        pause();
        String applicant = state.getString("input");
        if (applicant == null || applicant.isBlank()) {
            applicant = "applicant-unknown";
        }
        return Map.of("applicant", applicant, "trail", List.of("intake:" + applicant));
    }

    /** Credit-history scorer: contributes 40 to the summed score. */
    @DagNode(to = {"decide"})
    public Map<String, Object> credit(DagState state) {
        pause();
        int points = 40;
        return Map.of("score", points, "maxWeight", points, "factors", "credit",
                "trail", List.of("credit:+" + points));
    }

    /** Income scorer: contributes 30. */
    @DagNode(to = {"decide"})
    public Map<String, Object> income(DagState state) {
        pause();
        int points = 30;
        return Map.of("score", points, "maxWeight", points, "factors", "income",
                "trail", List.of("income:+" + points));
    }

    /** Collateral scorer: contributes 20. */
    @DagNode(to = {"decide"})
    public Map<String, Object> collateral(DagState state) {
        pause();
        int points = 20;
        return Map.of("score", points, "maxWeight", points, "factors", "collateral",
                "trail", List.of("collateral:+" + points));
    }

    /** Fan-in (Trigger ALL): reads the COMPUTED channels and makes the decision. */
    @DagNode(trigger = "ALL")
    public Map<String, Object> decide(DagState state) {
        pause();
        long score = state.getLong("score");          // SUM_INT computed: 40 + 30 + 20 = 90
        long maxWeight = state.getLong("maxWeight");   // MAX computed: 40
        String factors = state.getString("factors");   // JOIN_CSV: "credit,income,collateral"
        if (factors == null) {
            factors = "";
        }
        String decision = score >= 70 ? "APPROVED" : "REJECTED";
        // Report the decision + the values the reducers COMPUTED. Note we do NOT write back to the
        // 'factors' channel here — the scorers already populated it; re-writing would double it.
        return Map.of("decision", decision, "totalScore", score, "topWeight", maxWeight,
                "usedFactors", factors, "trail", List.of("decide:" + decision + "(" + score + ")"));
    }

    /** Simulated work so each node stays RUNNING long enough to see the live pulse on the graph. */
    private void pause() {
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
