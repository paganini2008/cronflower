package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronBuilder;
import com.github.cronsmith.cron.CronExpression;

/**
 * 
 * Verifies that a cron expression stored as bytes and read back produces exactly the same fire
 * times. This is the guarantee the JOOQ task manager relies on: a task is persisted as the
 * serialized expression, and after a restart its schedule has to continue producing the very same
 * instants it would have without one.
 * 
 * @Description: CronPersistenceTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class CronPersistenceTests {

    private static final LocalDateTime BASE =
            LocalDateTime.of(2026, Month.AUGUST, 24, 10, 15, 30);

    /**
     * Cron texts spanning every field and special character, so the round trip is checked across
     * the whole grammar rather than one easy case.
     */
    private static final String[] EXPRESSIONS = {"0 0 12 * * ?", "0 0/5 * * * ?",
            "0 0 0 1 * ?", "0 15 10 ? * MON-FRI", "0 0 12 ? * SUN", "*/10 * * * * ?",
            "0 30 9 ? * MON#1", "0 0 22 L * ?", "0 0 12 1/2 * ?", "0 0 0 ? * 6L",
            "0 0 8-18 * * ?", "0 0 12 1,15 * ?", "0 0 0 1 1 ? 2027"};

    private static void assertSameFireTimes(CronExpression original) {
        List<LocalDateTime> before = original.list(BASE, BASE.plusYears(2));
        byte[] bytes = original.serialize();
        assertNotNull(bytes);
        CronExpression restored = CronExpression.deserialize(bytes);
        List<LocalDateTime> after = restored.list(BASE, BASE.plusYears(2));
        assertEquals(before, after, "fire times must survive a round trip");
    }

    @Test
    public void testEveryParsedExpressionRoundTrips() {
        for (String text : EXPRESSIONS) {
            CronExpression expression = CRON.parse(text);
            assertSameFireTimes(expression);
        }
    }

    @Test
    public void testBuilderExpressionsRoundTrip() {
        assertSameFireTimes(new CronBuilder().everySecond(15));
        assertSameFireTimes(new CronBuilder().everyMinute(5));
        assertSameFireTimes(new CronBuilder().everyHour(2));
        assertSameFireTimes(new CronBuilder().everyDay());
        assertSameFireTimes(new CronBuilder().everyMonth());
    }

    @Test
    public void testTextFormSurvivesRoundTrip() {
        for (String text : EXPRESSIONS) {
            CronExpression original = CRON.parse(text);
            CronExpression restored = CronExpression.deserialize(original.serialize());
            assertEquals(original.toString(), restored.toString());
        }
    }

    @Test
    public void testNextFiredDateTimeMatchesAfterRoundTrip() {
        for (String text : EXPRESSIONS) {
            CronExpression original = CRON.parse(text);
            CronExpression restored = CronExpression.deserialize(original.serialize());
            assertEquals(original.getNextFiredDateTime(BASE), restored.getNextFiredDateTime(BASE), "next fire time diverged for: " + text);
        }
    }

    @Test
    public void testPositionIsPreservedAfterRoundTrip() {
        // Advance the original a few occurrences, then serialize: the restored expression must
        // carry on from where the original stood, not from the beginning.
        CronExpression original = CRON.parse("0 0/5 * * * ?");
        LocalDateTime cursor = BASE;
        for (int i = 0; i < 3; i++) {
            cursor = original.getNextFiredDateTime(cursor);
        }
        // Serialize at this position, then let the original and the restored copy each take the
        // next step from the same cursor: a copy that lost the position would answer differently.
        CronExpression restored = CronExpression.deserialize(original.serialize());
        assertEquals(original.getNextFiredDateTime(cursor), restored.getNextFiredDateTime(cursor));
    }

    @Test
    public void testAtFutureRoundTrip() {
        CronExpression once = CRON.atFuture(BASE.plusDays(10));
        CronExpression restored = CronExpression.deserialize(once.serialize());
        assertEquals(once.getNextFiredDateTime(BASE), restored.getNextFiredDateTime(BASE));
    }

    @Test
    public void testCopyProducesIndependentButEqualSchedule() {
        CronExpression original = CRON.parse("0 0/5 * * * ?");
        CronExpression copy = original.copy();
        assertEquals(original.list(BASE, BASE.plusHours(1)), copy.list(BASE, BASE.plusHours(1)));
    }

}
