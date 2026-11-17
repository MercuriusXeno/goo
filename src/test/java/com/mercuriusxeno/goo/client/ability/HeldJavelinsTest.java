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

    @Test
    void aFormingJavelinStartsAtTheCubesTopAndRisesToItsSlot() {
        HeldJavelins.formed(CASTER, 1, 0);
        HeldJavelins.formed(CASTER, 2, 10);
        HeldJavelins.Stack stack = HeldJavelins.stackOf(CASTER);

        Vec3 top = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.spawnSlot(1, StackHover.JAVELINS));
        assertEquals(0, top.distanceTo(HeldJavelins.javelinAt(stack, 1, CUBE, PITCH, YAW, 0)), WITHIN);
        Vec3 slot = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.javelinSlot(1, 2));
        assertEquals(0, slot.distanceTo(HeldJavelins.javelinAt(stack, 1, CUBE, PITCH, YAW,
                HeldJavelins.EASE_TICKS)), WITHIN);
        Vec3 first = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.javelinSlot(0, 1));
        assertEquals(0, first.distanceTo(HeldJavelins.javelinAt(stack, 0, CUBE, PITCH, YAW, 0)), WITHIN,
                "a javelin formed before the change starts where it stood alone");
        Vec3 shifted = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.javelinSlot(0, 2));
        assertEquals(0, shifted.distanceTo(HeldJavelins.javelinAt(stack, 0, CUBE, PITCH, YAW,
                HeldJavelins.EASE_TICKS)), WITHIN, "and eases sideways as the pile widens");
    }

    // dart-javelins-hover-over-the-shrinking-blob-cube: the server throws each javelin from the slot it hovers at
    @Test
    void eachJavelinIsThrownFromTheSlotItHoversAt() {
        Vec3 eye = new Vec3(4.5, 66.62, -1.5);
        Vec3 cube = StackHover.cubeCenterInView(eye, PITCH, YAW, true, true);
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
        HeldJavelins.counted(CASTER, 1, 0, CASTER, dart, played::add);
        HeldJavelins.counted(CASTER, 1, 4, CASTER, dart, played::add);
        HeldJavelins.counted(CASTER, 0, 5, CASTER, dart, played::add);
        HeldJavelins.counted(CASTER + 1, 1, 6, CASTER, dart, played::add);

        assertEquals(1, played.size(), "one javelin formed for the local player should scrape once, " + played);
        assertEquals("minecraft:item.axe.scrape", played.getFirst().sound().toString());
        assertEquals(1.5f, played.getFirst().pitch(), 1e-6f);
    }
}
