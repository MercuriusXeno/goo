package com.mercuriusxeno.goo.ability.root;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What crossing Vines goo is, a thing's center over a cell's column, and
 * the fire the vines feed while they hold.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * vines-unpack-root-and-thorn
 */
class RootEventsTest {

    private static final BlockPos CELL = new BlockPos(3, 64, 3);

    @Nested
    class Crossing {

        @Test
        void aThingWhoseCenterStandsInTheCellsColumnIsOverIt() {
            assertTrue(RootEvents.over(CELL, new Vec3(3.9, 70, 3.1)));
        }

        @Test
        void aThingWhoseCenterStandsBesideTheCellIsNotOverIt() {
            assertFalse(RootEvents.over(CELL, new Vec3(4.05, 64, 3.5)));
        }
    }

    @Nested
    class Damage {

        private static DamageSource fire() {
            DamageSource source = mock(DamageSource.class);
            when(source.is(DamageTypeTags.IS_FIRE)).thenReturn(true);
            return source;
        }

        private static LivingEntity mob(boolean fireImmune) {
            LivingEntity living = mock(LivingEntity.class);
            when(living.fireImmune()).thenReturn(fireImmune);
            return living;
        }

        @Test
        void fireToAMobThatBurnsIsFed() {
            assertTrue(RootEvents.feedsFire(mob(false), fire()));
        }

        @Test
        void fireToAFireImmuneMobIsNotFed() {
            assertFalse(RootEvents.feedsFire(mob(true), fire()));
        }

        @Test
        void damageThatIsNotFireIsNotFed() {
            assertFalse(RootEvents.feedsFire(mob(false), mock(DamageSource.class)));
        }
    }
}
