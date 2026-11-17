package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.Optional;

/**
 * How a spray drip lands its goo on the volume cell, the one landing every
 * stream laying a world goo runs: a drip striking a block's top stands a
 * one-layer cell in the open cell over it, where that top can hold a world
 * goo, and a drip striking a cell of its goo short of fifteen layers adds a
 * layer to it, in the tick it strikes; a drip striking a block's side coats
 * that wall in the open cell beside it, a film where its goo's adhesion
 * holds it there and a runny layer running down the wall where it does not;
 * a drip striking a block's underside runs down a wall the cell under it
 * touches or drops to the first top below; a full cell or a cell of another
 * goo takes nothing. Each layer pays a fifteenth of the
 * laying ability's cost as it lands, and a layer the player cannot pay for
 * does not land.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * slime-and-slick-are-one-volume-cell-tech
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
 * drip-striking-a-side-sticks-or-runs-by-adhesion
 * adhesion-is-a-ladder-of-holds
 * drip-striking-a-ceiling-runs-down-a-wall-or-drops
 */
final class CellLanding {

    private CellLanding() {
    }

    /**
     * What the landing reads of the cells a strike may feed.
     */
    interface Cells {

        /**
         * @param pos a cell
         * @return true where the cell holds this goo short of a whole block
         */
        boolean thickens(BlockPos pos);

        /**
         * @param pos a cell
         * @return true where the cell stands open over a top that can hold a world goo
         */
        boolean takesACell(BlockPos pos);

        /**
         * @param cell a cell beside a struck wall
         * @param side the cell's side facing that wall
         * @return what that side holds, bare for an open cell, empty where the cell holds another
         *         block or the wall has no face to coat
         */
        Optional<SideCoat> sideAt(BlockPos cell, Direction side);

        /**
         * @param cell a cell
         * @return true where a cell of this goo stands in the cell below it
         */
        boolean gooBelow(BlockPos cell);

        /**
         * @param cell a cell
         * @return true where a block of any goo stands there, which no goo clings to
         */
        default boolean gooAt(BlockPos cell) {
            return false;
        }

        /**
         * @param cell a cell
         * @return the volume a block of this goo there holds, 0 for any other
         */
        default int volumeAt(BlockPos cell) {
            return 0;
        }

        /**
         * @param cell a cell
         * @return the sides a block of this goo there is attached to
         */
        default int attachedAt(BlockPos cell) {
            return 0;
        }

        /**
         * @param cell a cell
         * @return true where a block there stands on a floor
         */
        default boolean floorAt(BlockPos cell) {
            return false;
        }

        /**
         * @return the goo's cohesion
         */
        default int cohesion() {
            return 0;
        }

        /**
         * @param cell an open cell
         * @param side one of its sides
         * @return true where the block on that side has a sturdy face toward the cell, a wall
         */
        boolean wallAt(BlockPos cell, Direction side);

        /**
         * @param cell an open cell a layer drops through
         * @return the cell whose fill the layer joins on the first top below, empty where it finds none
         */
        Optional<BlockPos> dropLanding(BlockPos cell);
    }

    /**
     * The cell a drip's strike feeds with a goo, read from the caster's level.
     *
     * @param host   the caster's host
     * @param strike where the drip struck
     * @param cell   the goo's volume cell block
     * @return the cell and the side fed, empty where the strike lays nothing
     */
    static Optional<CellFed> cellFed(ChannelHost host, CoatRay.BlockStrike strike, VolumeCellBlock cell) {
        return cellFedBy(strike, cell.adhesionAt(host.level()), new LevelCells(host, cell));
    }

    /**
     * What the landing reads of the cells a strike may feed, from the caster's level.
     *
     * @param host the caster's host
     * @param cell the goo's volume cell block
     */
    private record LevelCells(ChannelHost host, VolumeCellBlock cell) implements Cells {

        @Override
        public boolean thickens(BlockPos pos) {
            BlockState standing = host.stateAt(pos);
            return standing.is(cell) && VolumeCellBlock.layersOf(standing) < VolumeCellBlock.MAX_LAYERS;
        }

        @Override
        public boolean takesACell(BlockPos pos) {
            return host.airAt(pos) && cell.liesOn(host.level(), pos);
        }

        @Override
        public Optional<SideCoat> sideAt(BlockPos pos, Direction side) {
            BlockState standing = host.stateAt(pos);
            boolean holds = standing.isAir() || standing.is(cell);
            return holds && VolumeCellBlock.clingsTo(host.level(), pos, side)
                    ? Optional.of(VolumeCellBlock.coatOn(standing, side)) : Optional.empty();
        }

        @Override
        public boolean gooBelow(BlockPos pos) {
            return host.stateAt(pos.below()).is(cell);
        }

        @Override
        public boolean gooAt(BlockPos pos) {
            return host.stateAt(pos).getBlock() instanceof VolumeCellBlock;
        }

        @Override
        public int volumeAt(BlockPos pos) {
            BlockState standing = host.stateAt(pos);
            return standing.is(cell) ? VolumeCellBlock.layersOf(standing) : 0;
        }

        @Override
        public int attachedAt(BlockPos pos) {
            BlockState standing = host.stateAt(pos);
            return standing.is(cell) ? VolumeCellBlock.attachedSidesOf(standing) : 0;
        }

        @Override
        public boolean floorAt(BlockPos pos) {
            return cell.onAFloor(host.level(), pos);
        }

        @Override
        public int cohesion() {
            return cell.cohesionAt(host.level());
        }

        @Override
        public boolean wallAt(BlockPos pos, Direction side) {
            return VolumeCellBlock.clingsTo(host.level(), pos, side);
        }

        @Override
        public Optional<BlockPos> dropLanding(BlockPos pos) {
            return cell.landingBelow(host.level(), pos);
        }
    }

    /**
     * The cell a drip's strike feeds: a cell of the goo short of a whole
     * block struck on its top, else the open cell over a struck top that can
     * hold a world goo; a strike on a block's side at adhesion 3 coats the
     * side of the open cell beside the struck face that faces the struck
     * block; any other strike feeds nothing.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param strike   where the drip struck
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param cells    what the landing reads of the cells
     * @return the cell and the side fed, empty where the strike lays nothing
     */
    static Optional<CellFed> cellFedBy(CoatRay.BlockStrike strike, int adhesion, Cells cells) {
        if (strike.face().getAxis().isHorizontal()) {
            return sideFedBy(strike, adhesion, cells);
        }
        if (strike.face() == Direction.DOWN) {
            return undersideFedBy(strike, adhesion, cells);
        }
        BlockPos struck = strike.block();
        if (cells.thickens(struck)) {
            return Optional.of(CellFed.fill(struck));
        }
        BlockPos over = struck.above();
        return cells.takesACell(over) ? Optional.of(CellFed.fill(over)) : Optional.empty();
    }

    /**
     * What a strike on a block's side lays in the open cell beside the
     * struck face, on its side facing the struck block: a film or a runny
     * layer, by the adhesion ladder; a strike on the face of a film or a
     * runny layer lays a runny layer on that side of the struck cell, and a
     * strike on the side of its goo's fill joins that fill.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * adhesion-is-a-ladder-of-holds
     *
     * @param strike   where the drip struck a block's side
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param cells    what the landing reads of the cells
     * @return the cell and the side fed, empty where the cell beside cannot hold the goo
     */
    private static Optional<CellFed> sideFedBy(CoatRay.BlockStrike strike, int adhesion, Cells cells) {
        Direction wall = strike.face().getOpposite();
        // a drip striking a film's own face strikes the wall the film already coats, its block taking it or not
        Optional<SideCoat> onTheStruck = cells.sideAt(strike.block(), wall).filter(SideCoat::holdsALayer);
        if (onTheStruck.isPresent()) {
            return Optional.of(fedBeside(strike.block(), wall, adhesion, onTheStruck.get(), cells));
        }
        // a drip striking the side of its goo's fill joins that fill, as one striking its top does
        if (cells.thickens(strike.block())) {
            return Optional.of(CellFed.fill(strike.block()));
        }
        BlockPos beside = strike.block().relative(strike.face());
        if (cells.gooAt(strike.block())) {
            // drip-striking-a-side-sticks-or-runs-by-adhesion: goo is no wall, so the drip falls past it
            return cells.dropLanding(beside).map(landing -> CellFed.falling(beside));
        }
        return cells.sideAt(beside, wall).map(standing -> fedBeside(beside, wall, adhesion, standing, cells));
    }

    /**
     * What a side strike lays in a block beside a wall: its layer joining
     * the block's volume with that side attached, or running down the wall.
     *
     * @param cell     the block beside the struck wall
     * @param wall     its side facing that wall
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param standing what that side holds
     * @param cells    what the landing reads of the cells
     * @return the cell and the side fed
     */
    private static CellFed fedBeside(BlockPos cell, Direction wall, int adhesion, SideCoat standing, Cells cells) {
        Beside beside = new Beside(standing, cells.volumeAt(cell), cells.attachedAt(cell), cells.floorAt(cell),
                cells.gooBelow(cell));
        return sideCoatFor(adhesion, cells.cohesion(), beside) == SideCoat.ATTACHED ? CellFed.film(cell, wall)
                : CellFed.runny(cell, wall);
    }

    /**
     * What the landing reads of a block beside a struck wall.
     *
     * @param coat          what its side facing the wall holds
     * @param volume        the volume it holds, 0 for an empty space
     * @param attachedSides the sides it is attached to
     * @param onAFloor      whether it stands on a floor
     * @param gooBelow      whether goo of its type stands in the cell below it
     */
    record Beside(SideCoat coat, int volume, int attachedSides, boolean onAFloor, boolean gooBelow) {
    }

    /**
     * What a strike on a block's underside lays: at adhesion 4 its layer
     * joins the volume of the block under the struck block, its ceiling
     * attached, while that block holds fewer than its hold; past that, and
     * for goo below 4, which holds no ceiling, a
     * runny layer on the wall the cell under it touches, the wall nearest
     * the strike where it touches several, at any adhesion; where it touches
     * none, the layer falls from that cell to the first top below and joins
     * the fill there when its drip arrives.
     * drip-striking-a-ceiling-runs-down-a-wall-or-drops
     *
     * @param strike   where the drip struck a block's underside
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param cells  what the landing reads of the cells
     * @return the cell and the side fed, empty where the layer finds no top
     */
    private static Optional<CellFed> undersideFedBy(CoatRay.BlockStrike strike, int adhesion, Cells cells) {
        BlockPos under = strike.block().below();
        if (adhesion >= GooTypeDefinition.HOLDS_AN_UNDERSIDE) {
            // adhesion-is-a-ladder-of-holds: at 4 the underside holds the layer, as a held wall does
            Optional<CellFed> held = cells.sideAt(under, Direction.UP)
                    .map(ceiling -> fedBeside(under, Direction.UP, adhesion, ceiling, cells))
                    .filter(fed -> fed.coat() == SideCoat.ATTACHED);
            if (held.isPresent()) {
                return held;
            }
        }
        Optional<Direction> nearest = Direction.Plane.HORIZONTAL.stream().filter(side -> cells.wallAt(under, side))
                .min(Comparator.comparingDouble(side -> reachTo(strike.point(), under, side)));
        if (nearest.isPresent()) {
            return Optional.of(CellFed.runny(under, nearest.get()));
        }
        return cells.dropLanding(under).map(landing -> CellFed.falling(under));
    }

    /**
     * How far a point stands from one side's wall of a cell, across the cell.
     *
     * @param point a point over the cell
     * @param cell  the cell
     * @param side  the side whose wall is measured to
     * @return the distance
     */
    private static double reachTo(Vec3 point, BlockPos cell, Direction side) {
        double plane = side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? side.getAxis().choose(cell.getX(), cell.getY(), cell.getZ()) + 1
                : side.getAxis().choose(cell.getX(), cell.getY(), cell.getZ());
        return Math.abs(side.getAxis().choose(point.x, point.y, point.z) - plane);
    }

    /**
     * What a side strike's layer stands as, the whole adhesion ladder in one
     * rule: at 0 or 1 it runs; at 2 it holds as a film where goo of its type
     * stands in the cell below and runs where none does; at 3 it holds where
     * it strikes; and at any adhesion it runs where the side already holds its
     * one layer.
     * adhesion-is-a-ladder-of-holds
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param standing what the side already holds
     * @param gooBelow whether goo of its type stands in the cell below
     * @return a film or a runny layer
     */
    static SideCoat sideCoatFor(int adhesion, SideCoat standing, boolean gooBelow) {
        int volume = standing.holdsALayer() ? 1 : 0;
        int attached = standing == SideCoat.ATTACHED ? 1 : 0;
        return sideCoatFor(adhesion, 0, new Beside(standing, volume, attached, false, gooBelow));
    }

    /**
     * What a side strike's layer stands as, by the adhesion ladder and the
     * goo's cohesion: where the adhesion holds that side, a bare side takes
     * the layer attached, and an attached side takes it into the block's
     * volume while a block with no floor holds fewer than one layer plus the
     * cohesion for each side it is attached to, a block on a floor always;
     * the layer past that, a runny side, or a side the adhesion does not hold
     * runs down the wall.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param cohesion the goo's cohesion
     * @param beside   the block beside the struck wall
     * @return attached where the layer joins the block, runny where it runs
     */
    static SideCoat sideCoatFor(int adhesion, int cohesion, Beside beside) {
        boolean holds = holdsTheSide(adhesion, beside.gooBelow()) && beside.coat() != SideCoat.RUNNY;
        return holds && hasRoom(cohesion, beside) ? SideCoat.ATTACHED : SideCoat.RUNNY;
    }

    /**
     * Whether a goo's adhesion holds a block's side: at 3 and up where it
     * strikes, at 2 only over goo of its type.
     * adhesion-is-a-ladder-of-holds
     *
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param gooBelow whether goo of its type stands in the cell below
     * @return true where the side holds
     */
    private static boolean holdsTheSide(int adhesion, boolean gooBelow) {
        boolean holdsOverGoo = adhesion == GooTypeDefinition.HOLDS_WHERE_IT_STRIKES - 1 && gooBelow;
        return adhesion >= GooTypeDefinition.HOLDS_WHERE_IT_STRIKES || holdsOverGoo;
    }

    /**
     * Whether a block beside a held wall takes one more layer: a bare side
     * takes it attached, a block on a floor always, and a block with no floor
     * while it holds fewer than its hold.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param cohesion the goo's cohesion
     * @param beside   the block beside the struck wall
     * @return true where the layer joins the block
     */
    private static boolean hasRoom(int cohesion, Beside beside) {
        return beside.coat() == SideCoat.BARE || beside.onAFloor()
                || beside.volume() < VolumeCellBlock.holdOf(cohesion, beside.attachedSides());
    }

    /**
     * Lands the drip's layer in its cell once the player pays the layer's
     * price, a fifteenth of the laying ability's cost: a fill layer spreads
     * the cell at once into its side cells, and a film clings to its wall; a
     * layer the player cannot pay for does not land.
     * drip-creates-a-volume-cell-immediately
     * drip-on-a-standing-cell-increases-it
     * spread-decreases-the-cell-it-leaves
     * goop-drains-per-cubic-meter-of-slime-placed
     * slick-drains-per-film-it-lays
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param host the caster's host
     * @param fed  the cell and the side a strike fed
     * @param cell the goo's volume cell block
     */
    static void feed(ChannelHost host, CellFed fed, VolumeCellBlock cell) {
        BlockPos pos = fed.cell();
        if (fed.falls()) {
            // falling-layer-lands-when-its-drip-arrives: the layer is paid once, as the strike starts its fall
            if (host.placementPrice().pay(VolumeCellBlock.MAX_LAYERS)) {
                cell.beginFall(host.level(), pos, pos.getY() + 1.0, host.placingPlayer());
            }
        } else if (fed.side().isPresent()) {
            feedSide(host, pos, fed.side().get(), fed.coat(), cell);
        } else {
            feedFill(host, pos, cell);
        }
    }

    /**
     * Lands a side strike's layer once the player pays for it: a runny layer
     * wherever its run first takes it, a film on its wall.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param host the caster's host
     * @param pos  the cell beside the struck wall
     * @param side the cell's side facing that wall
     * @param coat what the layer stands as there
     * @param cell the goo's volume cell block
     */
    private static void feedSide(ChannelHost host, BlockPos pos, Direction side, SideCoat coat, VolumeCellBlock cell) {
        if (coat == SideCoat.RUNNY) {
            cell.runnyLanding(host.level(), pos, side)
                    .filter(landing -> host.placementPrice().pay(VolumeCellBlock.MAX_LAYERS))
                    .ifPresent(landing -> cell.placeRunny(host, pos, side, landing));
            return;
        }
        VolumeCellBlock.stuck(host.stateAt(pos), cell, side)
                .filter(into -> host.placementPrice().pay(VolumeCellBlock.MAX_LAYERS))
                .ifPresent(into -> {
                    host.transformBlock(pos, into);
                    cell.creditPlacer(host.level(), pos, host.placingPlayer());
                });
    }

    /**
     * Lands a fill layer once the player pays for it and spreads the cell at once into its side cells.
     * drip-creates-a-volume-cell-immediately
     * spread-decreases-the-cell-it-leaves
     *
     * @param host the caster's host
     * @param pos  the cell whose fill takes the layer
     * @param cell the goo's volume cell block
     */
    private static void feedFill(ChannelHost host, BlockPos pos, VolumeCellBlock cell) {
        BlockState standing = host.stateAt(pos);
        VolumeCellBlock.thickened(standing, cell)
                .filter(into -> !VolumeCellBlock.wouldRiseAroundFeet(host.level(), pos,
                        VolumeCellBlock.layersOf(standing), into.getValue(VolumeCellBlock.LAYERS)))
                .filter(into -> host.placementPrice().pay(VolumeCellBlock.MAX_LAYERS))
                .ifPresent(into -> {
                    host.transformBlock(pos, into);
                    // standing-goo-thing-credits-whoever-placed-it: credited before the spread carries it on
                    cell.creditPlacer(host.level(), pos, host.placingPlayer());
                    cell.spreadAtOnce(host.level(), pos);
                });
    }
}
