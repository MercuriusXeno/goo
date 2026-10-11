package com.mercuriusxeno.goo.ability.typhoon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The air control a player holds under Airborn until the game time it ends
 * at: the speed midair steering drives toward and the share of the way it
 * turns each tick, the fastest it falls, how much higher it jumps and how
 * much harder Jet pushes it, and the speed an elytra glide is drawn toward.
 * Saved with the player and synced to its client,
 * which steers and caps its fall, since the client moves the player.
 * airborn-steerable-levitation-and-soft-falls
 *
 * @param airSpeed  the horizontal speed midair input drives toward, in blocks per tick
 * @param airSteer  the share of the way the horizontal velocity turns toward it each tick, 0 to 1
 * @param fallCap   the fastest the player falls, in blocks per tick
 * @param jumpBoost the share the player's jump strength grows by
 * @param jetBoost  what Jet's push strength is multiplied by
 * @param glideSpeed the speed an elytra glide is drawn toward along the look, in blocks per tick
 * @param glideSteer the share of the way a slower glide is drawn toward it each tick, 0 to 1
 * @param expiresAt the game time Airborn ends at; zero when none stands
 */
public record Airborn(float airSpeed, float airSteer, float fallCap, float jumpBoost, float jetBoost,
                      float glideSpeed, float glideSteer, long expiresAt) {

    /** No Airborn. */
    public static final Airborn NONE = new Airborn(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0L);
    /** The end time of an Airborn held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_AIR_SPEED = "air_speed";
    private static final String FIELD_AIR_STEER = "air_steer";
    private static final String FIELD_FALL_CAP = "fall_cap";
    private static final String FIELD_JUMP_BOOST = "jump_boost";
    private static final String FIELD_JET_BOOST = "jet_boost";
    private static final String FIELD_GLIDE_SPEED = "glide_speed";
    private static final String FIELD_GLIDE_STEER = "glide_steer";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves Airborn with the player. */
    public static final MapCodec<Airborn> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_AIR_SPEED).forGetter(Airborn::airSpeed),
            Codec.FLOAT.fieldOf(FIELD_AIR_STEER).forGetter(Airborn::airSteer),
            Codec.FLOAT.fieldOf(FIELD_FALL_CAP).forGetter(Airborn::fallCap),
            Codec.FLOAT.fieldOf(FIELD_JUMP_BOOST).forGetter(Airborn::jumpBoost),
            Codec.FLOAT.fieldOf(FIELD_JET_BOOST).forGetter(Airborn::jetBoost),
            Codec.FLOAT.optionalFieldOf(FIELD_GLIDE_SPEED, 0f).forGetter(Airborn::glideSpeed),
            Codec.FLOAT.optionalFieldOf(FIELD_GLIDE_STEER, 0f).forGetter(Airborn::glideSteer),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Airborn::expiresAt)
    ).apply(inst, Airborn::new));

    /** Syncs Airborn to the owning client, which steers and caps the fall. */
    public static final StreamCodec<ByteBuf, Airborn> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Airborn::airSpeed,
            ByteBufCodecs.FLOAT, Airborn::airSteer,
            ByteBufCodecs.FLOAT, Airborn::fallCap,
            ByteBufCodecs.FLOAT, Airborn::jumpBoost,
            ByteBufCodecs.FLOAT, Airborn::jetBoost,
            ByteBufCodecs.FLOAT, Airborn::glideSpeed,
            ByteBufCodecs.FLOAT, Airborn::glideSteer,
            ByteBufCodecs.VAR_LONG, Airborn::expiresAt,
            Airborn::new);

    /**
     * Whether Airborn stands at a game time.
     *
     * @param gameTime the game time
     * @return true before it ends
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * The same air control standing until a game time.
     *
     * @param endsAt the game time it ends at
     * @return the Airborn ending then
     */
    public Airborn until(long endsAt) {
        return new Airborn(airSpeed, airSteer, fallCap, jumpBoost, jetBoost, glideSpeed, glideSteer, endsAt);
    }
}
