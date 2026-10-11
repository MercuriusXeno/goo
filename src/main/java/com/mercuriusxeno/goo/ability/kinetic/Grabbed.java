package com.mercuriusxeno.goo.ability.kinetic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * An entity Grab holds: suspended until a few ticks after the channel last
 * reached it, with the AI and gravity it had before the hold, which its
 * release hands back. Saved with the entity, so a reload mid-hold still frees it.
 * grab-holds-and-throws-a-physics-body
 *
 * @param heldUntil  the last game time the entity stays held
 * @param wasNoAi    whether the entity stood without AI before the hold
 * @param hadNoGravity whether the entity stood without gravity before the hold
 */
public record Grabbed(long heldUntil, boolean wasNoAi, boolean hadNoGravity) {

    /** An entity no grab holds. */
    public static final Grabbed NONE = new Grabbed(0L, false, false);

    private static final String FIELD_HELD_UNTIL = "held_until";
    private static final String FIELD_WAS_NO_AI = "was_no_ai";
    private static final String FIELD_HAD_NO_GRAVITY = "had_no_gravity";

    /** Codec for the saved hold. */
    public static final MapCodec<Grabbed> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_HELD_UNTIL).forGetter(Grabbed::heldUntil),
            Codec.BOOL.fieldOf(FIELD_WAS_NO_AI).forGetter(Grabbed::wasNoAi),
            Codec.BOOL.fieldOf(FIELD_HAD_NO_GRAVITY).forGetter(Grabbed::hadNoGravity)
    ).apply(inst, Grabbed::new));

    /**
     * The hold stretched to a later end.
     *
     * @param until the last game time the entity stays held
     * @return the stretched hold
     */
    public Grabbed heldTo(long until) {
        return new Grabbed(Math.max(heldUntil, until), wasNoAi, hadNoGravity);
    }

    /**
     * Answers whether the hold has run out at a game time: the channel has let go.
     *
     * @param now the game time
     * @return true once the entity goes free
     */
    public boolean releasedAt(long now) {
        return now > heldUntil;
    }
}
