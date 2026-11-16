package com.mercuriusxeno.goo.ability.end;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Where the gate End summons takes the player channelling it: from any
 * level but the End, to the End's entrance, where a vanilla end portal sets
 * a player down, facing the way it faces them; in the End, back to where
 * the gate took them from, the way back they keep.
 * decision end-summons-a-gate-like-zone-that-takes-you-to-the-end-and-back
 * decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in
 *
 * @param dimension the level the gate takes them to
 * @param feet      where their feet arrive
 * @param yaw       the way they face arriving, in degrees
 */
public record EndDestination(ResourceKey<Level> dimension, Vec3 feet, float yaw) {

    /** Saves a destination with the player who keeps it as their way back. */
    public static final Codec<EndDestination> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(EndDestination::dimension),
            Vec3.CODEC.fieldOf("feet").forGetter(EndDestination::feet),
            Codec.FLOAT.fieldOf("yaw").forGetter(EndDestination::yaw)
    ).apply(inst, EndDestination::new));

    /** Sends a destination to the client of the player who keeps it. */
    public static final StreamCodec<ByteBuf, EndDestination> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), EndDestination::dimension,
            Vec3.STREAM_CODEC, EndDestination::feet,
            ByteBufCodecs.FLOAT, EndDestination::yaw,
            EndDestination::new);

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
     * @param wayBack    where the player portalled into the End from, empty for a player holding none
     * @return the destination, empty where the gate takes them nowhere
     */
    public static Optional<EndDestination> from(ResourceKey<Level> standingIn, Optional<EndDestination> wayBack) {
        if (standingIn == Level.END) {
            return wayBack;
        }
        return Optional.of(new EndDestination(Level.END, END_ENTRANCE, FACING_WEST));
    }

    /**
     * The way back a player carried to the End keeps: where they portalled in from.
     *
     * @param leaving the level they leave
     * @param feet    where their feet stood
     * @param yaw     the way they faced, in degrees
     * @return the way back
     */
    public static EndDestination wayBackFrom(ResourceKey<Level> leaving, Vec3 feet, float yaw) {
        return new EndDestination(leaving, feet, yaw);
    }
}
