package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Signal's wave front runs out along the aim by its speed each tick held,
 * stopping at the range, and offers each block inside the cone behind the
 * front to the host's once-per-hold toggle, none beyond it
 * (decision signal-wave-toggles-each-device-once).
 */
class SignalWaveStepTest {

    /** An eye in the block (0, 2, 0) looking east along x to a range of eight. */
    private static final Vec3 EYE = new Vec3(0.5, 2.5, 0.5);
    private static final Vec3 RANGE_END = new Vec3(8.5, 2.5, 0.5);
    private static final double RANGE = 8;
    private static final double SPEED = 0.5;
    private static final double CONE = 12;
    private static final BlockPos NEAR_LEVER = new BlockPos(2, 2, 0);
    private static final BlockPos FAR_LEVER = new BlockPos(6, 2, 0);

    private static ChannelHost hostOnTick(int held) {
        ChannelHost host = mock(ChannelHost.class);
        when(host.kind()).thenReturn(HostKind.PLAYER);
        when(host.eye()).thenReturn(EYE);
        when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(RANGE_END, null, CONE, held)));
        return host;
    }

    private static void run(ChannelHost host) {
        ProgramBehavior.forHost(List.of(new SignalWaveStep(Expr.literal(SPEED))), HostKind.PLAYER).tick(host);
    }

    @Nested
    class Front {

        @Test
        void theFrontRunsOutBySpeedEachTick() {
            assertEquals(2.5, SignalWaveStep.frontAt(5, SPEED, RANGE));
        }

        @Test
        void theFrontStopsAtTheRange() {
            assertEquals(RANGE, SignalWaveStep.frontAt(40, SPEED, RANGE));
        }
    }

    @Nested
    class Reach {

        @Test
        void aBlockBehindTheFrontIsOffered() {
            ChannelHost host = hostOnTick(5);
            run(host);
            verify(host).toggleOnceThisHold(NEAR_LEVER);
        }

        @Test
        void aBlockPastTheFrontIsNotOfferedYet() {
            ChannelHost host = hostOnTick(5);
            run(host);
            verify(host, never()).toggleOnceThisHold(FAR_LEVER);
        }

        @Test
        void aBlockWithinRangeIsOfferedOnceTheFrontReachesIt() {
            ChannelHost host = hostOnTick(14);
            run(host);
            verify(host).toggleOnceThisHold(FAR_LEVER);
        }
    }
}
