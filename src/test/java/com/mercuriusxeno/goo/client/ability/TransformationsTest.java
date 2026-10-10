package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
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

    /**
     * A blob turning into a block makes no hop: the flying blob draws not at all and
     * the block's renderer morphs it out of the struck face from the tick it lands.
     * decision prism-is-one-pointed-quartz-column
     */
    @Nested
    class IntoABlock {

        private static final BlockPos PRISM_AT = new BlockPos(3, 64, 0);

        private Transformations.Transformation prismOf() {
            return new Transformations.Transformation(GooTypes.CRYSTAL, STRUCK, Vec3.atCenterOf(PRISM_AT), -1,
                    PRISM_AT, START, TICKS);
        }

        @Test
        void theMorphRunsTheWholeTransformationWithNoHop() {
            for (float share : new float[] {0f, HOP_SHARE / 2f, 1f}) {
                assertEquals(0f, prismOf().blobScale(at(share)), EPSILON);
            }
            assertTrue(prismOf().modelScale(at(HOP_SHARE / 2f)) > 0f, "the morph waits on a hop");
            assertEquals(0.5f, prismOf().modelScale(at(0.5f)), EPSILON);
            assertEquals(1f, prismOf().modelScale(at(1f)), EPSILON);
        }

        @Test
        void theBlocksRendererFindsTheTransformationIntoItsCell() {
            Transformations transformations = new Transformations();
            transformations.add(GooTypes.CRYSTAL, STRUCK, Vec3.atCenterOf(PRISM_AT), -1, PRISM_AT, START, TICKS);
            Transformations.Transformation found = transformations.intoBlockAt(PRISM_AT, at(0.5f));
            assertEquals(PRISM_AT, found == null ? null : found.targetBlock());
            assertEquals(null, transformations.intoBlockAt(PRISM_AT.above(), at(0.5f)));
            assertEquals(null, transformations.intoBlockAt(PRISM_AT, START + TICKS));
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

            assertEquals(0f, transformations.modelScaleOf(CLONE, false, START), EPSILON);
            assertEquals(1f, transformations.modelScaleOf(CLONE + 1, false, START), EPSILON);
            assertEquals(1f, transformations.modelScaleOf(CLONE, false, START + TICKS), EPSILON);
        }

        // decision prism-blob-becomes-a-milky-quartz-crystal
        @Test
        void aBlockDrawsAtTheTransformationsScaleWhileItPlaysAndNoEntityDoes() {
            Transformations transformations = new Transformations();
            BlockPos prism = new BlockPos(3, 64, -2);
            transformations.add(GooTypes.CRYSTAL, STRUCK, CLONE_AT, -1, prism, START, TICKS);

            assertEquals(0f, transformations.modelScaleAt(prism, START), EPSILON);
            assertEquals(0.5f, transformations.modelScaleAt(prism, at(0.5f)), EPSILON);
            assertEquals(1f, transformations.modelScaleAt(prism.above(), START), EPSILON);
            assertEquals(1f, transformations.modelScaleAt(prism, START + TICKS), EPSILON);
            assertEquals(1f, transformations.modelScaleOf(-1, false, START), EPSILON);
        }

        @Test
        void anEntityTransformationLeavesEveryBlockFull() {
            Transformations transformations = new Transformations();
            transformations.add(GooTypes.VITAL, STRUCK, CLONE_AT, CLONE, START, TICKS);

            assertEquals(1f, transformations.modelScaleAt(BlockPos.ZERO, START), EPSILON);
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

    /** Rewind's shrinks ease a model between two sizes (decision rewind-shrinks-adult-to-baby-to-egg). */
    @Nested
    class Shrinks {

        private static final float ADULT_OVER_BABY = 2f;

        @Test
        void aBabyShrinksFromTheAdultsSizeToItsOwn() {
            Transformations transformations = new Transformations();
            transformations.shrink(CLONE, ADULT_OVER_BABY, 1f, true, START, TICKS);

            assertEquals(ADULT_OVER_BABY, transformations.modelScaleOf(CLONE, true, START), EPSILON);
            assertEquals(1.5f, transformations.modelScaleOf(CLONE, true, at(0.5f)), EPSILON);
            assertEquals(1f, transformations.modelScaleOf(CLONE, true, START + TICKS), EPSILON);
        }

        @Test
        void aBabyShrinkHoldsOffUntilTheEntityReadsAsABaby() {
            Transformations transformations = new Transformations();
            transformations.shrink(CLONE, ADULT_OVER_BABY, 1f, true, START, TICKS);

            assertEquals(1f, transformations.modelScaleOf(CLONE, false, START), EPSILON);
        }

        @Test
        void aMobShrunkIntoItsEggStaysHiddenUntilItsRemovalArrives() {
            Transformations transformations = new Transformations();
            transformations.shrink(CLONE, 1f, 0f, false, START, TICKS);

            assertEquals(0.5f, transformations.modelScaleOf(CLONE, false, at(0.5f)), EPSILON);
            assertEquals(0f, transformations.modelScaleOf(CLONE, false,
                    START + TICKS + Transformations.SHRINK_HOLD_TICKS - 1), EPSILON);
            assertEquals(1f, transformations.modelScaleOf(CLONE + 1, false, at(0.5f)), EPSILON);
        }
    }

    /**
     * A conjured spawn's splat stands where its mob forms: it makes no hop,
     * staying where it splatted, and starts morphing from its first tick
     * (decision spawn-goo-morphs-into-the-mob-it-births).
     */
    @Nested
    class InPlace {

        private Transformations.Transformation splat() {
            return new Transformations.Transformation(GooTypes.HEX, STRUCK, STRUCK, CLONE, START, TICKS);
        }

        @Test
        void theSplatNeverLeavesWhereItLanded() {
            assertEquals(STRUCK, splat().blobPosition(START + TICKS * HOP_SHARE / 2));
        }

        @Test
        void theMobGrowsFromTheFirstTick() {
            assertTrue(splat().modelScale(START + TICKS * HOP_SHARE / 2) > 0f);
        }
    }
}
