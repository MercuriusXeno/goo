package com.mercuriusxeno.goo.ability.gluttony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The gluttony standing on a player until it expires: it heals and feeds
 * nothing itself, and banks what healing and eating push past full bars as
 * overheal and overhunger, up to the caps its JSON names. Overhunger refills
 * hunger as it drops; overheal stands as extra hearts the effect owns, gone with it.
 * gluttony-overheals-and-overhungers
 *
 * @param expiresAt     the game time the gluttony ends at; zero when none stands
 * @param maxOverheal   the most overheal the gluttony banks, in health points
 * @param maxOverhunger the most overhunger the gluttony banks, in food points
 * @param overheal      the overheal standing, in health points
 * @param overhunger    the overhunger banked, in food points
 */
public record Gluttony(long expiresAt, int maxOverheal, int maxOverhunger, float overheal, int overhunger) {

    /** The gluttony a player without Gluttony holds. */
    public static final Gluttony NONE = new Gluttony(0L, 0, 0, 0f, 0);

    /**
     * The expiry of gluttony the glove holds, standing until the held effect ends.
     * self-effects-trickle-until-ended
     */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_MAX_OVERHEAL = "max_overheal";
    private static final String FIELD_MAX_OVERHUNGER = "max_overhunger";
    private static final String FIELD_OVERHEAL = "overheal";
    private static final String FIELD_OVERHUNGER = "overhunger";

    /** Codec for the saved gluttony. */
    public static final MapCodec<Gluttony> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Gluttony::expiresAt),
            Codec.INT.fieldOf(FIELD_MAX_OVERHEAL).forGetter(Gluttony::maxOverheal),
            Codec.INT.fieldOf(FIELD_MAX_OVERHUNGER).forGetter(Gluttony::maxOverhunger),
            Codec.FLOAT.fieldOf(FIELD_OVERHEAL).forGetter(Gluttony::overheal),
            Codec.INT.fieldOf(FIELD_OVERHUNGER).forGetter(Gluttony::overhunger)
    ).apply(inst, Gluttony::new));

    /**
     * Answers whether gluttony stands.
     *
     * @return true while Gluttony holds
     */
    public boolean stands() {
        return expiresAt > 0L;
    }

    /**
     * Applies Gluttony for a duration: standing gluttony stacks the duration
     * onto its expiry and keeps its banks; otherwise it starts with empty banks.
     *
     * @param caps     the bank caps the step names
     * @param duration the ticks the gluttony lasts
     * @param now      the game time
     * @return the gluttony after the invoke
     */
    public Gluttony apply(Gluttony caps, int duration, long now) {
        if (stands()) {
            return withExpiry(expiresAt == NEVER_EXPIRES ? NEVER_EXPIRES : expiresAt + duration);
        }
        return new Gluttony(now + duration, caps.maxOverheal, caps.maxOverhunger, 0f, 0);
    }

    /**
     * Starts gluttony the glove holds, with no expiry; standing gluttony keeps
     * its banks and holds on.
     * self-effects-trickle-until-ended
     *
     * @param caps the bank caps the step names
     * @param now  the game time
     * @return the gluttony after the start
     */
    public Gluttony hold(Gluttony caps, long now) {
        return stands() ? withExpiry(NEVER_EXPIRES)
                : new Gluttony(NEVER_EXPIRES, caps.maxOverheal, caps.maxOverhunger, 0f, 0);
    }

    /**
     * The step's caps as a gluttony standing nowhere, for {@link #apply} and {@link #hold}.
     *
     * @param maxOverheal   the overheal cap, in health points
     * @param maxOverhunger the overhunger cap, in food points
     * @return the caps
     */
    public static Gluttony caps(int maxOverheal, int maxOverhunger) {
        return new Gluttony(0L, maxOverheal, maxOverhunger, 0f, 0);
    }

    /**
     * Banks what a heal pushes past full health as overheal, up to the cap.
     *
     * @param health    the player's health before the heal
     * @param maxHealth the player's max health
     * @param amount    the health the heal adds
     * @return the gluttony after the heal, the same instance when nothing banks
     */
    public Gluttony bankHeal(float health, float maxHealth, float amount) {
        float excess = health + amount - maxHealth;
        if (!stands() || excess <= 0f || overheal >= maxOverheal) {
            return this;
        }
        return withBanks(Math.min(maxOverheal, overheal + excess), overhunger);
    }

    /**
     * Answers whether banked overhunger refills a shank now: hunger has dropped
     * below full and a point stands banked.
     *
     * @param hungerFull whether the player's hunger stands full
     * @return true when a banked point refills hunger
     */
    public boolean refills(boolean hungerFull) {
        return !hungerFull && overhunger > 0;
    }

    /**
     * The gluttony after a banked point refilled a shank.
     *
     * @return the gluttony with one point less banked
     */
    public Gluttony refilled() {
        return withBanks(overheal, overhunger - 1);
    }

    /**
     * The gluttony after damage spent the extra hearts down to what stands.
     *
     * @param absorption the extra hearts the player holds
     * @return the gluttony owning no more overheal than stands
     */
    public Gluttony spentTo(float absorption) {
        return absorption < overheal ? withBanks(Math.max(0f, absorption), overhunger) : this;
    }

    /**
     * Advances the gluttony one tick: it ends at its expiry.
     *
     * @param now the game time
     * @return the gluttony after the tick, the same instance when nothing changed
     */
    public Gluttony tick(long now) {
        return stands() && now >= expiresAt ? NONE : this;
    }

    private Gluttony withExpiry(long expiry) {
        return new Gluttony(expiry, maxOverheal, maxOverhunger, overheal, overhunger);
    }

    private Gluttony withBanks(float heal, int hunger) {
        return new Gluttony(expiresAt, maxOverheal, maxOverhunger, heal, hunger);
    }
}
