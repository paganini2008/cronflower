package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.chaconneai.openspreader.serialization.SerializationType;

/**
 * Locks the shipped defaults: sharding off, windowed loading at 5 minutes, UTC scheduling, and JDK
 * serialization. Changing any of these silently would change deployment behaviour. (The store kind is
 * not configured here — it is auto-detected, see {@link StoreTypeTests}.)
 *
 * @Description: CronsmithServerPropertiesTests
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
class CronsmithServerPropertiesTests {

    @Test
    void defaultsAreTheDocumentedOnes() {
        CronsmithServerProperties props = new CronsmithServerProperties();

        assertThat(props.isEnabled()).isTrue();

        assertThat(props.getStorage().getRequestTimeoutMillis()).isEqualTo(5000L);
        assertThat(props.getStorage().getSerialization()).isEqualTo(SerializationType.JDK);

        assertThat(props.getScheduler().getWindowMinutes()).isEqualTo(5);
        assertThat(props.getScheduler().getClaimIntervalSeconds()).isEqualTo(15);
        assertThat(props.getScheduler().getZone()).isEqualTo("UTC");
        assertThat(props.getScheduler().isSharding()).isFalse();

        assertThat(props.getDispatch().getConnectTimeoutMillis()).isEqualTo(3000);
        assertThat(props.getDispatch().getReadTimeoutMillis()).isEqualTo(10000);
        assertThat(props.getDispatch().getExecutorTtlMillis()).isEqualTo(90000L);
        assertThat(props.getDispatch().getMaxAwaitMillis()).isEqualTo(300000L);
        assertThat(props.getDispatch().getHeaders()).isEmpty();
    }
}
