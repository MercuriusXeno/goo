package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static com.mercuriusxeno.goo.client.ability.Transformations.HOP_HEIGHT;
import static com.mercuriusxeno.goo.client.ability.Transformations.HOP_SHARE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A model transformation hops a goo blob from its origin to an entity, then
 * shrinks the blob to nothing as the entity's model grows from nothing to
 * full size on a smooth curve, for any entity and goo type
 * (decision model-transformation-is-one-animation).
 */
class TransformationsTest {

    private static final long START = 1000;
    private static final int TICKS = 20;
    private static final int CLONE = 77;
    private static final Vec3 STRUCK = new Vec3(0, 64, 0);
    private static final Vec3 CLONE_AT = new Vec3(2, 64, 0);
    private static final float EPSILON = 1e-4f;

    private static Transformations.Transformation cloneOf() {
        return new Transformations.Transformation(GooTypes.VITAL, STRUCK, CLONE_AT, CLONE, START, TICKS);
    }

    private static float at(float share) {
        return START + share * TICKS;
    }

    @Nested
    class Curve {

        @Test
        void blobIsWholeAndTheModelNothingThroughTheHop() {
            for (float share : new float[] {0f, HOP_SHARE / 2f, HOP_SHARE}) {
                assertEquals(1f, cloneOf().blobScale(at(share)), EPSILON);
                assertEquals(0f, cloneOf().modelScale(at(share)), EPSILON);
            }
        }

        @Test
        void blobFallsToNothingAsTheModelRisesToFull() {
            float midMorph = HOP_SHARE + (1f - HOP_SHARE) / 2f;

            assertEquals(0.5f, cloneOf().modelScale(at(midMorph)), EPSILON);
            assertEquals(1f, cloneOf().modelScale(at(1f)), EPSILON);
            assertEquals(0f, cloneOf().blobScale(at(1f)), EPSILON);
        }

        @Test
        void blobAndModelAlwaysSumToOneSoTheModelIsNeverWholeBesideTheBlob() {
            for (int step = 0; step <= TICKS; step++) {
                Transformations.Transformation clone = cloneOf();
                float time = START + step;
                assertEquals(1f, clone.blobScale(time) + clone.modelScale(time), EPSILON);
                assertTrue(clone.modelScale(time) < 1f || clone.blobScale(time) == 0f, "whole beside the blob at " + step);
            }
        }

        @Test
        void morphEasesInAndOutRatherThanMovingLinearly() {
            float quarter = HOP_SHARE + (1f - HOP_SHARE) / 4f;

            assertTrue(cloneOf().modelScale(at(quarter)) < 0.25f, "the morph does not ease in");
        }
    }

    @Nested
    class Hop {

        @Test
        void blobLeavesTheStruckMobAndLandsOnTheClone() {
            assertEquals(STRUCK, cloneOf().blobPosition(at(0f)));
            assertEquals(CLONE_AT.x, cloneOf().blobPosition(at(HOP_SHARE)).x, EPSILON);
            assertEquals(CLONE_AT.y, cloneOf().blobPosition(at(1f)).y, EPSILON);
        }

        @Test
        void blobArcsUpMidHop() {
            Vec3 peak = cloneOf().blobPosition(at(HOP_SHARE / 2f));

            assertEquals(STRUCK.y + HOP_HEIGHT, peak.y, EPSILON);
            assertEquals(1.0, peak.x, EPSILON);
        }
    }

    @Nested
    class Life {

        @Test
        void anEntityDrawsAtTheTransformationsScaleWhileItPlays() {
            Transformations transformations = new Transformations();
            transformations.add(GooTypes.HEX, STRUCK, CLONE_AT, CLONE, START, TICKS);

            assertEquals(0f, transformations.modelScaleOf(CLONE, START), EPSILON);
            assertEquals(1f, transformations.modelScaleOf(CLONE + 1, START), EPSILON);
            assertEquals(1f, transformations.modelScaleOf(CLONE, START + TICKS), EPSILON);
        }

        @Test
        void aTransformationIsDroppedOnceTheModelIsWhole() {
            Transformations transformations = new Transformations();
            transformations.add(GooTypes.VITAL, STRUCK, CLONE_AT, CLONE, START, TICKS);

            assertEquals(1, transformations.live(START + TICKS - 1).size());
            assertTrue(transformations.live(START + TICKS).isEmpty());
        }

        @Test
        void clearDropsEveryTransformation() {
            Transformations transformations = new Transformations();
            transformations.add(GooTypes.VITAL, STRUCK, CLONE_AT, CLONE, START, TICKS);

            transformations.clear();

            assertTrue(transformations.live(START).isEmpty());
        }
    }
}
