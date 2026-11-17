package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A drip's strike feeds the cell over a struck top that can hold a world
 * goo, or the cell of its goo it struck short of a whole block; a strike on
 * a block's side at adhesion 3 coats that side's wall in the open cell
 * beside it; a strike on a top that holds none feeds nothing, for Goop and
 * Slick alike (decisions goop-and-slick-coat-lands-where-each-drip-arc-strikes,
 * slime-and-slick-are-one-volume-cell-tech, drip-striking-a-side-sticks-or-runs-by-adhesion,
 * adhesion-is-a-ladder-of-holds).
 */
class CellLandingTest {

    private static final BlockPos FLOOR = new BlockPos(8, 0, 2);
    private static final BlockPos CELL = new BlockPos(5, 1, 2);
    private static final BlockPos SLAB = new BlockPos(6, 0, 2);
    private static final BlockPos WALL = new BlockPos(3, 1, 2);
    /** A wall whose west neighbour already holds a film against it. */
    private static final BlockPos FILMED_WALL = new BlockPos(3, 1, 9);
    private static final int HOLDS_WHERE_IT_STRIKES = 3;
    /** The cell under an overhang, walled east and south. */
    private static final BlockPos UNDER_THE_OVERHANG = new BlockPos(12, 2, 12);
    /** An overhang whose cell below touches no wall. */
    private static final BlockPos OPEN_OVERHANG = new BlockPos(20, 5, 20);
    /** The floor an open overhang's drip drops to. */
    private static final BlockPos DROP_FLOOR = new BlockPos(20, 0, 20);
    private static final int HOLDS_TOPS = 1;
    /** A full block of goo standing in open air, no wall. */
    private static final BlockPos GOO_WALL = new BlockPos(3, 1, 15);

    /** Open cells stand over every top but the cell's and the slab's; the standing cell alone thickens. */
    private final CellLanding.Cells cells = new CellLanding.Cells() {
        @Override
        public boolean thickens(BlockPos pos) {
            return pos.equals(CELL);
        }

        @Override
        public boolean takesACell(BlockPos pos) {
            return !pos.equals(CELL) && !pos.equals(CELL.above()) && !pos.equals(SLAB.above());
        }

        @Override
        public Optional<SideCoat> sideAt(BlockPos cell, Direction side) {
            if (cell.equals(WALL) || cell.equals(FILMED_WALL) || cell.equals(CELL) || cell.equals(GOO_WALL)) {
                return Optional.empty();
            }
            boolean filmed = cell.equals(FILMED_WALL.west()) && side == Direction.EAST;
            return Optional.of(filmed ? SideCoat.ATTACHED : SideCoat.BARE);
        }

        @Override
        public boolean gooBelow(BlockPos cell) {
            return false;
        }

        @Override
        public boolean wallAt(BlockPos cell, Direction side) {
            return cell.equals(UNDER_THE_OVERHANG) && (side == Direction.EAST || side == Direction.SOUTH);
        }

        @Override
        public Optional<BlockPos> dropLanding(BlockPos cell) {
            return Optional.of(DROP_FLOOR.above());
        }

        @Override
        public boolean gooAt(BlockPos cell) {
            return cell.equals(GOO_WALL);
        }
    };

    /**
     * Goo is no wall: a drip striking the side of a block of goo it cannot
     * join clings to nothing there, at any adhesion, and falls past it to the
     * first top below, so it flows on to the goo there (decision
     * drip-striking-a-side-sticks-or-runs-by-adhesion).
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 3, 4})
    void aDripStrikingTheSideOfGooFallsPastIt(int adhesion) {
        assertEquals(Optional.of(CellFed.falling(GOO_WALL.west())),
                CellLanding.cellFedBy(strike(GOO_WALL, Direction.WEST), adhesion, cells));
    }

    /**
     * Adhesion 4 holds a block's underside where the drip strikes: the drip
     * joins the volume of the block under the struck block and marks its
     * ceiling attached; below 4 the strike takes the route goo holding no
     * ceiling takes, and 4 keeps the side holds below it (decisions
     * adhesion-is-a-ladder-of-holds, drip-striking-a-ceiling-runs-down-a-wall-or-drops).
     */
    @Nested
    class AdhesionFour {

        private static final int HOLDS_AN_UNDERSIDE = 4;

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3, 4})
        void onlyAdhesionFourHoldsAnUnderside(int adhesion) {
            BlockPos overhang = UNDER_THE_OVERHANG.above();
            CoatRay.BlockStrike nearTheEast = new CoatRay.BlockStrike(
                    new Vec3(overhang.getX() + 0.9, overhang.getY(), overhang.getZ() + 0.5), overhang, Direction.DOWN);
            Optional<CellFed> fed = CellLanding.cellFedBy(nearTheEast, adhesion, cells);
            Optional<CellFed> open = CellLanding.cellFedBy(strike(OPEN_OVERHANG, Direction.DOWN), adhesion, cells);

            if (adhesion == HOLDS_AN_UNDERSIDE) {
                assertEquals(Optional.of(CellFed.film(UNDER_THE_OVERHANG, Direction.UP)), fed);
                assertEquals(Optional.of(CellFed.film(OPEN_OVERHANG.below(), Direction.UP)), open);
            } else {
                assertEquals(Optional.of(CellFed.runny(UNDER_THE_OVERHANG, Direction.EAST)), fed);
                assertEquals(Optional.of(CellFed.falling(OPEN_OVERHANG.below())), open);
            }
        }

        /**
         * A block attached to a ceiling alone, with no floor, holds one layer
         * plus its goo's cohesion; the strike past that takes the route
         * below 4, down the nearest wall or falling to the first top below.
         */
        @ParameterizedTest
        @ValueSource(ints = {0, 4})
        void aCeilingHoldsALayerPerPointOfCohesionThenSendsTheRestOn(int cohesion) {
            for (int volume = 1; volume <= 1 + cohesion; volume++) {
                CellLanding.Cells holding = ceilingHolding(volume - 1, cohesion, true);
                assertEquals(Optional.of(CellFed.film(UNDER_THE_OVERHANG, Direction.UP)),
                        CellLanding.cellFedBy(strike(UNDER_THE_OVERHANG.above(), Direction.DOWN), HOLDS_AN_UNDERSIDE,
                                holding), "strike " + volume + " at cohesion " + cohesion);
            }
            assertEquals(Optional.of(CellFed.runny(UNDER_THE_OVERHANG, Direction.EAST)),
                    CellLanding.cellFedBy(strike(UNDER_THE_OVERHANG.above(), Direction.DOWN), HOLDS_AN_UNDERSIDE,
                            ceilingHolding(1 + cohesion, cohesion, true)));
            assertEquals(Optional.of(CellFed.falling(UNDER_THE_OVERHANG)),
                    CellLanding.cellFedBy(strike(UNDER_THE_OVERHANG.above(), Direction.DOWN), HOLDS_AN_UNDERSIDE,
                            ceilingHolding(1 + cohesion, cohesion, false)));
        }

        @Test
        void adhesionFourHoldsASideWhereItStrikes() {
            assertEquals(SideCoat.ATTACHED, CellLanding.sideCoatFor(HOLDS_AN_UNDERSIDE, SideCoat.BARE, false));
        }

        /**
         * The block under the overhang, attached to its ceiling alone and
         * holding a volume, with a wall to its east or none.
         */
        private CellLanding.Cells ceilingHolding(int volume, int cohesion, boolean walled) {
            return new CellLanding.Cells() {
                @Override
                public boolean thickens(BlockPos pos) {
                    return false;
                }

                @Override
                public boolean takesACell(BlockPos pos) {
                    return true;
                }

                @Override
                public Optional<SideCoat> sideAt(BlockPos cell, Direction side) {
                    return Optional.of(volume > 0 && side == Direction.UP ? SideCoat.ATTACHED : SideCoat.BARE);
                }

                @Override
                public boolean gooBelow(BlockPos cell) {
                    return false;
                }

                @Override
                public boolean wallAt(BlockPos cell, Direction side) {
                    return walled && side == Direction.EAST;
                }

                @Override
                public Optional<BlockPos> dropLanding(BlockPos cell) {
                    return Optional.of(DROP_FLOOR.above());
                }

                @Override
                public int volumeAt(BlockPos cell) {
                    return volume;
                }

                @Override
                public int attachedAt(BlockPos cell) {
                    return volume > 0 ? 1 : 0;
                }

                @Override
                public int cohesion() {
                    return cohesion;
                }
            };
        }
    }

    /**
     * Goo holds no ceiling: a drip striking a block's underside runs down a
     * wall the cell under it touches, the nearest, at any adhesion, and
     * where it touches none drops to the first top below
     * (decision drip-striking-a-ceiling-runs-down-a-wall-or-drops).
     */
    @Nested
    class Underside {

        @Test
        void aStrikeOnAnUndersideBesideAWallRunsDownTheNearestWall() {
            BlockPos overhang = UNDER_THE_OVERHANG.above();
            CoatRay.BlockStrike nearTheEast = new CoatRay.BlockStrike(
                    new Vec3(overhang.getX() + 0.9, overhang.getY(), overhang.getZ() + 0.5), overhang, Direction.DOWN);

            assertEquals(Optional.of(CellFed.runny(UNDER_THE_OVERHANG, Direction.EAST)),
                    CellLanding.cellFedBy(nearTheEast, HOLDS_WHERE_IT_STRIKES, cells));
        }

        /**
         * Where the cell under it touches no wall, the layer falls from that
         * cell to the first top below, landing there when its drip arrives
         * (decision falling-layer-lands-when-its-drip-arrives).
         */
        @Test
        void aStrikeOnAnUndersideTouchingNoWallFallsFromTheCellUnderIt() {
            assertEquals(Optional.of(CellFed.falling(OPEN_OVERHANG.below())),
                    CellLanding.cellFedBy(strike(OPEN_OVERHANG, Direction.DOWN), 0, cells));
        }
    }

    /**
     * What a side strike lays follows the adhesion ladder, read whole without a
     * goo dialed to each rung (decisions adhesion-is-a-ladder-of-holds,
     * drip-striking-a-side-sticks-or-runs-by-adhesion).
     */
    @Nested
    class Ladder {

        @Test
        void adhesionZeroAndOneRunAtABareSide() {
            assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(0, SideCoat.BARE, true));
            assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(HOLDS_TOPS, SideCoat.BARE, true));
        }

        @Test
        void adhesionTwoHoldsOnlyOverGooOfItsType() {
            assertEquals(SideCoat.ATTACHED, CellLanding.sideCoatFor(2, SideCoat.BARE, true));
            assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(2, SideCoat.BARE, false));
        }

        @Test
        void adhesionThreeHoldsWhereItStrikes() {
            assertEquals(SideCoat.ATTACHED, CellLanding.sideCoatFor(HOLDS_WHERE_IT_STRIKES, SideCoat.BARE, false));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3})
        void aSideAlreadyHoldingItsLayerRunsAtAnyAdhesion(int adhesion) {
            assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(adhesion, SideCoat.ATTACHED, true));
        }
    }

    /**
     * A block with no floor holds one layer plus its goo's cohesion for each
     * side it is attached to, 15 at the most: a strike on its attached side
     * joins the volume while it holds fewer, and the strike past that runs
     * (decisions drip-striking-a-side-sticks-or-runs-by-adhesion,
     * attached-goo-draws-per-attachment-and-divides-its-volume).
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4, 15})
    void aWallHoldsALayerPerPointOfCohesionThenRuns(int cohesion) {
        int holds = Math.min(VolumeCellBlock.MAX_LAYERS, 1 + cohesion);
        assertEquals(SideCoat.ATTACHED, CellLanding.sideCoatFor(HOLDS_WHERE_IT_STRIKES, cohesion,
                new CellLanding.Beside(SideCoat.BARE, 0, 0, false, false)), "the first strike on a bare wall");
        for (int volume = 1; volume < holds; volume++) {
            assertEquals(SideCoat.ATTACHED, CellLanding.sideCoatFor(HOLDS_WHERE_IT_STRIKES, cohesion,
                    new CellLanding.Beside(SideCoat.ATTACHED, volume, 1, false, false)),
                    "a strike on a block holding " + volume + " at cohesion " + cohesion);
        }
        assertEquals(SideCoat.RUNNY, CellLanding.sideCoatFor(HOLDS_WHERE_IT_STRIKES, cohesion,
                new CellLanding.Beside(SideCoat.ATTACHED, holds, 1, false, false)),
                "the strike past " + holds + " at cohesion " + cohesion);
    }

    @Test
    void aSideStrikeAtAdhesionZeroRunsDownTheWallInTheCellBesideIt() {
        assertEquals(Optional.of(CellFed.runny(WALL.west(), Direction.EAST)),
                CellLanding.cellFedBy(strike(WALL, Direction.WEST), 0, cells));
    }

    private static CoatRay.BlockStrike strike(BlockPos block, Direction face) {
        return new CoatRay.BlockStrike(Vec3.atCenterOf(block).relative(face, 0.5), block, face);
    }

    @Test
    void aStrikeOnAFloorsTopFeedsTheCellOverIt() {
        assertEquals(Optional.of(CellFed.fill(FLOOR.above())),
                CellLanding.cellFedBy(strike(FLOOR, Direction.UP), HOLDS_TOPS, cells));
    }

    @Test
    void aStrikeOnAStandingCellFeedsThatCell() {
        assertEquals(Optional.of(CellFed.fill(CELL)), CellLanding.cellFedBy(strike(CELL, Direction.UP), HOLDS_TOPS,
                cells));
    }

    @Test
    void aStrikeOnATopHoldingNoCellFeedsNothing() {
        assertEquals(Optional.empty(), CellLanding.cellFedBy(strike(SLAB, Direction.UP), HOLDS_TOPS, cells));
    }

    /**
     * At adhesion 3 a side strike coats the open cell beside the struck face,
     * on the cell's side facing the struck block.
     */
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void aSideStrikeAtAdhesionThreeCoatsTheWallInTheCellBesideIt(Direction face) {
        assertEquals(Optional.of(CellFed.film(WALL.relative(face), face.getOpposite())),
                CellLanding.cellFedBy(strike(WALL, face), HOLDS_WHERE_IT_STRIKES, cells));
    }

    /**
     * A drip striking a film's own face, the slab standing out from its wall,
     * strikes the wall the film coats and runs there.
     */
    @Test
    void aStrikeOnAFilmsFaceRunsOnThatFilmsSide() {
        assertEquals(Optional.of(CellFed.runny(FILMED_WALL.west(), Direction.EAST)),
                CellLanding.cellFedBy(strike(FILMED_WALL.west(), Direction.WEST), HOLDS_WHERE_IT_STRIKES, cells));
    }

    /**
     * A drip striking the side of its goo's fill joins that fill, as one striking its top does.
     */
    @Test
    void aStrikeOnTheSideOfItsGoosFillJoinsThatFill() {
        assertEquals(Optional.of(CellFed.fill(CELL)), CellLanding.cellFedBy(strike(CELL, Direction.WEST), 0, cells));
    }

    /**
     * A drip sticking to a side adds its layer to the volume of the block
     * beside that side: beside an empty space it stands a block of volume 1,
     * and beside a block of volume 6 it leaves volume 7
     * (decision goo-cell-holds-a-fill-and-a-state-per-side).
     */
    @Test
    void aDripStickingToASideAddsItsLayerToTheVolumeBesideIt() {
        assertEquals(1, VolumeCellBlock.volumeOnceStuck(0));
        assertEquals(7, VolumeCellBlock.volumeOnceStuck(6));
    }

    @Test
    void aSideStrikeOnAWallAlreadyFilmedRuns() {
        assertEquals(Optional.of(CellFed.runny(FILMED_WALL.west(), Direction.EAST)),
                CellLanding.cellFedBy(strike(FILMED_WALL, Direction.WEST), HOLDS_WHERE_IT_STRIKES, cells));
    }
}
