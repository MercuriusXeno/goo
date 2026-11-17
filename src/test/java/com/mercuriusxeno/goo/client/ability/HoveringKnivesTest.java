package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.StackHover;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Knives' hovering pyramid on the client: a new knife spawns at the cube's
 * top and rises to its slot, growing as it goes, a knife formed before the
 * change stands whole at its slot, and a count of 0 drops the pyramid
 * (decisions knives-hover-above-the-blob-cube-over-the-hand,
 * knives-hover-in-an-upside-down-bowling-pin-pyramid, knives-form-on-right-click-and-throw-on-left-click).
 */
class HoveringKnivesTest {

    private static final int CASTER = 7;
    /** The lifted blob cube's center. */
    private static final Vec3 CUBE = new Vec3(-0.3, 64.8, 0.6);
    private static final float PITCH = 20f;
    private static final float YAW = 30f;
    private static final double WITHIN = 1e-9;

    @AfterEach
    void clearTheFans() {
        HoveringKnives.clear();
    }

    @Test
    void aNewKnifeStartsAtTheCubesTopAndRisesToItsSlot() {
        HoveringKnives.formed(CASTER, 3, 0);
        HoveringKnives.formed(CASTER, 4, 9);
        HoveringKnives.Fan fan = HoveringKnives.fanOf(CASTER);

        Vec3 top = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.spawnSlot(3, 10));
        assertEquals(0, top.distanceTo(HoveringKnives.knifeAt(fan, 3, CUBE, PITCH, YAW, 0)), WITHIN);
        assertEquals(0f, HoveringKnives.knifeScaleAt(fan, 3, 0), 1e-6f, "the knife grows out of the cube");
        Vec3 slot = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.knifeSlot(3));
        assertEquals(0, slot.distanceTo(HoveringKnives.knifeAt(fan, 3, CUBE, PITCH, YAW,
                HoveringKnives.EASE_TICKS)), WITHIN);
        assertEquals(1f, HoveringKnives.knifeScaleAt(fan, 3, HoveringKnives.EASE_TICKS), 1e-6f);
    }

    @Test
    void aKnifeFormedBeforeTheChangeStandsWholeAtItsSlot() {
        HoveringKnives.formed(CASTER, 2, 0);
        HoveringKnives.formed(CASTER, 4, 12);
        HoveringKnives.Fan fan = HoveringKnives.fanOf(CASTER);

        Vec3 slot = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.knifeSlot(1));
        assertEquals(0, slot.distanceTo(HoveringKnives.knifeAt(fan, 1, CUBE, PITCH, YAW, 0)), WITHIN);
        assertEquals(1f, HoveringKnives.knifeScaleAt(fan, 1, 0), 1e-6f, "a knife already formed stays whole");
    }

    @Test
    void aCountOfZeroDropsTheFan() {
        HoveringKnives.formed(CASTER, 7, 0);

        HoveringKnives.formed(CASTER, 0, 30);

        assertNull(HoveringKnives.fanOf(CASTER));
    }
}
