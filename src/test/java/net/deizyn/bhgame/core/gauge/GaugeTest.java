package net.deizyn.bhgame.core.gauge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GaugeTest {

    @Test
    void movesAtItsRateAndClamps() {
        Gauge gauge = new Gauge(0, 0);
        gauge.set(100, 0.5F, 0.01F);
        assertEquals(0.6F, gauge.value(110), 1.0E-5);
        assertEquals(1.0F, gauge.value(1000), 1.0E-5);
        gauge.set(100, 0.5F, -0.01F);
        assertEquals(0.0F, gauge.value(1000), 1.0E-5);
    }

    @Test
    void predictsWhenItIsFull() {
        Gauge gauge = new Gauge(0, 0);
        gauge.set(0, 0.0F, 1.0F / 240.0F);
        assertEquals(240, gauge.tickReaching(0, 1.0F));
        gauge.setRate(120, -0.01F);
        assertEquals(Long.MAX_VALUE, gauge.tickReaching(120, 1.0F));
    }

    @Test
    void syncsOnChangeThenOnlyWhileMoving() {
        Gauge gauge = new Gauge(0, 0);
        gauge.set(0, 0.0F, 0.1F);
        assertTrue(gauge.needsSync(0, 20));
        gauge.markSynced(0);
        assertFalse(gauge.needsSync(10, 20));
        // full after 10 ticks: no longer moving, so no resync
        assertFalse(gauge.needsSync(40, 20));
        gauge.set(40, 0.2F, 0.001F);
        gauge.markSynced(40);
        assertTrue(gauge.needsSync(60, 20));
    }
}
