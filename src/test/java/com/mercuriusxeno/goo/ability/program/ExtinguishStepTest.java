package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.BurningLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cold's extinguish step puts out the fire blocks inside the stream's cone
 * with the extinguish hiss and smoke, leaves the fire beside the cone
 * burning, and clears the fire on each burning mob the cone reaches
 * (decision frost-reach-extinguishes-fire-and-burning-mobs).
 */
class ExtinguishStepTest {

    private static final Vec3 EYE = new Vec3(0.5, 64.5, 0.5);
    private static final Vec3 REACH = new Vec3(10.5, 64.5, 0.5);
    private static final double CONE = 16;
    private static final BlockPos FIRE_IN_CONE = new BlockPos(5, 64, 0);
    private static final BlockPos FIRE_BESIDE_CONE = new BlockPos(5, 64, 5);

    @Test
    void theConePutsOutItsFireAndTheBurningMobItReachesAndLeavesTheFireBesideIt() {
        LivingEntity burning = mock(LivingEntity.class);
        TargetHost reached = mock(TargetHost.class);
        when(reached.target()).thenReturn(burning);
        try (BurningLevel world = new BurningLevel(Set.of(FIRE_IN_CONE, FIRE_BESIDE_CONE), List.of())) {
            PlayerHost host = mock(PlayerHost.class);
            when(host.kind()).thenReturn(HostKind.PLAYER);
            when(host.read(anyString())).thenReturn(OptionalDouble.empty());
            when(host.level()).thenReturn(world.level());
            when(host.eye()).thenReturn(EYE);
            when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(REACH, null, CONE, ChannelAim.FIRST_TICK)));
            doAnswer(inv -> {
                List<BlockPos> cells = inv.getArgument(0);
                assertTrue(cells.contains(FIRE_IN_CONE), "the mob stands in the cone's cells");
                inv.<Consumer<TargetHost>>getArgument(2).accept(reached);
                return null;
            }).when(host).forEachLivingIn(any(), any(), any());

            ProgramBehavior.forHost(List.of(new ExtinguishStep()), HostKind.PLAYER).tick(host);

            world.assertPutOut(FIRE_IN_CONE);
            world.assertStillBurning(FIRE_BESIDE_CONE);
            verify(burning).clearFire();
        }
    }
}
