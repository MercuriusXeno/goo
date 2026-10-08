package com.mercuriusxeno.goo.ability.bloom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import java.util.List;
import java.util.Optional;

/**
 * Reads the surface an empty cell offers Bloom's plants: water beneath it
 * takes a lily; a cell the sky does not reach is a cave, its floor, ceiling
 * or a wall taking cave flora; under the sky, plantable ground takes a
 * field plant and a wall a vine; a cell with nothing to sit on takes none.
 * bloom-places-buds-by-biome-and-surface
 */
public final class BloomSurface {

    private static final List<Direction> WALLS =
            List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private BloomSurface() {
    }

    /**
     * Where a plant sits: the flora the surface takes and the way out of
     * what holds it.
     *
     * @param flora  the flora the surface takes
     * @param facing the way out of what holds the plant
     */
    public record Spot(BloomFlora flora, Direction facing) {
    }

    /**
     * What the cell's surroundings offer a plant.
     */
    public interface Probe {

        /**
         * @return true when still water lies right beneath the cell
         */
        boolean waterBelow();

        /**
         * @param side the side of the cell
         * @return true when the block on that side offers the cell a sturdy face
         */
        boolean solid(Direction side);

        /**
         * @return true when the ground beneath the cell takes plants
         */
        boolean plantableBelow();

        /**
         * @return true when the sky reaches the cell
         */
        boolean skyVisible();
    }

    /**
     * Reads the spot a cell offers a plant.
     *
     * @param probe the cell's surroundings
     * @return the spot, or empty where nothing holds a plant
     */
    public static Optional<Spot> classify(Probe probe) {
        if (probe.waterBelow()) {
            return Optional.of(new Spot(BloomFlora.WATER, Direction.UP));
        }
        if (!probe.skyVisible()) {
            return caveSpot(probe);
        }
        if (probe.solid(Direction.DOWN) && probe.plantableBelow()) {
            return Optional.of(new Spot(BloomFlora.FIELD, Direction.UP));
        }
        return wallFacing(probe).map(facing -> new Spot(BloomFlora.WALL, facing));
    }

    private static Optional<Spot> caveSpot(Probe probe) {
        if (probe.solid(Direction.DOWN)) {
            return Optional.of(new Spot(BloomFlora.CAVE, Direction.UP));
        }
        if (probe.solid(Direction.UP)) {
            return Optional.of(new Spot(BloomFlora.CAVE, Direction.DOWN));
        }
        return wallFacing(probe).map(facing -> new Spot(BloomFlora.CAVE, facing));
    }

    private static Optional<Direction> wallFacing(Probe probe) {
        return WALLS.stream().filter(probe::solid).findFirst().map(Direction::getOpposite);
    }

    /**
     * The probe over a cell of a live level.
     *
     * @param level the level
     * @param cell  the cell
     * @return the probe
     */
    public static Probe of(Level level, BlockPos cell) {
        return new Probe() {
            @Override
            public boolean waterBelow() {
                FluidState fluid = level.getFluidState(cell.below());
                return fluid.is(Fluids.WATER) && fluid.isSource() && level.getBlockState(cell.below()).is(Blocks.WATER);
            }

            @Override
            public boolean solid(Direction side) {
                BlockPos neighbor = cell.relative(side);
                return level.getBlockState(neighbor).isFaceSturdy(level, neighbor, side.getOpposite());
            }

            @Override
            public boolean plantableBelow() {
                BlockState ground = level.getBlockState(cell.below());
                return ground.is(BlockTags.DIRT) || ground.is(BlockTags.SUPPORTS_VEGETATION);
            }

            @Override
            public boolean skyVisible() {
                return level.canSeeSky(cell);
            }
        };
    }
}
