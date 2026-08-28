package com.github.cronsmith.springapp.scheduler.web;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronsmith.CRON;
import com.github.cronsmith.cron.CronExpression;

/**
 * Tests a schedule expression without saving anything: parses a cron (or an ISO-8601 duration,
 * anything starting with {@code P}) and returns the next few fire times. Powers the schedule
 * builder's "Test" button.
 *
 * @Description: CronController
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
@RestController
@RequestMapping("/cron")
public class CronController {

    private final ZoneId zoneId;

    public CronController(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam String expr,
            @RequestParam(defaultValue = "5") int count) {
        Map<String, Object> out = new LinkedHashMap<>();
        String e = expr == null ? "" : expr.trim();
        CronExpression cron;
        try {
            cron = e.length() > 1 && (e.charAt(0) == 'P' || e.charAt(0) == 'p')
                    ? CRON.every(Duration.parse(e))
                    : CRON.parse(e);
        } catch (Exception ex) {
            out.put("valid", false);
            out.put("error", messageOf(ex));
            return out;
        }
        List<String> next = new ArrayList<>();
        try {
            LocalDateTime cursor = LocalDateTime.now(zoneId);
            int n = Math.min(20, Math.max(1, count));
            for (int i = 0; i < n; i++) {
                cursor = cron.getNextFiredDateTime(cursor);
                if (cursor == null) {
                    break;
                }
                next.add(cursor.toString());
            }
        } catch (Exception ex) {
            out.put("valid", false);
            out.put("error", messageOf(ex));
            return out;
        }
        out.put("valid", true);
        out.put("next", next);
        return out;
    }

    private static String messageOf(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
