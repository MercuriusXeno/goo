package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An Iceborn player's snowball strikes with ice, aggravated, doubled,
 * against a fire-immune mob; the leech puts out the fire around the player
 * with the extinguish hiss and smoke, and burning things near them stop
 * burning. Its lava and water freezes reach Blocks, which the unit suite
 * cannot build, so no test here reaches them.
 */
class IcebornEventsTest {

    @Test
    void aSnowballStrikesWithIce() {
        assertEquals(IcebornEvents.SNOWBALL_ICE_DAMAGE, IcebornEvents.snowballDamage(false));
    }

    @Test
    void aSnowballStrikesAFireImmuneMobDoubly() {
        assertEquals(IcebornEvents.SNOWBALL_ICE_DAMAGE * 2, IcebornEvents.snowballDamage(true));
    }

    // frost-reach-extinguishes-fire-and-burning-mobs
    @Nested
    class TheLeech {

        private static final BlockPos STANDING = new BlockPos(10, 64, 10);
        private static final BlockPos FIRE = STANDING.offset(IcebornEvents.LEECH_RADIUS, 0, -1);

        private ServerPlayer icebornAt(BurningLevel world) {
            ServerPlayer player = mock(ServerPlayer.class);
            when(player.blockPosition()).thenReturn(STANDING);
            when(player.getBoundingBox()).thenReturn(new AABB(STANDING));
            return player;
        }

        @Test
        void putsOutFireInItsRadiusWithAHissAndSmoke() {
            try (BurningLevel world = new BurningLevel(Set.of(FIRE), List.of());
                 MockedStatic<IcebornFrozenBlocks> frozen = mockStatic(IcebornFrozenBlocks.class)) {
                IcebornEvents.leech(world.level(), icebornAt(world));

                world.assertPutOut(FIRE);
            }
        }

        @Test
        void aBurningEntityNearThePlayerStopsBurning() {
            Entity burning = BurningLevel.burningAt(0);
            try (BurningLevel world = new BurningLevel(Set.of(), List.of());
                 MockedStatic<IcebornFrozenBlocks> frozen = mockStatic(IcebornFrozenBlocks.class)) {
                ServerPlayer player = icebornAt(world);
                when(world.level().getEntities(eq(player), any(AABB.class))).thenReturn(List.of(burning));

                IcebornEvents.leech(world.level(), player);

                verify(burning).clearFire();
            }
        }
    }
}
