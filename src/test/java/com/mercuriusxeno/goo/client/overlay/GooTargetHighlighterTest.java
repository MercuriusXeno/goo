package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The aim line ends where the bullseye draws on an aimed mob or block, and on
 * the cursor only for free aim (decision aim-line-roots-to-the-aimed-mob-or-block).
 */
class GooTargetHighlighterTest {

    private static final BlockPos POS = new BlockPos(2, 64, 2);
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** The gate the aim passed. */
    private static final Gate PASSED = new Gate(OVERWORLD, new AABB(new BlockPos(0, 0, 0)).minmax(
            new AABB(new BlockPos(0, 1, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** Another gate, a view pass looking into which shows something else. */
    private static final Gate ELSEWHERE = new Gate(OVERWORLD, new AABB(new BlockPos(9, 0, 0)).minmax(
            new AABB(new BlockPos(9, 1, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    private static final TargetResult.Passage THROUGH = new TargetResult.Passage(PASSED, new Vec3(0.5, 1.0, 0.0));

    /**
     * A far-side target's marks draw in the view pass of the gate the aim
     * passed, seen through it, and a near-side target's in the main frame
     * alone (decision aim-and-shots-carry-through-linked-gates).
     */
    @Nested
    class MarksDrawWhereTheTargetIsSeen {

        @Test
        void aFarSideTargetsMarksDrawInTheViewThroughTheGateItPassed() {
            assertTrue(GooTargetHighlighter.drawsMarks(true, PASSED, THROUGH),
                    "A far-side target's marks should draw in the view pass through the gate the aim passed");
        }

        @Test
        void aFarSideTargetsMarksDrawNotInTheMainFrame() {
            assertFalse(GooTargetHighlighter.drawsMarks(false, null, THROUGH),
                    "A far-side target's marks should not draw in the main frame");
        }

        @Test
        void aFarSideTargetsMarksDrawNotInAViewThroughAnotherGate() {
            assertFalse(GooTargetHighlighter.drawsMarks(true, ELSEWHERE, THROUGH),
                    "A far-side target's marks should not draw in a view through another gate");
        }

        @Test
        void aNearSideTargetsMarksDrawInTheMainFrameAlone() {
            assertTrue(GooTargetHighlighter.drawsMarks(false, null, null),
                    "A near-side target's marks should draw in the main frame");
            assertFalse(GooTargetHighlighter.drawsMarks(true, PASSED, null),
                    "A near-side target's marks should stay out of every view pass");
        }
    }

    @Nested
    class RootedToTheTarget {

        @Test
        void aMobEndsTheLineAtItsBoundingBoxCenter() {
            Entity cow = mock(Entity.class);
            AABB box = new AABB(6.5, 64.0, 0.5, 7.5, 65.4, 1.5);
            when(cow.getBoundingBox()).thenReturn(box);
            Vec3 cursorHit = new Vec3(7.0, 65.3, 1.2);
            TargetResult target = TargetResult.entity(cow, cursorHit);

            assertNotEquals(cursorHit, box.getCenter());
            assertEquals(box.getCenter(), GooTargetHighlighter.aimLineEnd(target));
        }

        @Test
        void aBlockEndsTheLineAtTheStruckFacesCenter() {
            Vec3 cursorHit = new Vec3(2.15, 64.8, 2.0);
            TargetResult target = TargetResult.block(POS, Direction.NORTH, cursorHit);

            assertNotEquals(cursorHit, target.resolveEndpoint());
            assertEquals(target.resolveEndpoint(), GooTargetHighlighter.aimLineEnd(target));
        }

        @Test
        void aCursorSwayingAcrossAMobLeavesTheLineEndPut() {
            Entity cow = mock(Entity.class);
            when(cow.getBoundingBox()).thenReturn(new AABB(6.5, 64.0, 0.5, 7.5, 65.4, 1.5));

            assertEquals(GooTargetHighlighter.aimLineEnd(TargetResult.entity(cow, new Vec3(6.6, 64.2, 0.6))),
                    GooTargetHighlighter.aimLineEnd(TargetResult.entity(cow, new Vec3(7.4, 65.3, 1.4))));
        }

        @Test
        void aCursorSwayingAcrossAFaceLeavesTheLineEndPut() {
            assertEquals(
                    GooTargetHighlighter.aimLineEnd(TargetResult.block(POS, Direction.NORTH, new Vec3(2.1, 64.1, 2.0))),
                    GooTargetHighlighter.aimLineEnd(TargetResult.block(POS, Direction.NORTH, new Vec3(2.9, 64.9, 2.0))));
        }
    }

    @Nested
    class FreeAimFollowsTheCursor {

        @Test
        void aPointOnABlockEndsTheLineAtThePoint() {
            Vec3 cursorHit = new Vec3(2.15, 65.0, 2.7);

            assertEquals(cursorHit,
                    GooTargetHighlighter.aimLineEnd(TargetResult.pointOnBlock(cursorHit, POS, Direction.UP)));
        }

        @Test
        void aPointInOpenAirEndsTheLineAtTheRaysEnd() {
            Vec3 reach = new Vec3(40, 90, -30);

            assertEquals(reach, GooTargetHighlighter.aimLineEnd(TargetResult.pointInAir(reach)));
        }

        @Test
        void nothingAimedDrawsNoLine() {
            assertNull(GooTargetHighlighter.aimLineEnd(TargetResult.NONE));
        }
    }

    /** A drag-sized ability is never thrown, so it draws no throw arc (decision blast-draws-no-throw-arc). */
    @Nested
    class DrawsTheThrowArc {

        @Test
        void aThrownArcDrawsItsAimLine() {
            assertEquals(true, GooTargetHighlighter.drawsAimLine(Delivery.of(DeliveryKind.ARC), false));
        }

        @Test
        void aDragSizedArcDrawsNoAimLine() {
            assertEquals(false, GooTargetHighlighter.drawsAimLine(Delivery.of(DeliveryKind.ARC), true));
        }

        @Test
        void theBlastAndTheBlackHoleAreDragSizedAndTheFrostOrbIsThrown() {
            assertEquals(Map.of("unstable_explode", true, "void_dark_star", true, "frost_orb", false),
                    Stream.of("unstable_explode", "void_dark_star", "frost_orb").collect(Collectors.toMap(
                            name -> name, name -> AbilityJson.decode(name).hasTag(AbilityTags.DRAG_SIZED))));
        }
    }
}
