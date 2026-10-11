package com.mercuriusxeno.goo.ability.gluttony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The gluttony standing on a player: a point every interval until it
 * expires, each point filling a shank of hunger and a half heart of health,
 * and past a full bar banking overhunger and overheal up to the caps its
 * JSON names. Overhunger refills hunger as it drops; overheal stands as
 * extra hearts the effect owns, gone with it.
 * gluttony-overheals-and-overhungers
 *
 * @param interval      the ticks between points
 * @param nextAt        the game time the next point lands at
 * @param expiresAt     the game time the gluttony ends at; zero when none stands
 * @param maxOverheal   the most overheal the gluttony banks, in health points
 * @param maxOverhunger the most overhunger the gluttony banks, in food points
 * @param overheal      the overheal standing, in health points
 * @param overhunger    the overhunger banked, in food points
 */
public record Gluttony(int interval, long nextAt, long expiresAt, int maxOverheal, int maxOverhunger,
                       float overheal, int overhunger) {

    /** The gluttony a player without Gluttony holds. */
    public static final Gluttony NONE = new Gluttony(1, 0L, 0L, 0, 0, 0f, 0);

    /**
     * The expiry of gluttony the glove holds, standing until the held effect ends.
     * self-effects-trickle-until-ended
     */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    /** The health one point adds, to the bar or to the overheal. */
    public static final float HEALTH_PER_POINT = 1f;

    private static final String FIELD_INTERVAL = "interval";
    private static final String FIELD_NEXT_AT = "next_at";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_MAX_OVERHEAL = "max_overheal";
    private static final String FIELD_MAX_OVERHUNGER = "max_overhunger";
    private static final String FIELD_OVERHEAL = "overheal";
    private static final String FIELD_OVERHUNGER = "overhunger";

    /** Codec for the saved gluttony. */
    public static final MapCodec<Gluttony> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_INTERVAL).forGetter(Gluttony::interval),
            Codec.LONG.fieldOf(FIELD_NEXT_AT).forGetter(Gluttony::nextAt),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Gluttony::expiresAt),
            Codec.INT.fieldOf(FIELD_MAX_OVERHEAL).forGetter(Gluttony::maxOverheal),
            Codec.INT.fieldOf(FIELD_MAX_OVERHUNGER).forGetter(Gluttony::maxOverhunger),
            Codec.FLOAT.fieldOf(FIELD_OVERHEAL).forGetter(Gluttony::overheal),
            Codec.INT.fieldOf(FIELD_OVERHUNGER).forGetter(Gluttony::overhunger)
    ).apply(inst, Gluttony::new));

    /**
     * What one point does to the player's bars.
     *
     * @param eats  whether the point fills a shank of hunger
     * @param heals whether the point heals a half heart
     * @param after the gluttony after the point, its banks grown where a bar stood full
     */
    public record Landing(boolean eats, boolean heals, Gluttony after) {
    }

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
     * onto its expiry and keeps its clock and banks; otherwise the first point
     * lands an interval from now.
     *
     * @param caps     the interval and the bank caps the step names
     * @param duration the ticks the gluttony lasts
     * @param now      the game time
     * @return the gluttony after the invoke
     */
    public Gluttony apply(Gluttony caps, int duration, long now) {
        if (stands()) {
            return withExpiry(expiresAt == NEVER_EXPIRES ? NEVER_EXPIRES : expiresAt + duration);
        }
        return new Gluttony(caps.interval, now + caps.interval, now + duration, caps.maxOverheal,
                caps.maxOverhunger, 0f, 0);
    }

    /**
     * Starts gluttony the glove holds: no expiry, the first point an interval
     * from now; standing gluttony keeps its clock and banks and holds on.
     * self-effects-trickle-until-ended
     *
     * @param caps the interval and the bank caps the step names
     * @param now  the game time
     * @return the gluttony after the start
     */
    public Gluttony hold(Gluttony caps, long now) {
        return stands() ? withExpiry(NEVER_EXPIRES)
                : new Gluttony(caps.interval, now + caps.interval, NEVER_EXPIRES, caps.maxOverheal,
                        caps.maxOverhunger, 0f, 0);
    }

    /**
     * The step's parameters as a gluttony standing nowhere, for {@link #apply} and {@link #hold}.
     *
     * @param pointInterval the ticks between points
     * @param maxOverheal   the overheal cap, in health points
     * @param maxOverhunger the overhunger cap, in food points
     * @return the caps
     */
    public static Gluttony caps(int pointInterval, int maxOverheal, int maxOverhunger) {
        return new Gluttony(pointInterval, 0L, 0L, maxOverheal, maxOverhunger, 0f, 0);
    }

    /**
     * Answers whether a point lands this tick: one stands, its time has come
     * and it falls inside the gluttony.
     *
     * @param now the game time
     * @return true when a point lands now
     */
    public boolean feeds(long now) {
        return stands() && now >= nextAt && nextAt <= expiresAt;
    }

    /**
     * Lands a point: a bar short of full fills, and a full bar banks the
     * point past its cap, up to the cap the JSON names.
     *
     * @param hungerFull whether the player's hunger stands full
     * @param healthFull whether the player's health stands full
     * @return what the point does
     */
    public Landing land(boolean hungerFull, boolean healthFull) {
        int bankedHunger = hungerFull ? Math.min(maxOverhunger, overhunger + 1) : overhunger;
        float bankedHeal = healthFull ? Math.min(maxOverheal, overheal + HEALTH_PER_POINT) : overheal;
        return new Landing(!hungerFull, !healthFull, withBanks(bankedHeal, bankedHunger));
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
     * Advances the gluttony one tick: it ends at its expiry, and a point
     * landing schedules the next an interval on.
     *
     * @param now the game time
     * @return the gluttony after the tick, the same instance when nothing changed
     */
    public Gluttony tick(long now) {
        if (!stands()) {
            return this;
        }
        if (feeds(now)) {
            return new Gluttony(interval, nextAt + interval, expiresAt, maxOverheal, maxOverhunger, overheal,
                    overhunger);
        }
        return now >= expiresAt ? NONE : this;
    }

    private Gluttony withExpiry(long expiry) {
        return new Gluttony(interval, nextAt, expiry, maxOverheal, maxOverhunger, overheal, overhunger);
    }

    private Gluttony withBanks(float heal, int hunger) {
        return new Gluttony(interval, nextAt, expiresAt, maxOverheal, maxOverhunger, heal, hunger);
    }
}
