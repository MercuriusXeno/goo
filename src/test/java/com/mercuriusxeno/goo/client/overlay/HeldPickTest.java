package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * While a Grab press holds the mob it picked, the glove's aim stays on that
 * mob and outlines no other; before the hold lands and once the press lets
 * go, the frame's own resolve names the target
 * (decision outlined-target-is-the-one-pick).
 */
class HeldPickTest {

    private final Mob slime = mob(new AABB(0, 0, 0, 2, 2, 2));
    private final Mob bystander = mob(new AABB(10, 0, 0, 11, 1, 1));
    private final HeldPick held = new HeldPick();

    @Test
    void aHeldMobStaysTheTargetWhenTheLookPassesAnother() {
        held.resolve(true, Set.of()::contains, () -> picking(slime));
        AimState.Resolution frame = held.resolve(true, Set.<Entity>of(slime)::contains, () -> picking(bystander));
        assertEquals(slime, aimedAt(frame), "The held slime should stay the target");
        assertEquals(slime, ((AimAssistResolver.AimHit.EntityHit) frame.hit()).entity(),
                "The held slime should be the one outlined");
    }

    @Test
    void whileHoldingNoOtherMobIsOutlinedEvenWithNothingUnderTheLook() {
        held.resolve(true, Set.of()::contains, () -> picking(slime));
        AimState.Resolution frame = held.resolve(true, Set.<Entity>of(slime)::contains,
                () -> AimState.Resolution.NOTHING);
        assertEquals(slime, aimedAt(frame));
    }

    @Test
    void beforeTheHoldLandsTheFramesOwnPickNamesTheTarget() {
        AimState.Resolution frame = held.resolve(true, Set.of()::contains, () -> picking(bystander));
        assertEquals(bystander, aimedAt(frame));
    }

    @Test
    void aReleasedPressLetsTheFramesOwnPickBack() {
        held.resolve(true, Set.of()::contains, () -> picking(slime));
        held.resolve(true, Set.<Entity>of(slime)::contains, () -> picking(bystander));
        AimState.Resolution frame = held.resolve(false, Set.<Entity>of(slime)::contains, () -> picking(bystander));
        assertEquals(bystander, aimedAt(frame));
    }

    @Test
    void aMobAnotherPressHoldsIsNeverPinned() {
        AimState.Resolution frame = held.resolve(true, Set.<Entity>of(slime)::contains, () -> picking(bystander));
        assertEquals(bystander, aimedAt(frame), "A slime this press never picked should not take the aim");
    }

    /**
     * A locked item, which wears Grab's ailment as a held mob does, leaves the
     * aim free to pick the next item (decision sweeping-grab-locks-every-item-the-highlight-passes).
     */
    @Test
    void aLockedItemLeavesTheAimFreeForTheNext() {
        ItemEntity locked = mock(ItemEntity.class);
        when(locked.isAlive()).thenReturn(true);
        when(locked.getBoundingBox()).thenReturn(new AABB(4, 0, 0, 4.25, 0.25, 0.25));
        held.resolve(true, Set.of()::contains, () -> picking(locked));
        AimState.Resolution frame = held.resolve(true, Set.<Entity>of(locked)::contains, () -> picking(bystander));
        assertEquals(bystander, aimedAt(frame), "A locked item should leave the aim to the next pick");
    }

    /** Whether this Grab press holds a mob now, whose aim stays on it wherever the crosshair turns (decision grab-hides-the-reticle-once-a-mob-is-held). */
    @Nested
    class Holds {

        @Test
        void aLandedHoldHoldsAMob() {
            held.resolve(true, Set.of()::contains, () -> picking(slime));
            held.resolve(true, Set.<Entity>of(slime)::contains, () -> picking(bystander));
            assertTrue(held.holds());
        }

        @Test
        void aPickBeforeTheHoldLandsHoldsNoMob() {
            held.resolve(true, Set.of()::contains, () -> picking(slime));
            assertFalse(held.holds());
        }

        @Test
        void aReleasedPressHoldsNoMob() {
            held.resolve(true, Set.of()::contains, () -> picking(slime));
            held.resolve(true, Set.<Entity>of(slime)::contains, () -> picking(bystander));
            held.resolve(false, Set.<Entity>of(slime)::contains, () -> picking(bystander));
            assertFalse(held.holds());
        }
    }

    private static Entity aimedAt(AimState.Resolution frame) {
        return frame.target() instanceof TargetResult.EntityTarget aimed ? aimed.entity() : null;
    }

    private static AimState.Resolution picking(Entity mob) {
        return new AimState.Resolution(TargetResult.entity(mob, mob.getBoundingBox().getCenter()),
                new AimAssistResolver.AimHit.EntityHit(mob));
    }

    private static Mob mob(AABB box) {
        Mob mob = mock(Mob.class);
        when(mob.isAlive()).thenReturn(true);
        when(mob.getBoundingBox()).thenReturn(box);
        return mob;
    }
}
