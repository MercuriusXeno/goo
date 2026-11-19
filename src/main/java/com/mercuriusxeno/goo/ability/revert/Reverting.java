package com.mercuriusxeno.goo.ability.revert;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A mob Revert holds: frozen until a few ticks after the stream last
 * reached it, and, once its shrink into an egg has begun, the game time its
 * shrink ends and its egg drops.
 * rewind-fills-while-held
 * rewind-shrinks-adult-to-baby-to-egg
 *
 * @param heldUntil the last game time the mob stays frozen
 * @param vanishAt  the game time the mob becomes its egg, NOT_VANISHING while it is not shrinking away
 */
public record Reverting(long heldUntil, long vanishAt) {

    /** The vanish time of a mob that is not shrinking into its egg. */
    public static final long NOT_VANISHING = -1L;

    /** A mob the stream has not reached. */
    public static final Reverting NONE = new Reverting(0L, NOT_VANISHING);

    private static final String FIELD_HELD_UNTIL = "held_until";
    private static final String FIELD_VANISH_AT = "vanish_at";

    /** Codec for the saved hold, so a reload mid-hold still frees the mob. */
    public static final MapCodec<Reverting> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_HELD_UNTIL).forGetter(Reverting::heldUntil),
            Codec.LONG.fieldOf(FIELD_VANISH_AT).forGetter(Reverting::vanishAt)
    ).apply(inst, Reverting::new));

    /**
     * The hold stretched to a later end; a shrink already under way keeps its own end.
     *
     * @param until the last game time the mob stays frozen
     * @return the stretched hold
     */
    public Reverting heldTo(long until) {
        return new Reverting(Math.max(heldUntil, until), vanishAt);
    }

    /**
     * Answers whether the mob is shrinking into its egg.
     *
     * @return true once its shrink into an egg has begun
     */
    public boolean vanishing() {
        return vanishAt != NOT_VANISHING;
    }

    /**
     * Answers whether the hold has run out at a game time: the stream has
     * let go and no shrink keeps the mob.
     *
     * @param now the game time
     * @return true once the mob goes free
     */
    public boolean releasedAt(long now) {
        return !vanishing() && now > heldUntil;
    }

    /**
     * Answers whether the shrink into the egg has finished at a game time.
     *
     * @param now the game time
     * @return true once the egg drops
     */
    public boolean vanishedAt(long now) {
        return vanishing() && now >= vanishAt;
    }
}
