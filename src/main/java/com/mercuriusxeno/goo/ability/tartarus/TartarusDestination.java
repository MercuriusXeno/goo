package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Optional;

/**
 * Where the gate Tartarus summons leads a player and where its search for a
 * floor starts: from the overworld, the nether at the player's X and Z over
 * eight, at the height standing as far down the nether as theirs stands up
 * the overworld.
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 * From the nether, however the player came there, the overworld at their X
 * and Z times eight, at the height mirrored the same way back.
 * decision tartarus-in-the-nether-takes-you-to-the-overworld-at-eight-times-your-spot
 *
 * @param dimension the level the gate leads to
 * @param start     where the search for a floor starts
 */
public record TartarusDestination(ResourceKey<Level> dimension, BlockPos start) {

    /** How many overworld blocks one nether block spans across X and Z, as vanilla's nether scales them. */
    static final int NETHER_SCALE = 8;

    /**
     * The level the gate leads to from where a player stands.
     *
     * @param standingIn the level the player channels in
     * @return the level the gate leads to, empty where it leads nowhere
     */
    public static Optional<ResourceKey<Level>> targetOf(ResourceKey<Level> standingIn) {
        if (standingIn == Level.OVERWORLD) {
            return Optional.of(Level.NETHER);
        }
        return standingIn == Level.NETHER ? Optional.of(Level.OVERWORLD) : Optional.empty();
    }

    /**
     * The gate's destination for a player.
     *
     * @param standingIn the level the player channels in
     * @param feet       where the player stands
     * @param here       the height of the level they stand in
     * @param there      the height of the level the gate leads to
     * @return the destination, empty where the gate leads nowhere
     */
    public static Optional<TartarusDestination> from(ResourceKey<Level> standingIn, BlockPos feet, HeightSpan here,
                                                     HeightSpan there) {
        return targetOf(standingIn).map(target -> new TartarusDestination(target, new BlockPos(
                scaled(feet.getX(), target), here.mirroredInto(feet.getY(), there), scaled(feet.getZ(), target))));
    }

    /**
     * A coordinate across X or Z carried into a level: an eighth into the nether, eight times out of it.
     *
     * @param coordinate the coordinate where the player stands
     * @param target     the level the gate leads to
     * @return the coordinate there
     */
    private static int scaled(int coordinate, ResourceKey<Level> target) {
        return target == Level.NETHER ? Math.floorDiv(coordinate, NETHER_SCALE) : coordinate * NETHER_SCALE;
    }
}
