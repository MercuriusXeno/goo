package com.mercuriusxeno.goo.ability.end;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Where the gate End summons takes the player channelling it: from any
 * level but the End, to the End's entrance, where a vanilla end portal sets
 * a player down, facing the way it faces them.
 * decision end-summons-a-gate-like-zone-that-takes-you-to-the-end-and-back
 *
 * @param dimension the level the gate takes them to
 * @param feet      where their feet arrive
 * @param yaw       the way they face arriving, in degrees
 */
public record EndDestination(ResourceKey<Level> dimension, Vec3 feet, float yaw) {

    /** The block a vanilla end portal centres its arrival on, ServerLevel's END_SPAWN_POINT. */
    static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    /** A vanilla end portal sets a player a block below the spawn point's bottom centre, on the platform. */
    public static final Vec3 END_ENTRANCE = Vec3.atBottomCenterOf(END_SPAWN_POINT).subtract(0, 1, 0);
    /** The way a vanilla end portal turns its arriver: west. */
    static final float FACING_WEST = 90f;

    /**
     * The gate's destination for a player standing in a level.
     *
     * @param standingIn the level the player channels in
     * @return the destination, empty where the gate takes them nowhere
     */
    public static Optional<EndDestination> from(ResourceKey<Level> standingIn) {
        if (standingIn == Level.END) {
            return Optional.empty();
        }
        return Optional.of(new EndDestination(Level.END, END_ENTRANCE, FACING_WEST));
    }
}
