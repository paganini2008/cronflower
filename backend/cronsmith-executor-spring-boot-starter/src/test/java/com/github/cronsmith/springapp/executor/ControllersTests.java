package com.github.cronsmith.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Covers the two thin controllers directly, no web server involved.
 */
class ControllersTests {

    @Test
    void pingReturnsPong() {
        assertThat(new CronsmithPingController().ping()).isEqualTo("PONG");
    }

    @Test
    void runAcceptsAndDispatches() {
        AtomicReference<RunRequest> seen = new AtomicReference<>();
        CronsmithClientController controller = new CronsmithClientController(seen::set);
        RunRequest request =
                new RunRequest("e", "g", "t", "C", "b", "m", null, 0, -1L, null);

        ResponseEntity<Void> response = controller.run(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(seen.get()).isSameAs(request);
    }

}
