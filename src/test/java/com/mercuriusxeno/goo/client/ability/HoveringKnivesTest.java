package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.KnifeFan;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' hovering fan on the client: a new knife emerges from the blob's
 * side toward the view's center and eases out to its slot, a knife formed
 * before the change eases from its old slot to its new one, and a count of
 * 0 drops the fan (decisions knives-emerge-from-the-blob-and-hover-in-a-fan,
 * knives-form-on-right-click-and-throw-on-left-click).
 */
class HoveringKnivesTest {

    private static final int CASTER = 7;
    private static final Vec3 EYE = new Vec3(0, 65, 0);
    private static final Vec3 LOOK_SOUTH = new Vec3(0, 0, 1);
    /** A blob floating to the glove side, west of the look facing south, below and ahead of the eye. */
    private static final Vec3 BLOB = new Vec3(-0.3, 64.8, 0.6);
    private static final double WITHIN = 1e-9;

    @AfterEach
    void clearTheFans() {
        HoveringKnives.clear();
    }

    @Test
    void theBlobsInnerSideFacesTheViewsCenterLine() {
        Vec3 inner = HoveringKnives.innerSideOf(BLOB, EYE, LOOK_SOUTH);

        Vec3 nearestOnTheLook = new Vec3(0, 65, 0.6);
        assertEquals(HoveringKnives.BLOB_REACH, inner.distanceTo(BLOB), WITHIN);
        assertTrue(inner.distanceTo(nearestOnTheLook) < BLOB.distanceTo(nearestOnTheLook),
                "the inner side " + inner + " should stand nearer the look than the blob " + BLOB);
    }

    @Test
    void aNewKnifeLeavesTheBlobsInnerSideAndReachesItsSlot() {
        HoveringKnives.formed(CASTER, 1, 0);
        HoveringKnives.Fan fan = HoveringKnives.fanOf(CASTER);
        Vec3 inner = HoveringKnives.innerSideOf(BLOB, EYE, LOOK_SOUTH);

        assertEquals(0, inner.distanceTo(HoveringKnives.knifeAt(fan, 0, EYE, LOOK_SOUTH, inner, 0)), WITHIN);
        assertEquals(0f, HoveringKnives.knifeScaleAt(fan, 0, 0), 1e-6f, "the knife grows out of the blob");
        Vec3 slot = KnifeFan.pointOf(EYE, LOOK_SOUTH, KnifeFan.slotOf(0, 1));
        assertEquals(0, slot.distanceTo(HoveringKnives.knifeAt(fan, 0, EYE, LOOK_SOUTH, inner,
                HoveringKnives.EASE_TICKS)), WITHIN);
        assertEquals(1f, HoveringKnives.knifeScaleAt(fan, 0, HoveringKnives.EASE_TICKS), 1e-6f);
    }

    @Test
    void aKnifeFormedBeforeTheNextStageEasesFromItsOldSlotToTheWiderFans() {
        HoveringKnives.formed(CASTER, 2, 0);
        HoveringKnives.formed(CASTER, 4, 12);
        HoveringKnives.Fan fan = HoveringKnives.fanOf(CASTER);
        Vec3 inner = HoveringKnives.innerSideOf(BLOB, EYE, LOOK_SOUTH);

        Vec3 oldSlot = KnifeFan.pointOf(EYE, LOOK_SOUTH, KnifeFan.slotOf(1, 2));
        Vec3 newSlot = KnifeFan.pointOf(EYE, LOOK_SOUTH, KnifeFan.slotOf(1, 4));
        assertEquals(0, oldSlot.distanceTo(HoveringKnives.knifeAt(fan, 1, EYE, LOOK_SOUTH, inner, 0)), WITHIN);
        assertEquals(0, newSlot.distanceTo(HoveringKnives.knifeAt(fan, 1, EYE, LOOK_SOUTH, inner,
                HoveringKnives.EASE_TICKS)), WITHIN);
        assertEquals(1f, HoveringKnives.knifeScaleAt(fan, 1, 0), 1e-6f, "a knife already formed stays whole");
    }

    @Test
    void aCountOfZeroDropsTheFan() {
        HoveringKnives.formed(CASTER, 7, 0);

        HoveringKnives.formed(CASTER, 0, 30);

        assertNull(HoveringKnives.fanOf(CASTER));
    }
}
