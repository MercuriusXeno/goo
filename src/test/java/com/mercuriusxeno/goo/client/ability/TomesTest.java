package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Enchant's book through its choreography: shut as it starts, wide
 * open while it drinks in glyphs, shut again once it snaps, and faded out by
 * its end.
 */
class TomesTest {

    private static final float TOLERANCE = 1e-6f;

    /**
     * Where the tome floats as the player sneaks: the entity eye has stepped
     * to crouch height while the camera still eases toward it (decision
     * diagnose-and-correct-eye-drawn-visuals-jumping-on-sneak).
     */
    @Nested
    class OnSneak {

        private static final double EPSILON = 1e-9;
        private static final Vec3 LOOK = new Vec3(0, -0.6, 0.8);
        private static final CameraEye.Sight EASING_CAMERA = new CameraEye.Sight(new Vec3(0, 65.47, 0), LOOK);
        private static final CameraEye.Sight CROUCHED_EYE = new CameraEye.Sight(new Vec3(0, 65.27, 0), LOOK);

        @Test
        void theLocalPlayersTomeInFirstPersonFloatsFromTheCamera() {
            Vec3 anchor = Tomes.anchorFrom(CameraEye.chosen(true, EASING_CAMERA, CROUCHED_EYE));

            assertEquals(65.47 - 0.6 * Tomes.REACH - Tomes.DROP, anchor.y, EPSILON);
            assertEquals(0.8 * Tomes.REACH, anchor.z, EPSILON);
        }

        @Test
        void aTomeSeenFromOutsideTheLocalFirstPersonViewFloatsFromTheEntityEye() {
            Vec3 anchor = Tomes.anchorFrom(CameraEye.chosen(false, EASING_CAMERA, CROUCHED_EYE));

            assertEquals(65.27 - 0.6 * Tomes.REACH - Tomes.DROP, anchor.y, EPSILON);
        }
    }

    @Test
    void theBookStartsShut() {
        assertEquals(0f, Tomes.enchantOpenness(0f), TOLERANCE);
    }

    @Test
    void theBookStandsOpenWhileItDrinksGlyphs() {
        assertTrue(Tomes.enchantOpenness(Tomes.SNAP_AT / 2f) > 1f);
    }

    @Test
    void theBookIsShutOnceItSnaps() {
        assertEquals(0f, Tomes.enchantOpenness(Tomes.SNAP_AT), TOLERANCE);
    }

    @Test
    void theBookFadesOutByTheEnd() {
        assertEquals(1f, Tomes.showAlpha(Tomes.SNAP_AT, Tomes.SNAP_AT + 1, Tomes.ENCHANT_TICKS), TOLERANCE);
        assertEquals(0f, Tomes.showAlpha(Tomes.ENCHANT_TICKS, Tomes.SNAP_AT + 1, Tomes.ENCHANT_TICKS), TOLERANCE);
    }

    // goo-sound-at-the-listener-reaches-both-ears: the tome floats a hand ahead of its caster's eye
    @Nested
    class WhereATomesSoundPlays {

        private static final double EPSILON = 1e-9;
        private final Vec3 tome = new Vec3(13.4, 65.32, -7.9);
        private final SoundEvent pageTurn = SoundEvent.createVariableRangeEvent(
                Identifier.withDefaultNamespace("item.book.page_turn"));

        @Test
        void theLocalCastersTomePlaysAtTheListenerUnattenuated() {
            SimpleSoundInstance sound = Tomes.tomeSoundOf(pageTurn, tome, 1f, 1f, true);
            assertTrue(sound.isRelative());
            assertEquals(SoundInstance.Attenuation.NONE, sound.getAttenuation());
        }

        @Test
        void anotherPlayersTomePlaysAtTheTome() {
            SimpleSoundInstance sound = Tomes.tomeSoundOf(pageTurn, tome, 1f, 1f, false);
            assertFalse(sound.isRelative());
            assertEquals(tome.x, sound.getX(), EPSILON);
            assertEquals(tome.y, sound.getY(), EPSILON);
            assertEquals(tome.z, sound.getZ(), EPSILON);
        }
    }
}
