package com.github.cronflow.springapp.dag;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.chaconneai.openspreader.dag.Reducer;

/**
 * Exercises every extra channel reducer cronflow registers (max/min/and/or/concatString/joinCsv),
 * including the null-identity branches. In the same package as {@link CronflowReducers} to reach its
 * package-private static factory methods.
 */
class CronflowReducersTests {

    @SuppressWarnings("unchecked")
    private static <T> T reduce(Reducer<?> r, Object a, Object b) {
        return (T) ((Reducer<Object>) r).reduce(a, b);
    }

    @Test
    void allRegistersTheSixReducersWithNames() {
        List<Reducer<?>> all = CronflowReducers.all();
        assertThat(all).hasSize(6);
        assertThat(all).extracting(Reducer::name)
                .containsExactly("max", "min", "and", "or", "concatString", "joinCsv");
    }

    @Test
    void maxKeepsLargerAndHandlesNulls() {
        Reducer<Number> r = CronflowReducers.max();
        assertThat((Number) reduce(r, 3, 7)).isEqualTo(7);
        assertThat((Number) reduce(r, 7, 3)).isEqualTo(7);
        assertThat((Number) reduce(r, null, 5)).isEqualTo(5);
        assertThat((Number) reduce(r, 5, null)).isEqualTo(5);
    }

    @Test
    void minKeepsSmallerAndHandlesNulls() {
        Reducer<Number> r = CronflowReducers.min();
        assertThat((Number) reduce(r, 3, 7)).isEqualTo(3);
        assertThat((Number) reduce(r, 7, 3)).isEqualTo(3);
        assertThat((Number) reduce(r, null, 5)).isEqualTo(5);
        assertThat((Number) reduce(r, 5, null)).isEqualTo(5);
    }

    @Test
    void andLogicalWithNullIdentityTrue() {
        Reducer<Boolean> r = CronflowReducers.and();
        assertThat((Boolean) reduce(r, true, true)).isTrue();
        assertThat((Boolean) reduce(r, true, false)).isFalse();
        assertThat((Boolean) reduce(r, false, true)).isFalse();
        assertThat((Boolean) reduce(r, null, true)).isTrue();
        assertThat((Boolean) reduce(r, true, null)).isTrue();
    }

    @Test
    void orLogicalWithNullIdentityFalse() {
        Reducer<Boolean> r = CronflowReducers.or();
        assertThat((Boolean) reduce(r, false, false)).isFalse();
        assertThat((Boolean) reduce(r, true, false)).isTrue();
        assertThat((Boolean) reduce(r, false, true)).isTrue();
        assertThat((Boolean) reduce(r, null, false)).isFalse();
        assertThat((Boolean) reduce(r, null, true)).isTrue();
    }

    @Test
    void concatStringAppendsTreatingNullAsEmpty() {
        Reducer<String> r = CronflowReducers.concatString();
        assertThat((String) reduce(r, "a", "b")).isEqualTo("ab");
        assertThat((String) reduce(r, null, "b")).isEqualTo("b");
        assertThat((String) reduce(r, "a", null)).isEqualTo("a");
    }

    @Test
    void joinCsvSkipsEmpties() {
        Reducer<String> r = CronflowReducers.joinCsv();
        assertThat((String) reduce(r, "a", "b")).isEqualTo("a,b");
        assertThat((String) reduce(r, "", "b")).isEqualTo("b");
        assertThat((String) reduce(r, null, "b")).isEqualTo("b");
        assertThat((String) reduce(r, "a", "")).isEqualTo("a");
        assertThat((String) reduce(r, "a", null)).isEqualTo("a");
    }
}
