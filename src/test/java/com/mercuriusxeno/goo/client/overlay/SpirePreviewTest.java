package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spire's preview ripples over exactly the footprint's ground cells, its
 * rings rectangles scaled from the footprint's center out to its edge, and
 * ghosts exactly the cells the rise would fill above them, drawing only the
 * ghost's outer faces so no seam shows between its blocks
 * (decision spire-previews-as-a-surface-ripple-and-ghost-stone).
 */
class SpirePreviewTest {

    private static final int RISE = 4;
    /** A 2x3 footprint on ground at y 64. */
    private static final SpireFootprint FOOTPRINT =
            new SpireFootprint(new BlockPos(10, 64, 20), new BlockPos(11, 64, 22), RISE);

    @Test
    void theRippleCoversEachGroundCellOnce() {
        List<BlockPos> ripple = SpirePreview.rippleCells(FOOTPRINT);
        assertEquals(6, ripple.size());
        assertEquals(Set.copyOf(FOOTPRINT.groundCells()), Set.copyOf(ripple));
    }

    @Test
    void theGhostFillsTheRiseAboveEveryGroundCell() {
        List<BlockPos> ghost = SpirePreview.ghostCells(FOOTPRINT);
        assertEquals(24, ghost.size());
        assertEquals(24, new HashSet<>(ghost).size());
        for (BlockPos cell : ghost) {
            assertTrue(cell.getY() >= 65 && cell.getY() <= 64 + RISE, cell + " lies outside the rise");
            assertTrue(FOOTPRINT.groundCells().contains(new BlockPos(cell.getX(), 64, cell.getZ())),
                    cell + " stands over no ground cell");
        }
    }

    @Test
    void aRingAtTheEdgeTracesTheFootprintsOutline() {
        // The 2x3 footprint spans x 10 to 12 and z 20 to 23, its top at y 65.
        Vec3[] ring = SpirePreview.ringCorners(FOOTPRINT, 1);
        double y = 65 + SpirePreview.RING_LIFT;
        assertEquals(new Vec3(10, y, 20), ring[0]);
        assertEquals(new Vec3(10, y, 23), ring[1]);
        assertEquals(new Vec3(12, y, 23), ring[2]);
        assertEquals(new Vec3(12, y, 20), ring[3]);
        assertEquals(ring[0], ring[4], "the ring should close on its first corner");
    }

    @Test
    void aRingHalfwayIsTheOutlineScaledByHalfFromTheCenter() {
        Vec3[] ring = SpirePreview.ringCorners(FOOTPRINT, 0.5);
        double y = 65 + SpirePreview.RING_LIFT;
        assertEquals(new Vec3(10.5, y, 20.75), ring[0]);
        assertEquals(new Vec3(11.5, y, 22.25), ring[2]);
    }

    @Test
    void aGhostCellDrawsOnlyTheFacesNoOtherGhostCellCovers() {
        Set<BlockPos> ghost = Set.copyOf(SpirePreview.ghostCells(FOOTPRINT));
        BlockPos bottomCorner = new BlockPos(10, 65, 20);
        assertEquals(Set.of(Direction.DOWN, Direction.WEST, Direction.NORTH),
                Set.copyOf(SpirePreview.outerFaces(bottomCorner, ghost)));
        BlockPos inside = new BlockPos(11, 66, 21);
        assertEquals(Set.of(Direction.EAST), Set.copyOf(SpirePreview.outerFaces(inside, ghost)));
    }

    @Test
    void theSteadyEdgeTracesTheFootprintsOutlineUnderTheRings() {
        Vec3[] edge = SpirePreview.edgeOutline(FOOTPRINT);
        double y = 65 + SpirePreview.RING_LIFT;
        assertEquals(java.util.List.of(new Vec3(10, y, 20), new Vec3(10, y, 23), new Vec3(12, y, 23),
                new Vec3(12, y, 20), new Vec3(10, y, 20)), java.util.List.of(edge));
    }
}
