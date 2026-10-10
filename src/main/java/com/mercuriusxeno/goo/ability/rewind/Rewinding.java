package com.mercuriusxeno.goo.ability.rewind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A mob Rewind holds: frozen until a few ticks after the stream last
 * reached it, and, once its rewind into an egg has begun, the game time its
 * shrink ends and its egg drops.
 * rewind-fills-while-held
 * rewind-shrinks-adult-to-baby-to-egg
 *
 * @param heldUntil the last game time the mob stays frozen
 * @param vanishAt  the game time the mob becomes its egg, NOT_VANISHING while it is not shrinking away
 */
public record Rewinding(long heldUntil, long vanishAt) {

    /** The vanish time of a mob that is not shrinking into its egg. */
    public static final long NOT_VANISHING = -1L;

    /** A mob the stream has not reached. */
    public static final Rewinding NONE = new Rewinding(0L, NOT_VANISHING);

    private static final String FIELD_HELD_UNTIL = "held_until";
    private static final String FIELD_VANISH_AT = "vanish_at";

    /** Codec for the saved hold, so a reload mid-hold still frees the mob. */
    public static final MapCodec<Rewinding> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_HELD_UNTIL).forGetter(Rewinding::heldUntil),
            Codec.LONG.fieldOf(FIELD_VANISH_AT).forGetter(Rewinding::vanishAt)
    ).apply(inst, Rewinding::new));

    /**
     * The hold stretched to a later end; a shrink already under way keeps its own end.
     *
     * @param until the last game time the mob stays frozen
     * @return the stretched hold
     */
    public Rewinding heldTo(long until) {
        return new Rewinding(Math.max(heldUntil, until), vanishAt);
    }

    /**
     * Answers whether the mob is shrinking into its egg.
     *
     * @return true once its rewind into an egg has begun
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
