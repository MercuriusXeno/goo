package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Heal's block step wakes each statue standing in the stream's cone and
 * leaves every other block, and every statue off the cone, alone
 * (decision heal-wakes-a-statue-at-a-nearly-full-gauge).
 */
class WakeStatueStepTest {

    private static final Vec3 EYE = new Vec3(0.5, 0.5, 0.5);
    private static final Vec3 REACH = new Vec3(6.5, 0.5, 0.5);
    private static final double CONE = 50;
    private static final BlockPos STATUE_ON_AXIS = new BlockPos(3, 0, 0);
    private static final BlockPos STATUE_OFF_CONE = new BlockPos(3, 4, 0);
    private static final BlockPos STONE_ON_AXIS = new BlockPos(2, 0, 0);

    private final ChannelHost host = mock(ChannelHost.class);

    @BeforeEach
    void aimTheStreamAlongTheAxis() {
        when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(REACH, null, CONE, ChannelAim.FIRST_TICK)));
        when(host.eye()).thenReturn(EYE);
        when(host.statueAt(STATUE_ON_AXIS)).thenReturn(true);
        when(host.statueAt(STATUE_OFF_CONE)).thenReturn(true);
    }

    @Test
    void wakesTheStatueInTheCone() {
        new WakeStatueStep().tick(new StepContext(host, 0, 0));
        verify(host).wakeStatue(STATUE_ON_AXIS);
    }

    @Test
    void leavesAStatueOffTheConeAndEveryOtherBlock() {
        new WakeStatueStep().tick(new StepContext(host, 0, 0));
        verify(host, never()).wakeStatue(STATUE_OFF_CONE);
        verify(host, never()).wakeStatue(STONE_ON_AXIS);
    }

    @Test
    void wakesNothingOutsideAHeldChannel() {
        when(host.channelAim()).thenReturn(Optional.empty());
        new WakeStatueStep().tick(new StepContext(host, 0, 0));
        verify(host, never()).wakeStatue(any());
    }
}
