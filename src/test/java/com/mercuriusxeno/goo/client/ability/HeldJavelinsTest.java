package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.StackHover;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dart's stacked javelins on the client: each forming javelin spawns at the
 * cube's top and rises to its slot, the javelins hover side by side at one
 * height pointing along the look, and a javelin formed before the change
 * stands at its slot (decision dart-javelins-hover-over-the-shrinking-blob-cube).
 */
class HeldJavelinsTest {

    private static final int CASTER = 9;
    /** The lifted blob cube's center. */
    private static final Vec3 CUBE = new Vec3(1.2, 70.4, -3.1);
    private static final float PITCH = -15f;
    private static final float YAW = 120f;
    private static final double WITHIN = 1e-9;
    /** Slack for the float rounding a view's directions carry. */
    private static final double FLOAT_WITHIN = 1e-6;

    @AfterEach
    void clearTheStacks() {
        HeldJavelins.clear();
    }

    // dart-javelins-hover-over-the-shrinking-blob-cube: the server throws each javelin from the slot it hovers at
    @Test
    void eachJavelinIsThrownFromTheSlotItHoversAt() {
        Vec3 eye = new Vec3(4.5, 66.62, -1.5);
        Vec3 cube = StackHover.cubeCenterInView(eye, PITCH, YAW, true);
        for (int held = 1; held <= StackHover.JAVELINS; held++) {
            HeldJavelins.formed(CASTER, held, held);
            HeldJavelins.formed(CASTER, held, held + 1);
            Vec3 drawn = HeldJavelins.javelinAt(HeldJavelins.stackOf(CASTER), held - 1, cube, PITCH, YAW,
                    HeldJavelins.EASE_TICKS);
            Vec3 launched = com.mercuriusxeno.goo.ability.crystal.ThrownKnife.launchPointOf(eye, PITCH, YAW, true,
                    held, true);
            assertEquals(0, drawn.distanceTo(launched), WITHIN, "the javelin leaving at " + held + " held");
        }
    }

    // dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
    @Test
    void eachJavelinFormingForTheLocalPlayerScrapesOnce() {
        com.mercuriusxeno.goo.ability.FormSounds dart =
                com.mercuriusxeno.goo.ability.AbilityJson.decode("metal_dart").delivery().formSounds();
        java.util.List<com.mercuriusxeno.goo.ability.program.SoundCue> played = new java.util.ArrayList<>();
        HeldJavelins.counted(CASTER, 1, 0, CASTER, dart, played::add, false);
        HeldJavelins.counted(CASTER, 1, 4, CASTER, dart, played::add, false);
        HeldJavelins.counted(CASTER, 0, 5, CASTER, dart, played::add, false);
        HeldJavelins.counted(CASTER + 1, 1, 6, CASTER, dart, played::add, false);

        assertEquals(1, played.size(), "one javelin formed for the local player should scrape once, " + played);
        assertEquals("minecraft:item.axe.scrape", played.getFirst().sound().toString());
        assertEquals(1.5f, played.getFirst().pitch(), 1e-6f);
    }

    // dart-javelins-hover-over-the-shrinking-blob-cube: three javelins held leave the cube at the floor, not gone
    @Test
    void threeJavelinsHeldLeaveTheCubeAtTheFloor() {
        ClientPalmBuffers.synced(CASTER, 20, 2, 20, 0);
        HeldJavelins.formed(CASTER, StackHover.JAVELINS, 0);
        assertEquals(0.464f, LiftedPalmBlob.stackShareOf(CASTER, 100), 0.001f);
        ClientPalmBuffers.clear();
    }

    // weapons-form-from-a-goo-cuboid-leaving-the-palm-blob: the cuboid leaves the blob and becomes the javelin
    @Test
    void aFormingJavelinStartsAsTheCuboidAtTheBlobAndEndsAsTheJavelinAtItsSlot() {
        HeldJavelins.formed(CASTER, 1, 0);
        HeldJavelins.Stack stack = HeldJavelins.stackOf(CASTER);
        assertEquals(0, CUBE.distanceTo(HeldJavelins.javelinAt(stack, 0, CUBE, PITCH, YAW, 0)), WITHIN,
                "the cuboid starts at the blob");
        assertEquals(0f, HeldJavelins.javelinDrawnScaleAt(stack, 0, 0), 1e-6f, "no javelin shape at the first frame");
        assertEquals(0.5f, HeldJavelins.javelinDrawnScaleAt(stack, 0, 5), 1e-6f, "half formed midway, 5 ticks on");
        Vec3 slot = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.javelinSlot(0, 1));
        assertEquals(0, slot.distanceTo(HeldJavelins.javelinAt(stack, 0, CUBE, PITCH, YAW, 10)), WITHIN,
                "settled at its slot 10 ticks on");
        assertEquals(1f, HeldJavelins.javelinDrawnScaleAt(stack, 0, 10), 1e-6f, "the javelin whole 10 ticks on");
    }

    // weapons-form-from-a-goo-cuboid-leaving-the-palm-blob: the blob shrinks by ease as the cuboid leaves
    @Test
    void theBlobShrinksByEaseAsTheCuboidLeaves() {
        ClientPalmBuffers.synced(CASTER, 140, 2, 140, 0);
        HeldJavelins.formed(CASTER, 1, 0);
        float first = LiftedPalmBlob.stackShareOf(CASTER, 0);
        float last = LiftedPalmBlob.stackShareOf(CASTER, 10);
        assertEquals(1f, first * first * first, 1e-4f, "the blob starts at its volume before");
        assertEquals(0.7f, last * last * last, 1e-4f, "and ends a javelin's share less");
        ClientPalmBuffers.clear();
    }

    // released-weapons-turn-back-into-blobs-that-rejoin-the-palm-blob: the blob grows by ease as each blob rejoins it
    @Test
    void theBlobGrowsByEaseAsEachReleasedJavelinRejoinsIt() {
        com.mercuriusxeno.goo.ability.FormSounds none = com.mercuriusxeno.goo.ability.FormSounds.NONE;
        HeldJavelins.counted(CASTER, 3, 0, CASTER, none, cue -> { }, false);
        HeldJavelins.counted(CASTER, 0, 10, CASTER, none, cue -> { }, true);
        ClientPalmBuffers.synced(CASTER, 200, 2, 200, 10);
        float before = -1f;
        for (double now = 10; now <= 19; now += 0.25) {
            float edge = LiftedPalmBlob.stackShareOf(CASTER, now);
            float volume = edge * edge * edge;
            assertTrue(volume >= before - 1e-6f, "the blob should never shrink as the javelins return, " + volume
                    + " after " + before + " at " + now);
            before = volume;
        }
        float atRelease = LiftedPalmBlob.stackShareOf(CASTER, 10);
        assertEquals(0.1f, atRelease * atRelease * atRelease, 1e-4f, "the blob starts the return at 10%");
        float afterTheFirstMerge = LiftedPalmBlob.stackShareOf(CASTER, 15);
        assertEquals(0.4f, afterTheFirstMerge * afterTheFirstMerge * afterTheFirstMerge, 1e-4f,
                "one share gained as the first merges");
        assertEquals(1f, before, 1e-4f, "and 100% once all three are back");
        ClientPalmBuffers.clear();
    }}
