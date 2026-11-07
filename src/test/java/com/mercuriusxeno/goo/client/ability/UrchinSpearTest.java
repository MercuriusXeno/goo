package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Urchin's shot arms over three ticks, fires over three into the mob, and
 * retracts over six, drawn as the urchin's own spike cone
 * (decision urchin-rests-bristled-and-spears; operator ruling 2026-10-11:
 * arm 3, fire 3, retract 6, damage on contact).
 */
class UrchinSpearTest {

    private static final int STRIKE_TICK = 6;
    private static final int LENGTH = 12;
    private static final float REACH = 3.5f;
    private static final float EPSILON = 1e-5f;

    @Nested
    class TipDistance {

        @Test
        void armedHeadStandsJustPastTheBristlesOnTheThirdTick() {
            assertEquals(UrchinSpear.ARM_REACH,
                    UrchinSpear.tipDistance(UrchinSpear.ARM_TICKS, 0f, STRIKE_TICK, LENGTH, REACH), EPSILON);
        }

        @Test
        void armingRisesFromNothing() {
            float early = UrchinSpear.tipDistance(1, 0f, STRIKE_TICK, LENGTH, REACH);
            float later = UrchinSpear.tipDistance(2, 0f, STRIKE_TICK, LENGTH, REACH);
            assertEquals(0f, UrchinSpear.tipDistance(0, 0f, STRIKE_TICK, LENGTH, REACH), EPSILON);
            assertTrue(early > 0f && later > early && later < UrchinSpear.ARM_REACH, early + " then " + later);
        }

        @Test
        void tipStandsInTheMobOnTheTickTheDamageLands() {
            assertEquals(REACH, UrchinSpear.tipDistance(STRIKE_TICK, 0f, STRIKE_TICK, LENGTH, REACH), EPSILON);
        }

        @Test
        void firePunchesFasterAsItGoes() {
            float first = UrchinSpear.tipDistance(4, 0f, STRIKE_TICK, LENGTH, REACH)
                    - UrchinSpear.tipDistance(3, 0f, STRIKE_TICK, LENGTH, REACH);
            float last = UrchinSpear.tipDistance(STRIKE_TICK, 0f, STRIKE_TICK, LENGTH, REACH)
                    - UrchinSpear.tipDistance(5, 0f, STRIKE_TICK, LENGTH, REACH);
            assertTrue(last > first, "fire's last tick " + last + " should outrun its first " + first);
        }

        @Test
        void retractTakesSixTicksAndPunchOutrunsIt() {
            float halfRetract = UrchinSpear.tipDistance(STRIKE_TICK + 3, 0f, STRIKE_TICK, LENGTH, REACH);
            assertEquals(REACH / 2, halfRetract, EPSILON);
            assertTrue(UrchinSpear.tipDistance(LENGTH - 1, 0.99f, STRIKE_TICK, LENGTH, REACH) < 0.01f);
            assertEquals(0f, UrchinSpear.tipDistance(LENGTH, 0f, STRIKE_TICK, LENGTH, REACH), EPSILON);
        }

        @Test
        void aMobInsideTheBristlesIsArmedAtNoFurtherThanItStands() {
            float close = 0.8f;
            assertEquals(close, UrchinSpear.tipDistance(UrchinSpear.ARM_TICKS, 0f, STRIKE_TICK, LENGTH, close),
                    EPSILON);
            assertEquals(close, UrchinSpear.tipDistance(STRIKE_TICK, 0f, STRIKE_TICK, LENGTH, close), EPSILON);
        }
    }

    /** The spines bristle with each shot and the urchin rests smooth between shots (operator ruling 2026-10-11). */
    @Nested
    class BristleShare {

        @Test
        void spinesRestRetractedBeforeAndAfterAShot() {
            assertEquals(0f, UrchinSpear.bristleShare(0, 0f, STRIKE_TICK, LENGTH), EPSILON);
            assertEquals(0f, UrchinSpear.bristleShare(LENGTH, 0f, STRIKE_TICK, LENGTH), EPSILON);
        }

        @Test
        void spinesStandFullyOutFromTheArmToTheStab() {
            for (int age = UrchinSpear.ARM_TICKS; age <= STRIKE_TICK; age++) {
                assertEquals(1f, UrchinSpear.bristleShare(age, 0f, STRIKE_TICK, LENGTH), EPSILON, "age " + age);
            }
        }

        @Test
        void spinesRetractAlongsideTheSpear() {
            float share = UrchinSpear.bristleShare(STRIKE_TICK + 3, 0f, STRIKE_TICK, LENGTH);
            assertEquals(0.5f, share, EPSILON);
            assertEquals(REACH * share, UrchinSpear.tipDistance(STRIKE_TICK + 3, 0f, STRIKE_TICK, LENGTH, REACH),
                    EPSILON);
        }
    }

    /**
     * Every spine Urchin draws, the shot's and the flare's, is the urchin's
     * one spike: a four-sided cone whose every vertex stands on its base
     * ring or at its tip (decision urchin-spines-return-to-the-original-spikes).
     */
    @Nested
    class SpineMesh {

        private static final int CONE_VERTICES = 16;
        private static final float SHOT_TIP = 2.5f;
        private static final float MESH_EPSILON = 1e-4f;

        @Test
        void aShotDrawsOneSpikeConeFromTheUrchinToItsTip() {
            Vector3f base = MetalExplosionVisual.basePoint(Direction.UP);
            Vector3f aim = new Vector3f(base).add(1f, 3f, 0.5f);
            RecordingVertexConsumer consumer = new RecordingVertexConsumer();
            UrchinSpear.emit(new PoseStack().last(), consumer, base, aim, SHOT_TIP, 0);
            List<RecordingVertexConsumer.Vertex> vertices = consumer.vertices();
            assertEquals(CONE_VERTICES, vertices.size(), "a shot draws " + vertices.size() + " vertices");
            assertIsSpikeCone(vertices, base, new Vector3f(aim).sub(base).normalize(), SHOT_TIP);
        }

        @Test
        void theBristleDrawsEverySpineAsTheSameSpikeCone() {
            float share = 0.5f;
            assertUrchinIsSpikeCones(MetalExplosionVisual.bristled(Direction.UP, 0f, share),
                    MetalExplosionVisual.SPIKE_REACH * share);
        }

        /** The landing flare at its hold and the held ghost draw the one urchin the bristle does. */
        @Test
        void theHeldGhostDrawsEverySpineAsTheSameSpikeCone() {
            assertUrchinIsSpikeCones(MetalExplosionVisual.heldUrchin(Direction.UP, 1f, 0),
                    MetalExplosionVisual.SPIKE_REACH);
        }

        private void assertUrchinIsSpikeCones(MetalExplosionVisual.Urchin urchin, float length) {
            Vector3f base = MetalExplosionVisual.basePoint(urchin.face());
            RecordingVertexConsumer consumer = new RecordingVertexConsumer();
            urchin.emit(new PoseStack().last(), consumer);
            List<RecordingVertexConsumer.Vertex> vertices = consumer.vertices();
            List<Vector3f> directions = MetalExplosionVisual.spikeDirections(urchin.face());
            assertEquals(CONE_VERTICES * directions.size(), vertices.size());
            for (int spine = 0; spine < directions.size(); spine++) {
                assertIsSpikeCone(vertices.subList(spine * CONE_VERTICES, (spine + 1) * CONE_VERTICES), base,
                        directions.get(spine), length);
            }
        }

        /**
         * Asserts the vertices are one cone along dir: each at the tip, or
         * on a base ring of one radius around the base, square to the axis.
         */
        private void assertIsSpikeCone(List<RecordingVertexConsumer.Vertex> vertices, Vector3f base, Vector3f dir,
                                       float length) {
            Vector3f tip = new Vector3f(dir).mul(length).add(base);
            float ringRadius = -1f;
            for (RecordingVertexConsumer.Vertex vertex : vertices) {
                Vector3f at = new Vector3f(vertex.x(), vertex.y(), vertex.z());
                if (at.distance(tip) < MESH_EPSILON) {
                    continue;
                }
                Vector3f offset = new Vector3f(at).sub(base);
                assertEquals(0f, offset.dot(dir), MESH_EPSILON, "a vertex stands off the base ring at " + at);
                if (ringRadius < 0f) {
                    ringRadius = offset.length();
                }
                assertEquals(ringRadius, offset.length(), MESH_EPSILON, "the base ring is not round at " + at);
            }
            assertTrue(ringRadius > 0f, "the cone has no base ring");
        }
    }
}
