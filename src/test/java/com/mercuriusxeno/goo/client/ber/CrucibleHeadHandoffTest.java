package com.mercuriusxeno.goo.client.ber;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the handoff that draws a melt head's first frame in the pose its item entity
 * was drawn at, easing it flat (decision consume-at-rest-in-place).
 */
class CrucibleHeadHandoffTest {

    /** Mth's sine table is good to a few parts in ten thousand. */
    private static final float EPSILON = 1e-3f;
    private static final Identifier COBBLE = Identifier.fromNamespaceAndPath("minecraft", "cobblestone");
    private static final Identifier DIRT = Identifier.fromNamespaceAndPath("minecraft", "dirt");
    /** A ground model's bounds: a flat sprite, a quarter block across, offset off its origin. */
    private static final AABB GROUND_BOX = new AABB(-0.1, 0.0, -0.02, 0.15, 0.25, 0.02);
    private static final CrucibleHeadHandoff.ItemPose REST = CrucibleHeadHandoff.restingPose(
        new CrucibleItemLayout.ItemPlacement(0.5f, 0.6f, 0.5f, CrucibleItemLayout.HEAD_SIZE), GROUND_BOX, 1e-3);
    private static final double SEEN_AT = 1000.0;
    private static final float AGE = 37.4f;
    private static final float BOB_OFFSET = 2.1f;
    private static final double ENTITY_X = 0.51;
    private static final double ENTITY_Y = 0.6;
    private static final double ENTITY_Z = 0.49;

    /** The handoff reads the entity's pose and eases it into the resting one. */
    @Nested
    class Handoff {

        @Test
        void headTakesTheEntityPoseAtTheSwap() {
            CrucibleHeadHandoff handoff = seenCobble();
            double swap = SEEN_AT + 1.0;
            CrucibleHeadHandoff.ItemPose drawn = handoff.headPose(COBBLE, GROUND_BOX, REST, swap);
            assertPose(CrucibleHeadHandoff.entityPose((float) ENTITY_X, (float) ENTITY_Y, (float) ENTITY_Z,
                AGE + 1f, BOB_OFFSET, GROUND_BOX), drawn);
        }

        @Test
        void headLiesAtRestOnceTheEaseRuns() {
            CrucibleHeadHandoff handoff = seenCobble();
            handoff.headPose(COBBLE, GROUND_BOX, REST, SEEN_AT);
            assertPose(REST, handoff.headPose(COBBLE, GROUND_BOX, REST, SEEN_AT + CrucibleHeadHandoff.EASE_TICKS));
        }

        @Test
        void headIsBetweenThePosesMidEase() {
            CrucibleHeadHandoff handoff = seenCobble();
            handoff.headPose(COBBLE, GROUND_BOX, REST, SEEN_AT);
            CrucibleHeadHandoff.ItemPose mid = handoff.headPose(COBBLE, GROUND_BOX, REST,
                SEEN_AT + CrucibleHeadHandoff.EASE_TICKS / 2);
            assertEquals(CrucibleHeadHandoff.FLAT_TILT_DEGREES / 2, mid.tilt(), EPSILON);
        }
    }

    /** A head with nothing to take a pose from lies at rest from its first frame. */
    @Nested
    class NoHandoff {

        @Test
        void headWithNoEntitySeenRestsAtOnce() {
            assertPose(REST, new CrucibleHeadHandoff().headPose(COBBLE, GROUND_BOX, REST, SEEN_AT));
        }

        @Test
        void headOfAnotherItemRestsAtOnce() {
            assertPose(REST, seenCobble().headPose(DIRT, GROUND_BOX, REST, SEEN_AT));
        }

        @Test
        void headLongAfterTheEntityWasSeenRestsAtOnce() {
            assertPose(REST, seenCobble().headPose(COBBLE, GROUND_BOX, REST,
                SEEN_AT + CrucibleHeadHandoff.HANDOFF_WINDOW_TICKS + 1.0));
        }

        @Test
        void headCarriedOverFromLastFrameKeepsResting() {
            CrucibleHeadHandoff handoff = new CrucibleHeadHandoff();
            handoff.headPose(COBBLE, GROUND_BOX, REST, SEEN_AT);
            handoff.seeEntity(COBBLE, ENTITY_X, ENTITY_Y, ENTITY_Z, AGE, BOB_OFFSET, SEEN_AT + 1.0);
            assertPose(REST, handoff.headPose(COBBLE, GROUND_BOX, REST, SEEN_AT + 1.0));
        }
    }

    /** The captured pose is the one ItemEntityRenderer draws. */
    @Nested
    class EntityPose {

        @Test
        void modelBottomHoversABobAboveTheFeet() {
            CrucibleHeadHandoff.ItemPose pose = CrucibleHeadHandoff.entityPose(0.5f, 0.6f, 0.5f, AGE, BOB_OFFSET,
                GROUND_BOX);
            float bob = Mth.sin(AGE / 10f + BOB_OFFSET) * 0.1f + 0.1f;
            assertEquals(0.6f + bob + 0.0625f + 0.125f, pose.y(), EPSILON);
        }

        @Test
        void entityStandsUprightUnscaledAndSpinning() {
            CrucibleHeadHandoff.ItemPose pose = CrucibleHeadHandoff.entityPose(0.5f, 0.6f, 0.5f, AGE, BOB_OFFSET,
                GROUND_BOX);
            assertEquals(0f, pose.tilt(), EPSILON);
            assertEquals(1f, pose.scale(), EPSILON);
            assertEquals(AGE / 20f + BOB_OFFSET, pose.spin(), EPSILON);
        }

        @Test
        void offCenterModelSwingsWithTheSpin() {
            CrucibleHeadHandoff.ItemPose pose = CrucibleHeadHandoff.entityPose(0.5f, 0.6f, 0.5f, 0f, (float) Math.PI,
                GROUND_BOX);
            assertEquals(0.5f - (float) GROUND_BOX.getCenter().x, pose.x(), EPSILON);
        }
    }

    /**
     * @return a handoff that saw a cobblestone entity resting in the basin
     */
    private static CrucibleHeadHandoff seenCobble() {
        CrucibleHeadHandoff handoff = new CrucibleHeadHandoff();
        handoff.seeEntity(COBBLE, ENTITY_X, ENTITY_Y, ENTITY_Z, AGE, BOB_OFFSET, SEEN_AT);
        return handoff;
    }

    private static void assertPose(CrucibleHeadHandoff.ItemPose expected, CrucibleHeadHandoff.ItemPose actual) {
        assertEquals(expected.x(), actual.x(), EPSILON, "x");
        assertEquals(expected.y(), actual.y(), EPSILON, "y");
        assertEquals(expected.z(), actual.z(), EPSILON, "z");
        assertEquals(expected.spin(), actual.spin(), EPSILON, "spin");
        assertEquals(expected.tilt(), actual.tilt(), EPSILON, "tilt");
        assertEquals(expected.scale(), actual.scale(), EPSILON, "scale");
    }
}
