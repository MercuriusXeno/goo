package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a third-person caster's glove palm draws: the palm point carried
 * through the glove arm's drawn transform, its up turned with the body, upright
 * and under the upside-down flip the player renderer lays on an inverted
 * player, a body facing south at the transforms' origin.
 * decision third-person-blob-stands-above-the-drawn-palm
 */
class DrawnPalmTest {

    private static final double DELTA = 1e-5;
    /** The player's bounding box height, which the upside-down flip lifts the model by. */
    private static final float PLAYER_HEIGHT = 1.8f;
    /** LivingEntityRenderer's lift of the model, a pixel past the feet. */
    private static final float MODEL_DROP = -1.501f;
    /** HumanoidModel's right arm pivot, in pixels. */
    private static final Vector3fc RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final float HANGING = 0f;
    private static final float HELD_OUT = (float) Math.toRadians(-90);
    /** The glove's model on the arm, as ItemInHandLayer and a display transform lay it: turned, offset and scaled. */
    private static final Matrix4fc GLOVE_ON_ARM = new Matrix4f().rotateX((float) Math.toRadians(-90))
            .rotateY((float) Math.PI).translate(1f / 16f, 2f / 16f, -10f / 16f).rotateX((float) Math.toRadians(90))
            .scale(0.675f).translate(-0.5f, -0.5f, -0.5f);
    private static final Vector3fc PALM = GloveSpecialRenderer.restingPalmCenter();

    /**
     * The body model's drawn transform, as LivingEntityRenderer lays it facing south.
     *
     * @param upsideDown whether the player renderer flips the model upside down
     * @return the body model onto the world's axes
     */
    static Matrix4f body(boolean upsideDown) {
        Matrix4f body = new Matrix4f().rotateY((float) Math.PI);
        if (upsideDown) {
            body.translate(0f, PLAYER_HEIGHT + 0.1f, 0f).rotateZ((float) Math.PI);
        }
        return body.scale(-1f, -1f, 1f).translate(0f, MODEL_DROP, 0f);
    }

    /**
     * The right arm part's drawn transform, as ModelPart.translateAndRotate lays it.
     *
     * @param body the body's transform
     * @param xRot the arm's pitch, 0 hanging and a quarter turn back held out ahead
     * @return the arm onto the world's axes
     */
    static Matrix4f arm(Matrix4fc body, float xRot) {
        return new Matrix4f(body).translate(new Vector3f(RIGHT_SHOULDER).div(16f)).rotateX(xRot);
    }

    private static DrawnPalm.Palm palmOf(boolean upsideDown, float xRot) {
        Matrix4f body = body(upsideDown);
        return DrawnPalm.of(arm(body, xRot), body, GLOVE_ON_ARM, PALM);
    }

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) <= DELTA, "expected " + expected + ", was " + actual);
    }

    /** The palm follows the arm as it draws. */
    @Nested
    class FollowsTheArm {

        @Test
        void eachArmTransformCarriesThePalmPointThroughIt() {
            for (float xRot : new float[] {HANGING, HELD_OUT}) {
                Vector3f carried = new Matrix4f(arm(body(false), xRot)).mul(GLOVE_ON_ARM)
                        .transformPosition(PALM, new Vector3f());

                assertNear(new Vec3(carried), palmOf(false, xRot).place());
            }
        }

        @Test
        void aHeldOutArmCarriesThePalmAheadOfTheBodyAndUpToTheShoulder() {
            Vec3 hanging = palmOf(false, HANGING).place();
            Vec3 heldOut = palmOf(false, HELD_OUT).place();

            assertTrue(heldOut.z - hanging.z > 0.3, "the held out palm " + heldOut + " should stand ahead, south, "
                    + "of the hanging one " + hanging);
            assertTrue(heldOut.y - hanging.y > 0.3, "the held out palm " + heldOut + " should stand higher than "
                    + "the hanging one " + hanging);
        }

        @Test
        void anUprightModelsUpIsTheWorldsUp() {
            assertNear(new Vec3(0, 1, 0), palmOf(false, HANGING).modelUp());
        }
    }

    /** Under Invert the palm is the upright one carried through the flip. */
    @Nested
    class TurnsUpsideDownWithTheModel {

        /** The flip in the world: the inverted body's transform after the upright one's undoing. */
        private static Matrix4f flip() {
            return body(true).mul(body(false).invert());
        }

        private static Vec3 flipped(Vec3 point) {
            return new Vec3(flip().transformPosition(point.toVector3f()));
        }

        @Test
        void theInvertedPalmIsTheUprightOneCarriedThroughTheFlip() {
            for (float xRot : new float[] {HANGING, HELD_OUT}) {
                DrawnPalm.Palm upright = palmOf(false, xRot);
                DrawnPalm.Palm inverted = palmOf(true, xRot);

                assertNear(flipped(upright.place()), inverted.place());
                assertNear(new Vec3(flip().transformDirection(upright.palmUp().toVector3f())), inverted.palmUp());
                assertEquals(-upright.place().x, inverted.place().x, DELTA, "the glove should swap sides across");
                assertNear(upright.modelUp().scale(-1), inverted.modelUp());
            }
        }

        @Test
        void theInvertedRestIsTheUprightOneCarriedThroughTheFlipRisenTowardTheHead() {
            for (BlobRest.Rest rest : BlobRest.Rest.values()) {
                DrawnPalm.Palm upright = palmOf(false, HELD_OUT);
                DrawnPalm.Palm inverted = palmOf(true, HELD_OUT);
                Vec3 invertedRest = BlobRest.aboveTheDrawnPalm(inverted, rest);

                assertNear(flipped(BlobRest.aboveTheDrawnPalm(upright, rest)), invertedRest);
                assertTrue(invertedRest.y < inverted.place().y, "the inverted rest " + invertedRest
                        + " should stand below the palm " + inverted.place() + ", toward the hanging model's head");
            }
        }
    }
}
