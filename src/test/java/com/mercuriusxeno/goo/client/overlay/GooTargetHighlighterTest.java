package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the aim's marks draw, and the one line the aim pass draws.
 */
class GooTargetHighlighterTest {

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

    /**
     * The knife lines are the only line the aim pass draws; an arc, a beam and
     * a mob ability's arc draw none, the vanilla crosshair aiming them
     * (decisions vanilla-crosshair-aims-everything-without-reticule-line-or-touch-ring,
     * knives-aims-a-spread-fan-not-a-blob-line).
     */
    @Nested
    class TheAimPassDrawsTheKnifeLinesAlone {

        @Test
        void aChargingKnivesPressDrawsTheKnifeLines() {
            assertEquals(GooTargetHighlighter.AimPassLine.KNIFE_LINES,
                    GooTargetHighlighter.aimPassLine(AbilityJson.decode("crystal_knives").delivery(), true));
        }

        @Test
        void anArcABeamAndAMobAbilitysArcDrawNoLine() {
            assertEquals(Map.of("arc", GooTargetHighlighter.AimPassLine.NONE,
                            "beam", GooTargetHighlighter.AimPassLine.NONE,
                            "hex_charm", GooTargetHighlighter.AimPassLine.NONE),
                    Map.of("arc", GooTargetHighlighter.aimPassLine(Delivery.ARC, true),
                            "beam", GooTargetHighlighter.aimPassLine(Delivery.BEAM, true),
                            "hex_charm", GooTargetHighlighter.aimPassLine(
                                    AbilityJson.decode("hex_charm").delivery(), true)));
        }
    }

    /** The blast is sized by a drag rather than thrown (decision blast-draws-no-throw-arc). */
    @Nested
    class DragSizedAbilities {

        @Test
        void theBlastIsDragSizedAndTheFrostOrbIsThrown() {
            assertEquals(Map.of("unstable_explode", true, "frost_orb", false),
                    Stream.of("unstable_explode", "frost_orb").collect(Collectors.toMap(
                            name -> name, name -> AbilityJson.decode(name).hasTag(AbilityTags.DRAG_SIZED))));
        }
    }
}
