package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The watch step pulses the nearest kept entity's distance and finishes once
 * it stands within its trigger distance; only the marker hosts it
 * (decision lurker-blob-brightens-then-detonates).
 */
class WatchStepTest {

    private static final double RADIUS = 10.0;
    private static final double UNTIL = 3.0;
    private static final Set<EntityFilter> MOBS = Set.of(EntityFilter.LIVING, EntityFilter.MOB);
    private static final WatchStep WATCH = new WatchStep(Expr.literal(RADIUS), Expr.literal(UNTIL),
            List.of(EntityFilter.LIVING, EntityFilter.MOB));

    private static boolean tickWith(WatchHost host) {
        return WATCH.tick(new StepContext(host, 0, 0));
    }

    @Test
    void anEmptyWatchNeitherPulsesNorFinishes() {
        WatchHost host = mock(WatchHost.class);
        when(host.nearestEntityDistance(RADIUS, MOBS)).thenReturn(OptionalDouble.empty());

        assertFalse(tickWith(host));
        verify(host, never()).pulse(anyDouble(), anyDouble());
    }

    @Test
    void anEnemyOutsideTheTriggerPulsesAndKeepsWatching() {
        WatchHost host = mock(WatchHost.class);
        when(host.nearestEntityDistance(RADIUS, MOBS)).thenReturn(OptionalDouble.of(UNTIL + 0.01));

        assertFalse(tickWith(host));
        verify(host).pulse(eq(UNTIL + 0.01), eq(RADIUS));
    }

    @Test
    void anEnemyAtTheTriggerPulsesAndFinishes() {
        WatchHost host = mock(WatchHost.class);
        when(host.nearestEntityDistance(RADIUS, MOBS)).thenReturn(OptionalDouble.of(UNTIL));

        assertTrue(tickWith(host));
        verify(host).pulse(eq(UNTIL), eq(RADIUS));
    }

    @Test
    void onlyTheMarkerHostsAWatch() {
        List<Step> steps = List.of(WATCH);

        assertDoesNotThrow(() -> ProgramBehavior.forHost(steps, HostKind.MARKER));
        assertThrows(ProgramLoadException.class, () -> ProgramBehavior.forHost(steps, HostKind.LANDING));
    }
}
