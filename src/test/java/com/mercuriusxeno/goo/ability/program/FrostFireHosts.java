package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.OptionalDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds a marker host frost pulses from, standing in a given level, and
 * ticks one frost step on it through its program.
 */
final class FrostFireHosts {

    private FrostFireHosts() {
    }

    /**
     * A marker host whose frost spreads out of a center in a level; its
     * entity scan selects nothing.
     *
     * @param level  the level
     * @param center the frost's center
     * @return the host
     */
    static MarkerHost frostingFrom(ServerLevel level, Vec3 center) {
        MarkerHost host = mock(MarkerHost.class);
        when(host.kind()).thenReturn(HostKind.MARKER);
        when(host.read(anyString())).thenReturn(OptionalDouble.empty());
        when(host.level()).thenReturn(level);
        when(host.frostCenter()).thenReturn(center);
        return host;
    }

    /**
     * Ticks one step once on a host through its program.
     *
     * @param step the step
     * @param host the host
     */
    static void tickOnce(Step step, MarkerHost host) {
        ProgramBehavior.forHost(List.of(step), HostKind.MARKER).tick(host);
    }
}
