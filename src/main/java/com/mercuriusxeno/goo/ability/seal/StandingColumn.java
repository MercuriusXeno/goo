package com.mercuriusxeno.goo.ability.seal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The three blocks a player would stand in and on at one spot: the floor
 * under their feet, the block their feet fill and the block their head fills.
 * Elude lands a player only where this column is safe.
 *
 * @param floor       the block under their feet
 * @param floorSturdy whether the floor's top face holds them
 * @param feet        the block their feet fill
 * @param head        the block their head fills
 * @param insideWorld whether the spot lies inside the build height and the world border, in a loaded chunk
 */
public record StandingColumn(BlockState floor, boolean floorSturdy, BlockState feet, BlockState head,
        boolean insideWorld) {

    /**
     * Samples the column a player's feet would fill at a spot, reading no block
     * of a chunk that is not loaded.
     *
     * @param level the level
     * @param feet  the block the player's feet would fill
     * @return the column there
     */
    public static StandingColumn at(Level level, BlockPos feet) {
        BlockPos floor = feet.below();
        BlockPos head = feet.above();
        boolean inside = level.isInsideBuildHeight(floor.getY()) && level.isInsideBuildHeight(head.getY())
                && level.getWorldBorder().isWithinBounds(feet) && level.isLoaded(feet);
        if (!inside) {
            BlockState outside = Blocks.VOID_AIR.defaultBlockState();
            return new StandingColumn(outside, false, outside, outside, false);
        }
        BlockState floorState = level.getBlockState(floor);
        return new StandingColumn(floorState, floorState.isFaceSturdy(level, floor, Direction.UP),
                level.getBlockState(feet), level.getBlockState(head), true);
    }

    /**
     * Whether a player can stand here: inside the world, on a sturdy floor
     * holding no lava or water, in two blocks of air, which fire and water
     * are not.
     * elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage
     *
     * @return true where the player stands safe
     */
    public boolean safe() {
        return insideWorld && dryFloor() && roomToStand();
    }

    private boolean dryFloor() {
        return floorSturdy && floor.getFluidState().isEmpty();
    }

    private boolean roomToStand() {
        return feet.isAir() && head.isAir();
    }
}
