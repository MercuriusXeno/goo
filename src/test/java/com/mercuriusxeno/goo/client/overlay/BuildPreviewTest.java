package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.BuildFootprint;
import com.mercuriusxeno.goo.ability.BuildGrowth;
import com.mercuriusxeno.goo.data.GooFraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Build's preview ripples over exactly the footprint's ground cells, its
 * rings rectangles scaled from the footprint's center out to its edge, and
 * ghosts exactly the cells the rise would fill above them, drawing only the
 * ghost's outer faces so no seam shows between its blocks
 * (decision spire-previews-as-a-surface-ripple-and-ghost-stone).
 */
class BuildPreviewTest {

    private static final int RISE = 4;
    /** A 2x3 footprint on ground at y 64. */
    private static final BuildFootprint FOOTPRINT =
            new BuildFootprint(new BlockPos(10, 64, 20), new BlockPos(11, 64, 22), RISE);
    private static final GooFraction PER_BLOCK = GooFraction.of(7);
    /** The ground cell a chest lies one under, in the chest world. */
    private static final BlockPos CHEST_GROUND = new BlockPos(11, 64, 21);

    /**
     * A world of dirt up to y 64 and open cells over it, a chest one under the ground cell named where one is given.
     *
     * @param chestGround the ground cell a chest lies under, or null for none
     * @return the world
     */
    private static BlockGetter ground(@Nullable BlockPos chestGround) {
        BlockState open = mock(BlockState.class);
        when(open.canBeReplaced()).thenReturn(true);
        BlockState dirt = mock(BlockState.class);
        BlockState chest = mock(BlockState.class);
        when(chest.hasBlockEntity()).thenReturn(true);
        BlockGetter world = mock(BlockGetter.class);
        when(world.getMinY()).thenReturn(-64);
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            if (chestGround != null && pos.equals(chestGround.below())) {
                return chest;
            }
            return pos.getY() <= 64 ? dirt : open;
        });
        return world;
    }

    private static List<BlockPos> ghostOverSolidGround() {
        return BuildPreview.ghostCells(BuildGrowth.plan(ground(null), FOOTPRINT, PER_BLOCK));
    }

    /**
     * A column the plan fizzles on a chest shows no ghost stone, and the growth the cost readout reads prices the
     * remaining columns alone.
     * decision column-meeting-a-block-entity-fizzles-at-no-cost
     */
    @Test
    void aFizzledColumnShowsNoGhostAndTheCostPricesTheRemainingColumns() {
        BuildGrowth growth = BuildGrowth.plan(ground(CHEST_GROUND), FOOTPRINT, PER_BLOCK);

        List<BlockPos> ghost = BuildPreview.ghostCells(growth);
        List<BlockPos> expected = FOOTPRINT.groundCells().stream().filter(cell -> !cell.equals(CHEST_GROUND))
                .flatMap(cell -> IntStream.rangeClosed(1, RISE).mapToObj(cell::above)).toList();
        assertEquals(Set.copyOf(expected), Set.copyOf(ghost));
        assertEquals(expected.size(), ghost.size());
        int remainingColumns = FOOTPRINT.groundCells().size() - 1;
        assertEquals(PER_BLOCK.times((long) remainingColumns * RISE).ceiling(), growth.cost());
    }

    @Test
    void theRippleCoversEachGroundCellOnce() {
        List<BlockPos> ripple = BuildPreview.rippleCells(FOOTPRINT);
        assertEquals(6, ripple.size());
        assertEquals(Set.copyOf(FOOTPRINT.groundCells()), Set.copyOf(ripple));
    }

    @Test
    void theGhostFillsTheRiseAboveEveryGroundCell() {
        List<BlockPos> ghost = ghostOverSolidGround();
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
        Vec3[] ring = BuildPreview.ringCorners(FOOTPRINT, 1);
        double y = 65 + BuildPreview.RING_LIFT;
        assertEquals(new Vec3(10, y, 20), ring[0]);
        assertEquals(new Vec3(10, y, 23), ring[1]);
        assertEquals(new Vec3(12, y, 23), ring[2]);
        assertEquals(new Vec3(12, y, 20), ring[3]);
        assertEquals(ring[0], ring[4], "the ring should close on its first corner");
    }

    @Test
    void aRingHalfwayIsTheOutlineScaledByHalfFromTheCenter() {
        Vec3[] ring = BuildPreview.ringCorners(FOOTPRINT, 0.5);
        double y = 65 + BuildPreview.RING_LIFT;
        assertEquals(new Vec3(10.5, y, 20.75), ring[0]);
        assertEquals(new Vec3(11.5, y, 22.25), ring[2]);
    }

    @Test
    void aGhostCellDrawsOnlyTheFacesNoOtherGhostCellCovers() {
        Set<BlockPos> ghost = Set.copyOf(ghostOverSolidGround());
        BlockPos bottomCorner = new BlockPos(10, 65, 20);
        assertEquals(Set.of(Direction.DOWN, Direction.WEST, Direction.NORTH),
                Set.copyOf(BuildPreview.outerFaces(bottomCorner, ghost)));
        BlockPos inside = new BlockPos(11, 66, 21);
        assertEquals(Set.of(Direction.EAST), Set.copyOf(BuildPreview.outerFaces(inside, ghost)));
    }

    @Test
    void theSteadyEdgeTracesTheFootprintsOutlineUnderTheRings() {
        Vec3[] edge = BuildPreview.edgeOutline(FOOTPRINT);
        double y = 65 + BuildPreview.RING_LIFT;
        assertEquals(java.util.List.of(new Vec3(10, y, 20), new Vec3(10, y, 23), new Vec3(12, y, 23),
                new Vec3(12, y, 20), new Vec3(10, y, 20)), java.util.List.of(edge));
    }

    @Test
    void eachOuterFaceAlsoFacesInwardFromItsOutsideNeighbourSoTheGhostShowsFromWithin() {
        Set<BlockPos> ghost = Set.copyOf(ghostOverSolidGround());
        BlockPos bottomCorner = new BlockPos(10, 65, 20);
        assertEquals(Set.of(new BuildPreview.Face(bottomCorner.below(), Direction.UP),
                        new BuildPreview.Face(bottomCorner.west(), Direction.EAST),
                        new BuildPreview.Face(bottomCorner.north(), Direction.SOUTH)),
                Set.copyOf(BuildPreview.inwardFaces(bottomCorner, ghost)));
    }
}
