package com.mercuriusxeno.goo.ability.typhoon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The flight a player holds under Float, typhoon's held effect, until the game time
 * it ends at: the speed it flies along its look and strafes at, the speed jump
 * and sneak climb and descend at, the share of the way its velocity turns
 * toward the input each tick, the slowed fall, how much higher it jumps, and
 * the speed an elytra glide is drawn toward. Saved with the player and synced
 * to its client, which flies it, since the client moves the player.
 * tailwind-and-airborn-merge-into-float
 *
 * @param airSpeed   the speed forward, backward and strafing drive toward, in blocks per tick
 * @param climbSpeed the speed jump climbs and sneak descends at, in blocks per tick
 * @param airSteer   the share of the way the velocity turns toward the input each tick, 0 to 1
 * @param fallCap    the fastest the player falls, in blocks per tick
 * @param jumpBoost  the share the player's jump strength grows by
 * @param glideSpeed the speed an elytra glide is drawn toward along the look, in blocks per tick
 * @param glideSteer the share of the way a slower glide is drawn toward it each tick, 0 to 1
 * @param expiresAt  the game time Float ends at; zero when none stands
 */
public record Airborn(float airSpeed, float climbSpeed, float airSteer, float fallCap, float jumpBoost,
                      float glideSpeed, float glideSteer, long expiresAt) {

    /** No Float. */
    public static final Airborn NONE = new Airborn(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0L);
    /** The end time of a Float held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_AIR_SPEED = "air_speed";
    private static final String FIELD_CLIMB_SPEED = "climb_speed";
    private static final String FIELD_AIR_STEER = "air_steer";
    private static final String FIELD_FALL_CAP = "fall_cap";
    private static final String FIELD_JUMP_BOOST = "jump_boost";
    private static final String FIELD_GLIDE_SPEED = "glide_speed";
    private static final String FIELD_GLIDE_STEER = "glide_steer";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves Float's flight with the player. */
    public static final MapCodec<Airborn> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_AIR_SPEED).forGetter(Airborn::airSpeed),
            Codec.FLOAT.optionalFieldOf(FIELD_CLIMB_SPEED, 0f).forGetter(Airborn::climbSpeed),
            Codec.FLOAT.fieldOf(FIELD_AIR_STEER).forGetter(Airborn::airSteer),
            Codec.FLOAT.fieldOf(FIELD_FALL_CAP).forGetter(Airborn::fallCap),
            Codec.FLOAT.fieldOf(FIELD_JUMP_BOOST).forGetter(Airborn::jumpBoost),
            Codec.FLOAT.optionalFieldOf(FIELD_GLIDE_SPEED, 0f).forGetter(Airborn::glideSpeed),
            Codec.FLOAT.optionalFieldOf(FIELD_GLIDE_STEER, 0f).forGetter(Airborn::glideSteer),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Airborn::expiresAt)
    ).apply(inst, Airborn::new));

    /** Syncs Float's flight to the owning client, which flies the player. */
    public static final StreamCodec<ByteBuf, Airborn> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Airborn::airSpeed,
            ByteBufCodecs.FLOAT, Airborn::climbSpeed,
            ByteBufCodecs.FLOAT, Airborn::airSteer,
            ByteBufCodecs.FLOAT, Airborn::fallCap,
            ByteBufCodecs.FLOAT, Airborn::jumpBoost,
            ByteBufCodecs.FLOAT, Airborn::glideSpeed,
            ByteBufCodecs.FLOAT, Airborn::glideSteer,
            ByteBufCodecs.VAR_LONG, Airborn::expiresAt,
            Airborn::new);

    /**
     * Whether Float stands at a game time.
     *
     * @param gameTime the game time
     * @return true before it ends
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }
}
