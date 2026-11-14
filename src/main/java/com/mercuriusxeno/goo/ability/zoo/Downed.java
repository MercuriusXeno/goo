package com.mercuriusxeno.goo.ability.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * A rallied mob fallen downed instead of dying: the game time it went down
 * and whether its AI was off before the fall, so a raise puts the AI back as
 * it stood. For its first stretch down it shrugs off attacks and falls; past
 * its death span it dies. Saved with the mob and synced to every client
 * drawing it, which lays it on its side.
 * rallied-mob-falls-downed-instead-of-dying
 *
 * @param downedAt the game time the mob went down
 * @param wasNoAi  whether the mob's AI was off before it went down
 */
public record Downed(long downedAt, boolean wasNoAi) {

    /** No fall: the mob stands. */
    public static final Downed NONE = new Downed(0L, false);

    /** Ticks a downed mob shrugs off attacks and falls for, 5 seconds. */
    public static final int IMMUNE_TICKS = 100;
    /** Ticks after going down that a mob still downed dies, 15 seconds. */
    public static final int DEATH_TICKS = 300;

    private static final String FIELD_DOWNED_AT = "downed_at";
    private static final String FIELD_WAS_NO_AI = "was_no_ai";

    /** Saves the fall with the mob, so a reload keeps its timers. */
    public static final MapCodec<Downed> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_DOWNED_AT).forGetter(Downed::downedAt),
            Codec.BOOL.fieldOf(FIELD_WAS_NO_AI).forGetter(Downed::wasNoAi)
    ).apply(inst, Downed::new));

    /** Syncs the fall to the clients drawing the mob, which lay it on its side. */
    public static final StreamCodec<ByteBuf, Downed> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Downed::downedAt,
            ByteBufCodecs.BOOL, Downed::wasNoAi,
            Downed::new);

    /**
     * Ticks the mob has lain downed at a game time.
     *
     * @param gameTime the game time
     * @return the ticks since it went down
     */
    public long ticksDown(long gameTime) {
        return gameTime - downedAt;
    }

    /**
     * Whether the mob, still downed at a game time, has outlived its death span.
     *
     * @param gameTime the game time
     * @return true once the death span has passed
     */
    public boolean diesAt(long gameTime) {
        return ticksDown(gameTime) >= DEATH_TICKS;
    }

    /**
     * Whether a hit is an attack: damage some entity caused or carried, or an explosion.
     *
     * @param source the hit's source
     * @return true for an attack
     */
    public static boolean isAttack(DamageSource source) {
        return source.getEntity() != null || source.getDirectEntity() != null
                || source.is(DamageTypeTags.IS_EXPLOSION);
    }

    /**
     * Whether a lethal hit on a rallied mob downs it rather than killing it:
     * an attack does, and the world's harm, lava, fire, the void, drowning
     * and the like, kills outright.
     *
     * @param source the lethal hit's source
     * @return true where the mob goes downed
     */
    public static boolean downs(DamageSource source) {
        return isAttack(source);
    }

    /**
     * Whether a downed mob shrugs off a hit: an attack or a fall inside its
     * immunity, since the blow's knockback or a blast taking out the floor
     * often throws it; any hit after the immunity, and the world's other
     * harm throughout, lands.
     *
     * @param ticksDown the ticks the mob has lain downed
     * @param source    the hit's source
     * @return true where the hit is negated
     */
    public static boolean negates(long ticksDown, DamageSource source) {
        return ticksDown < IMMUNE_TICKS && (isAttack(source) || source.is(DamageTypeTags.IS_FALL));
    }
}
