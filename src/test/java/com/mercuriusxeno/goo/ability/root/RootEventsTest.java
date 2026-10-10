package com.mercuriusxeno.goo.ability.root;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the vines do to a rooted mob each tick and hit: the drag back onto
 * the root, the stop at the leash's edge, the fire they feed and the thorns
 * that spend none of their hits (decision vines-unpack-root-and-thorn).
 */
class RootEventsTest {

    private static final Vec3 ROOT = new Vec3(10, 64, 10);
    private static final double EPSILON = 1e-9;

    @Nested
    class Leash {

        @Test
        void aMobWithinTheLeashIsLeftWhereItStands() {
            assertNull(RootEvents.pulledToLeash(ROOT, ROOT.add(0.6, 0, 0.6)));
        }

        @Test
        void aMobStandingOnTheRootIsNotDragged() {
            assertNull(RootEvents.springPull(ROOT, ROOT.add(0.1, 0, 0.1)));
        }

        @Test
        void aMobOffTheRootIsDraggedAShareOfTheWayBackAcrossTheGround() {
            Vec3 pull = RootEvents.springPull(ROOT, ROOT.add(0.8, 3, -0.4));
            assertEquals(-0.8 * RootEvents.PULL_SHARE, pull.x, EPSILON);
            assertEquals(0, pull.y, EPSILON);
            assertEquals(0.4 * RootEvents.PULL_SHARE, pull.z, EPSILON);
        }

        @Test
        void aMobPastTheLeashIsPulledBackToItsEdgeAtItsOwnHeight() {
            Vec3 pulled = RootEvents.pulledToLeash(ROOT, ROOT.add(3, 2, 4));
            assertEquals(RootEvents.LEASH_BLOCKS, Math.hypot(pulled.x - ROOT.x, pulled.z - ROOT.z), EPSILON);
            assertEquals(ROOT.x + 0.6 * RootEvents.LEASH_BLOCKS, pulled.x, EPSILON);
            assertEquals(ROOT.y + 2, pulled.y, EPSILON);
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

        @Test
        void theVinesOwnThornsAreTheSweetBerryDamage() {
            DamageSource thorn = mock(DamageSource.class);
            when(thorn.is(DamageTypes.SWEET_BERRY_BUSH)).thenReturn(true);
            assertTrue(RootEvents.isThorn(thorn));
            assertFalse(RootEvents.isThorn(mock(DamageSource.class)));
        }
    }
}
