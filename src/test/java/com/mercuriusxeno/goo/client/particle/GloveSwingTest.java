package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A glove stream mote swings with the glove when its caster turns: its
 * offset from the glove and its velocity rotate by the change in the
 * caster's look, fully at launch and fading to none over the swing window,
 * and only its own caster's turn moves it
 * (decision glove-stream-motes-swing-with-the-glove-at-launch).
 */
class GloveSwingTest {

    private static final Vec3 GLOVE = new Vec3(10, 64, -3);
    private static final Vec3 OFFSET = new Vec3(0.3, -0.2, 1.1);
    private static final Vec3 MOTE = GLOVE.add(OFFSET);
    private static final Vec3 VELOCITY = new Vec3(0.05, 0.02, 0.6);
    private static final Vec3 LOOKING_SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 LOOKING_EAST = new Vec3(1, 0, 0);
    private static final double FORTY_FIVE = Math.toRadians(45);
    private static final Vec3 LOOKING_UP_45 = new Vec3(0, Math.sin(FORTY_FIVE), Math.cos(FORTY_FIVE));
    private static final float QUARTER_TURN = (float) Math.toRadians(90);
    private static final float EIGHTH_TURN = (float) Math.toRadians(45);
    private static final double CLOSE = 1e-6;

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, CLOSE, "x of " + actual);
        assertEquals(expected.y, actual.y, CLOSE, "y of " + actual);
        assertEquals(expected.z, actual.z, CLOSE, "z of " + actual);
    }

    @Nested
    class AtLaunch {

        @Test
        void aYawTurnSwingsTheMoteAboutTheVerticalThroughTheGlove() {
            GloveSwing.Swung swung = GloveSwing.swing(MOTE, VELOCITY, GLOVE, LOOKING_SOUTH, LOOKING_EAST, 0);

            assertVec(OFFSET.yRot(QUARTER_TURN), swung.position().subtract(GLOVE));
            assertVec(VELOCITY.yRot(QUARTER_TURN), swung.velocity());
        }

        @Test
        void aPitchTurnSwingsTheMoteAboutTheLooksSidewaysAxis() {
            GloveSwing.Swung swung = GloveSwing.swing(MOTE, VELOCITY, GLOVE, LOOKING_SOUTH, LOOKING_UP_45, 0);

            assertVec(OFFSET.xRot(EIGHTH_TURN), swung.position().subtract(GLOVE));
            assertVec(VELOCITY.xRot(EIGHTH_TURN), swung.velocity());
        }
    }

    @Nested
    class OverTheWindow {

        @Test
        void halfwayThroughTheWindowTheMoteSwingsByHalfTheTurn() {
            GloveSwing.Swung swung = GloveSwing.swing(MOTE, VELOCITY, GLOVE, LOOKING_SOUTH, LOOKING_EAST,
                    GloveSwing.SWING_TICKS / 2);

            assertVec(OFFSET.yRot(EIGHTH_TURN), swung.position().subtract(GLOVE));
            assertVec(VELOCITY.yRot(EIGHTH_TURN), swung.velocity());
        }

        @Test
        void atTheWindowsEndAndPastItTheMoteFliesFree() {
            for (double age : new double[] {GloveSwing.SWING_TICKS, GloveSwing.SWING_TICKS + 1}) {
                GloveSwing.Swung swung = GloveSwing.swing(MOTE, VELOCITY, GLOVE, LOOKING_SOUTH, LOOKING_EAST, age);

                assertEquals(MOTE, swung.position(), "position at age " + age);
                assertEquals(VELOCITY, swung.velocity(), "velocity at age " + age);
            }
        }

        @Test
        void aStillLookLeavesTheMoteAsItWas() {
            for (double age : new double[] {0, GloveSwing.SWING_TICKS / 2}) {
                GloveSwing.Swung swung = GloveSwing.swing(MOTE, VELOCITY, GLOVE, LOOKING_EAST, LOOKING_EAST, age);

                assertEquals(MOTE, swung.position(), "position at age " + age);
                assertEquals(VELOCITY, swung.velocity(), "velocity at age " + age);
            }
        }
    }

    @Nested
    class ItsOwnCaster {

        private static final int SPRAYER = 7;
        private static final int BYSTANDER = 8;
        private final Map<Integer, GloveSwing.CasterPose> poses = new HashMap<>();

        @Test
        void theMoteAnswersThePlayerWhoSprayedItAndIgnoresAnother() {
            poses.put(SPRAYER, new GloveSwing.CasterPose(GLOVE, LOOKING_SOUTH));
            poses.put(BYSTANDER, new GloveSwing.CasterPose(GLOVE.add(3, 0, 0), LOOKING_SOUTH));
            GloveSwing.Tracker tracker = new GloveSwing.Tracker(SPRAYER, poses::get);

            poses.put(BYSTANDER, new GloveSwing.CasterPose(GLOVE.add(3, 0, 0), LOOKING_EAST));
            GloveSwing.Swung untouched = tracker.step(MOTE, VELOCITY, 0);
            assertEquals(MOTE, untouched.position(), "the bystander's turn should leave the mote alone");
            assertEquals(VELOCITY, untouched.velocity(), "the bystander's turn should leave the mote alone");

            poses.put(SPRAYER, new GloveSwing.CasterPose(GLOVE, LOOKING_EAST));
            GloveSwing.Swung swung = tracker.step(MOTE, VELOCITY, 1);
            assertVec(OFFSET.yRot(EIGHTH_TURN), swung.position().subtract(GLOVE));
        }

        @Test
        void aMoteWhoseCasterIsGoneFliesFree() {
            GloveSwing.Tracker tracker = new GloveSwing.Tracker(SPRAYER, poses::get);

            assertNull(tracker.step(MOTE, VELOCITY, 0));
        }
    }
}
