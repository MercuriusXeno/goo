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
        Vec3 slot = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.knifeSlot(3, 4));
        assertEquals(0, slot.distanceTo(HoveringKnives.knifeAt(fan, 3, CUBE, PITCH, YAW,
                HoveringKnives.EASE_TICKS)), WITHIN);
        assertEquals(1f, HoveringKnives.knifeScaleAt(fan, 3, HoveringKnives.EASE_TICKS), 1e-6f);
    }

    @Test
    void aKnifeFormedBeforeTheChangeStaysWholeAndEasesSidewaysAsThePileWidens() {
        HoveringKnives.formed(CASTER, 2, 0);
        HoveringKnives.formed(CASTER, 4, 12);
        HoveringKnives.Fan fan = HoveringKnives.fanOf(CASTER);

        Vec3 before = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.knifeSlot(1, 2));
        Vec3 after = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.knifeSlot(1, 4));
        assertEquals(0, before.distanceTo(HoveringKnives.knifeAt(fan, 1, CUBE, PITCH, YAW, 0)), WITHIN);
        assertEquals(0, after.distanceTo(HoveringKnives.knifeAt(fan, 1, CUBE, PITCH, YAW,
                HoveringKnives.EASE_TICKS)), WITHIN);
        assertEquals(1f, HoveringKnives.knifeScaleAt(fan, 1, 0), 1e-6f, "a knife already formed stays whole");
    }

    // knives-hover-above-the-blob-cube-over-the-hand: the server throws each knife from the slot it hovers at
    @Test
    void eachKnifeIsThrownFromTheSlotItHoversAt() {
        Vec3 eye = new Vec3(4.5, 66.62, -1.5);
        Vec3 cube = StackHover.cubeCenterInView(eye, PITCH, YAW, true);
        for (int held = 1; held <= 10; held++) {
            HoveringKnives.formed(CASTER, held, held);
            HoveringKnives.formed(CASTER, held, held + 1);
            Vec3 drawn = HoveringKnives.knifeAt(HoveringKnives.fanOf(CASTER), held - 1, cube, PITCH, YAW,
                    HoveringKnives.EASE_TICKS);
            Vec3 launched = com.mercuriusxeno.goo.ability.crystal.ThrownKnife.launchPointOf(eye, PITCH, YAW, true,
                    held, false);
            assertEquals(0, drawn.distanceTo(launched), WITHIN, "the knife leaving at " + held + " held");
        }
    }

    @Test
    void aCountOfZeroDropsTheFan() {
        HoveringKnives.formed(CASTER, 7, 0);

        HoveringKnives.formed(CASTER, 0, 30);

        assertNull(HoveringKnives.fanOf(CASTER));
    }

    // knives-and-javelins-sound-as-each-forms-and-is-thrown
    @Test
    void eachKnifeFormingForTheLocalPlayerClinksOnce() {
        com.mercuriusxeno.goo.ability.FormSounds knives =
                com.mercuriusxeno.goo.ability.AbilityJson.decode("crystal_knives").delivery().formSounds();
        java.util.List<com.mercuriusxeno.goo.ability.program.SoundCue> played = new java.util.ArrayList<>();
        HoveringKnives.counted(CASTER, 1, 0, CASTER, knives, played::add);
        HoveringKnives.counted(CASTER, 2, 3, CASTER, knives, played::add);
        HoveringKnives.counted(CASTER, 2, 4, CASTER, knives, played::add);
        HoveringKnives.counted(CASTER, 1, 5, CASTER, knives, played::add);
        HoveringKnives.counted(CASTER + 1, 1, 6, CASTER, knives, played::add);

        assertEquals(2, played.size(), "two knives formed for the local player should clink twice, " + played);
        played.forEach(cue -> assertEquals("minecraft:block.amethyst_cluster.place", cue.sound().toString()));
        played.forEach(cue -> assertEquals(1f, cue.volume(), 1e-6f, "the clink should play at full volume, heard clearly"));
    }

    // knives-hover-above-the-blob-cube-over-the-hand: the clear glass of a nearer knife lays over a farther one
    @Test
    void theKnivesDrawFarthestFromTheCameraFirst() {
        Vec3 camera = new Vec3(0, 64, 0);
        java.util.List<Vec3> middles = java.util.List.of(new Vec3(0, 64, 1), new Vec3(0, 64, 3), new Vec3(0, 64, 2));
        assertEquals(java.util.List.of(1, 2, 0), HoveringKnives.drawOrderOf(middles, camera));
    }}
