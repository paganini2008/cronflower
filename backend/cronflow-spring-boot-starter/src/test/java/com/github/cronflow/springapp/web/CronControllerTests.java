package com.github.cronflow.springapp.web;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the cron preview endpoint: a valid cron, a YCRON expression, an ISO-8601 duration,
 * an invalid expression, count clamping, and a blank input.
 */
class CronControllerTests {

    private final CronController controller = new CronController(ZoneId.of("UTC"));

    @Test
    void previewsAValidCron() {
        Map<String, Object> out = controller.preview("0 0 12 * * ?", 3, "cron");
        assertThat(out.get("valid")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> next = (List<String>) out.get("next");
        assertThat(next).hasSize(3);
        assertThat(next.get(0)).contains("12:00");
    }

    @Test
    void previewsAYcronExpression() {
        // Noon on the 200th day of the year (parser = ycron).
        Map<String, Object> out = controller.preview("0 0 12 ? ? 200", 2, "ycron");
        assertThat(out.get("valid")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> next = (List<String>) out.get("next");
        assertThat(next).isNotEmpty();
        assertThat(next.get(0)).contains("12:00");
    }

    @Test
    void ycronExpressionIsRejectedByTheTraditionalParser() {
        // The same year-based expression is not valid traditional cron.
        Map<String, Object> out = controller.preview("0 0 12 ? ? 200", 5, "cron");
        assertThat(out.get("valid")).isEqualTo(false);
    }

    @Test
    void previewsAnIsoDuration() {
        Map<String, Object> out = controller.preview("PT1H", 2, "cron");
        assertThat(out.get("valid")).isEqualTo(true);
        assertThat((List<?>) out.get("next")).hasSize(2);
    }

    @Test
    void reportsAnInvalidExpression() {
        Map<String, Object> out = controller.preview("not a cron", 5, "cron");
        assertThat(out.get("valid")).isEqualTo(false);
        assertThat(out.get("error")).isNotNull();
    }

    @Test
    void clampsTheCountToTheAllowedRange() {
        assertThat((List<?>) controller.preview("0 0 12 * * ?", 999, "cron").get("next")).hasSizeLessThanOrEqualTo(20);
        assertThat((List<?>) controller.preview("0 0 12 * * ?", 0, "cron").get("next")).hasSize(1);
    }

    @Test
    void treatsBlankAsInvalid() {
        assertThat(controller.preview("", 5, "cron").get("valid")).isEqualTo(false);
    }
}
