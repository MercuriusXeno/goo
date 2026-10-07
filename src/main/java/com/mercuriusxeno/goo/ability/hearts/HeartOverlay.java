package com.mercuriusxeno.goo.ability.hearts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

/**
 * The heart overlay standing over a player's health bar: a shield layer over
 * the real heart slots, which drains before real health and exposes it when
 * gone (decision overlay-hearts-are-an-elemental-overshield). Each slot's
 * shield counts in half hearts, two to a full shield, so hits strip it and
 * regrowth restores it a half at a time. The kind names the shield: Kindle's
 * embers over hearts that read ash when bare (decision
 * kindle-ember-hearts-ash-and-retaliate), Barkskin's bark over normal hearts
 * (decision barkskin-bark-hearts-thorn-and-burn). Times are absolute game
 * times, so the overlay changes, and syncs, only when a heart does.
 *
 * @param kind        the overlay's kind
 * @param shields     per heart slot, from the left, the half hearts of shield over it
 * @param expiresAt   the game time the overlay ends at; zero when none stands
 * @param regrowAt    the game time the next half of shield regrows at
 * @param fireReadyAt the game time fire can next relight Kindle at
 * @param damageTaken the share of a physical hit a half of shield takes, Stoneskin's reduction
 *                    (decision stoneskin-stone-hearts-block-regeneration)
 * @param drainCarry  the health points Reserve drained that have not yet banked a reserve half
 */
public record HeartOverlay(HeartKind kind, List<Integer> shields, long expiresAt, long regrowAt,
                           long fireReadyAt, float damageTaken, float drainCarry) {

    /**
     * The overlay a player without a heart brew holds.
     */
    public static final HeartOverlay NONE = new HeartOverlay(HeartKind.KINDLE, List.of(), 0L, 0L, 0L);

    /** The share of a hit a shield takes when nothing reduces it. */
    public static final float WHOLE_HIT = 1f;
    /**
     * The expiry of an overlay that stands until it is spent.
     * reserve-hearts-sit-behind-the-bar
     */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;
    /** The slack a banked half forgives in the float sum of a drain's ticks. */
    private static final float BANK_TOLERANCE = 1e-4f;

    /** Half hearts in a full shield, and in a heart slot. */
    public static final int FULL_SHIELD = 2;
    /** Health points one heart slot holds; one point is half a heart. */
    static final float HEART_POINTS = 2.0f;
    /** Ticks after fire relights Kindle before fire can again. */
    static final long FIRE_RELIGHT_COOLDOWN = 10L * HeartKind.TICKS_PER_SECOND;
    /** An aggravated hit burns its own amount in shields and the same amount again. */
    static final int AGGRAVATED_BURN_FACTOR = 2;
    /** The slot answer when every real heart wears a full shield. */
    private static final int NO_SLOT = -1;

    private static final String FIELD_KIND = "kind";
    private static final String FIELD_SHIELDS = "shields";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_REGROW_AT = "regrow_at";
    private static final String FIELD_FIRE_READY_AT = "fire_ready_at";
    private static final String FIELD_DAMAGE_TAKEN = "damage_taken";
    private static final String FIELD_DRAIN_CARRY = "drain_carry";

    /**
     * Codec for the saved overlay.
     */
    public static final MapCodec<HeartOverlay> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            HeartKind.CODEC.fieldOf(FIELD_KIND).forGetter(HeartOverlay::kind),
            Codec.INT.listOf().fieldOf(FIELD_SHIELDS).forGetter(HeartOverlay::shields),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(HeartOverlay::expiresAt),
            Codec.LONG.fieldOf(FIELD_REGROW_AT).forGetter(HeartOverlay::regrowAt),
            Codec.LONG.fieldOf(FIELD_FIRE_READY_AT).forGetter(HeartOverlay::fireReadyAt),
            Codec.FLOAT.optionalFieldOf(FIELD_DAMAGE_TAKEN, WHOLE_HIT).forGetter(HeartOverlay::damageTaken),
            Codec.FLOAT.optionalFieldOf(FIELD_DRAIN_CARRY, 0f).forGetter(HeartOverlay::drainCarry)
    ).apply(inst, HeartOverlay::new));

    /**
     * Codec for the overlay synced to the owning client.
     */
    public static final StreamCodec<ByteBuf, HeartOverlay> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(ordinal -> HeartKind.values()[ordinal], HeartKind::ordinal), HeartOverlay::kind,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), HeartOverlay::shields,
            ByteBufCodecs.VAR_LONG, HeartOverlay::expiresAt,
            ByteBufCodecs.VAR_LONG, HeartOverlay::regrowAt,
            ByteBufCodecs.VAR_LONG, HeartOverlay::fireReadyAt,
            ByteBufCodecs.FLOAT, HeartOverlay::damageTaken,
            ByteBufCodecs.FLOAT, HeartOverlay::drainCarry,
            HeartOverlay::new);

    /**
     * Copies the shield list so the record holds it unmodifiable.
     */
    public HeartOverlay {
        shields = List.copyOf(shields);
    }

    /**
     * An overlay whose shields take every hit whole, carrying no drain toward a reserve half.
     *
     * @param kind        the overlay's kind
     * @param shields     per heart slot, from the left, the half hearts of shield over it
     * @param expiresAt   the game time the overlay ends at; zero when none stands
     * @param regrowAt    the game time the next half of shield regrows at
     * @param fireReadyAt the game time fire can next relight Kindle at
     */
    public HeartOverlay(HeartKind kind, List<Integer> shields, long expiresAt, long regrowAt, long fireReadyAt) {
        this(kind, shields, expiresAt, regrowAt, fireReadyAt, WHOLE_HIT, 0f);
    }

    /**
     * What a hit leaves: the overlay after it and the damage that reaches real health.
     *
     * @param overlay   the overlay after the hit
     * @param remainder the damage real health takes
     */
    public record Drained(HeartOverlay overlay, float remainder) {
    }

    /**
     * Answers whether an overlay stands.
     *
     * @return true while a heart brew holds the bar
     */
    public boolean stands() {
        return expiresAt > 0L;
    }

    /**
     * Counts the half hearts of shield standing.
     *
     * @return the shield halves
     */
    public int shieldHalves() {
        return sum(shields);
    }

    /**
     * The shield standing, in hearts, which retaliation and thorns count by.
     *
     * @return the shield hearts, a half counting a half
     */
    public float shieldHearts() {
        return shieldHalves() / (float) FULL_SHIELD;
    }

    /**
     * The half hearts of shield over a heart slot.
     *
     * @param slot the heart slot, from the left
     * @return zero to two halves
     */
    public int shieldAt(int slot) {
        return slot < shields.size() ? shields.get(slot) : 0;
    }

    /**
     * The heart slots real health fills, a half heart counting as a slot.
     *
     * @param health the real health
     * @return the filled slot count
     */
    public static int filledSlots(float health) {
        return (int) Math.ceil(health / HEART_POINTS);
    }

    /**
     * Applies a heart brew. The same kind standing again adds the duration and
     * keeps its hearts; another kind ends the standing overlay and lays its own
     * whole, a full shield over every present heart, a missing heart staying
     * missing.
     *
     * @param brewKind the kind the brew lays
     * @param duration the brew's duration in ticks
     * @param health   the player's real health
     * @param now      the game time
     * @return the overlay after the brew
     */
    public HeartOverlay apply(HeartKind brewKind, int duration, float health, long now) {
        return apply(brewKind, duration, health, health, WHOLE_HIT, now);
    }

    /**
     * Applies a heart brew. The same kind standing again adds the duration and
     * keeps its hearts; another kind ends the standing overlay and lays its own
     * whole: a full shield over every present heart, or, for a kind filling
     * the missing hearts, over every heart slot the player is missing.
     *
     * @param brewKind    the kind the brew lays
     * @param duration    the brew's duration in ticks
     * @param health      the player's real health
     * @param maxHealth   the player's maximum health
     * @param damageTaken the share of a physical hit a half of shield takes
     * @param now         the game time
     * @return the overlay after the brew
     */
    public HeartOverlay apply(HeartKind brewKind, int duration, float health, float maxHealth, float damageTaken,
                              long now) {
        if (stands() && kind == brewKind) {
            // kindle-ember-hearts-ash-and-retaliate: the self ability stacks in duration
            return new HeartOverlay(kind, shields, expiresAt + duration, regrowAt, fireReadyAt, damageTaken, drainCarry);
        }
        // one-heart-overlay-at-a-time: a heart brew ends any other heart brew the moment it takes effect
        List<Integer> laid = brewKind.fillsMissing() ? missingFilled(health, maxHealth)
                : Collections.nCopies(filledSlots(health), FULL_SHIELD);
        return new HeartOverlay(brewKind, laid, now + duration, now + brewKind.regrowInterval(sum(laid)), now,
                damageTaken, 0f);
    }

    /**
     * Shields over every heart slot the player is missing and none over the
     * slots real health fills (decision stoneskin-stone-hearts-block-regeneration).
     *
     * @param health    the player's real health
     * @param maxHealth the player's maximum health
     * @return per heart slot, the half hearts of shield over it
     */
    private static List<Integer> missingFilled(float health, float maxHealth) {
        List<Integer> laid = new ArrayList<>(Collections.nCopies(filledSlots(health), 0));
        while (laid.size() < filledSlots(maxHealth)) {
            laid.add(FULL_SHIELD);
        }
        return laid;
    }

    /**
     * Answers whether this is a standing Reserve.
     *
     * @return true while Reserve holds the bar
     */
    public boolean reserves() {
        return stands() && kind == HeartKind.RESERVE;
    }

    /**
     * Banks health drained into the reserve behind the bar: the drained points
     * add to the carry, and each time the carry covers a half at the ratio a
     * reserve half is laid in the leftmost slot short of a full heart.
     * Banking stops at the cap, dropping what the carry still held. Any other
     * kind standing ends here, and the reserve never expires.
     * reserve-hearts-sit-behind-the-bar
     *
     * @param drained   the health points drained this tick
     * @param ratio     the reserve halves one drained point banks
     * @param capHalves the most reserve halves that may stand
     * @return the reserve after the banking
     */
    public HeartOverlay bank(float drained, float ratio, int capHalves) {
        HeartOverlay reserve = reserves() ? this
                : new HeartOverlay(HeartKind.RESERVE, List.of(), NEVER_EXPIRES, 0L, 0L, WHOLE_HIT, 0f);
        List<Integer> after = new ArrayList<>(reserve.shields);
        float carry = reserve.drainCarry + drained;
        float pointsPerHalf = 1f / ratio;
        int halves = sum(after);
        while (halves < capHalves && carry + BANK_TOLERANCE >= pointsPerHalf) {
            addHalf(after);
            halves++;
            carry -= pointsPerHalf;
        }
        float kept = halves >= capHalves ? 0f : Math.max(0f, carry);
        return new HeartOverlay(HeartKind.RESERVE, after, NEVER_EXPIRES, 0L, 0L, WHOLE_HIT, kept);
    }

    private static void addHalf(List<Integer> halves) {
        for (int slot = 0; slot < halves.size(); slot++) {
            if (halves.get(slot) < FULL_SHIELD) {
                halves.set(slot, halves.get(slot) + 1);
                return;
            }
        }
        halves.add(1);
    }

    /**
     * Runs a hit through the overlay: each half of shield absorbs up to half a
     * heart and strips, rightmost first, and what passes every shield reaches
     * real health at the kind's bare cost.
     *
     * @param damage the hit's damage
     * @param now    the game time
     * @return the overlay after the hit and the damage real health takes
     */
    public Drained drain(float damage, long now) {
        return drainScaled(damage, WHOLE_HIT, now);
    }

    /**
     * Runs a hit through the overlay with the shields taking a share of it:
     * the shields see the hit times the share, and what passes every shield
     * reaches real health back at its own scale, at the kind's bare cost. A
     * share under one is a shield shrugging the hit off; over one, a shield
     * worth less than its half (decision stoneskin-stone-hearts-block-regeneration).
     *
     * @param damage the hit's damage
     * @param share  the share of the hit the shields take
     * @param now    the game time
     * @return the overlay after the hit and the damage real health takes
     */
    public Drained drainScaled(float damage, float share, long now) {
        if (!stands()) {
            return new Drained(this, damage);
        }
        List<Integer> after = new ArrayList<>(shields);
        float remaining = damage * share;
        int slot = rightmostShielded(after);
        while (remaining > 0f && slot != NO_SLOT) {
            after.set(slot, after.get(slot) - 1);
            remaining = Math.max(0f, remaining - 1f);
            slot = rightmostShielded(after);
        }
        // overlay-hearts-are-an-elemental-overshield: the bar beneath takes only what passes the overlay
        return new Drained(settle(after, now), remaining / share * kind.bareCostMultiplier());
    }

    /**
     * Runs a hit the kind is especially weak to: the shields absorb none of it,
     * real health takes its whole amount, and it burns its own amount in
     * shield halves and the same amount again, rightmost first.
     *
     * @param damage the hit's damage
     * @param now    the game time
     * @return the overlay after the hit and the damage real health takes
     */
    public Drained aggravate(float damage, long now) {
        if (!stands()) {
            return new Drained(this, damage);
        }
        // aggravated-damage-is-a-per-heart-rule: burn the hit again in shields, leaving space to recover
        int burned = AGGRAVATED_BURN_FACTOR * (int) Math.ceil(damage);
        List<Integer> after = new ArrayList<>(shields);
        int slot = rightmostShielded(after);
        for (int burnt = 0; burnt < burned && slot != NO_SLOT; burnt++) {
            after.set(slot, after.get(slot) - 1);
            slot = rightmostShielded(after);
        }
        return new Drained(settle(after, now), damage);
    }

    /**
     * Advances the overlay one tick: it ends at its expiry, water or ice
     * strips a quenchable kind's shields, and otherwise the leftmost real heart
     * short of a full shield regrows a half when its interval has passed.
     *
     * @param health the player's real health
     * @param wet    whether the player touches water or ice
     * @param now    the game time
     * @return the overlay after the tick, the same instance when nothing changed
     */
    public HeartOverlay tick(float health, boolean wet, long now) {
        if (!stands()) {
            return this;
        }
        if (now >= expiresAt) {
            return NONE;
        }
        if (wet && kind.quenchedByWater()) {
            return quench(now);
        }
        return regrow(health, now);
    }

    /**
     * Answers whether the overlay holds real health from regenerating: stone
     * standing over the missing hearts
     * (decision stoneskin-stone-hearts-block-regeneration).
     *
     * @return true while a Stoneskin shield stands
     */
    public boolean blocksHealing() {
        return stands() && kind == HeartKind.STONESKIN && shieldHalves() > 0;
    }

    /**
     * Answers whether every real heart wears a full shield, which Kindle's fire cannot hurt.
     *
     * @param health the player's real health
     * @return true when no real heart is short of a full shield
     */
    public boolean allShielded(float health) {
        return leftmostShortSlot(filledSlots(health)) == NO_SLOT;
    }

    /**
     * Runs a fire hit through Kindle. With ash standing and fire's cooldown
     * passed, the fire relights the bar at the price of one heart: real health
     * loses a heart in place of the hit and every heart left relights whole.
     * Inside the cooldown the fire is an ordinary hit.
     *
     * @param damage the fire hit's damage
     * @param health the player's real health
     * @param now    the game time
     * @return the overlay after the fire and the damage real health takes
     */
    public Drained burn(float damage, float health, long now) {
        if (!stands() || now < fireReadyAt || allShielded(health)) {
            return drain(damage, now);
        }
        // kindle-ember-hearts-ash-and-retaliate: relighting costs an ash heart, so lava never makes the player invincible
        List<Integer> relit = Collections.nCopies(Math.max(0, filledSlots(health - HEART_POINTS)), FULL_SHIELD);
        HeartOverlay after = new HeartOverlay(kind, relit, expiresAt, now + kind.regrowInterval(sum(relit)),
                now + FIRE_RELIGHT_COOLDOWN, damageTaken, drainCarry);
        return new Drained(after, HEART_POINTS);
    }

    /**
     * Lights the hearts a heal brings back while the player burns, so health
     * regained in fire returns as ember rather than ash.
     *
     * @param health       the real health before the heal
     * @param healedHealth the real health after it
     * @return the overlay with every regained heart lit whole, the same instance when no heart came back
     */
    public HeartOverlay healInFire(float health, float healedHealth) {
        int from = filledSlots(health);
        int to = filledSlots(healedHealth);
        if (!stands() || to <= from) {
            return this;
        }
        List<Integer> after = padded(to);
        for (int slot = from; slot < to; slot++) {
            after.set(slot, FULL_SHIELD);
        }
        return withShields(after, regrowAt);
    }

    /**
     * The heart slot that regrows its next half of shield, the leftmost real
     * heart short of a full shield.
     *
     * @param health the player's real health
     * @return the slot, or empty when every real heart wears a full shield or no overlay stands
     */
    public OptionalInt nextRegrowSlot(float health) {
        int slot = leftmostShortSlot(filledSlots(health));
        return !stands() || !kind.regrows() || slot == NO_SLOT ? OptionalInt.empty() : OptionalInt.of(slot);
    }

    /**
     * The ticks the half regrowing now takes in all, which the regrow time was set from.
     *
     * @return the interval in ticks
     */
    public long regrowInterval() {
        return kind.regrowInterval(shieldHalves());
    }

    private HeartOverlay regrow(float health, long now) {
        if (!kind.regrows()) {
            // stoneskin-stone-hearts-block-regeneration: stone that breaks stays broken
            return this;
        }
        int shortSlot = leftmostShortSlot(filledSlots(health));
        // reserve-hearts-sit-behind-the-bar: a banked reserve only spends
        if (!kind.regrows() || now < regrowAt || shortSlot == NO_SLOT) {
            return this;
        }
        List<Integer> after = padded(shortSlot + 1);
        after.set(shortSlot, after.get(shortSlot) + 1);
        return withShields(after, now + kind.regrowInterval(sum(after)));
    }

    private HeartOverlay quench(long now) {
        if (shieldHalves() == 0) {
            return this;
        }
        return withShields(Collections.nCopies(shields.size(), 0), now + kind.regrowInterval(0));
    }

    /**
     * The overlay after shields stripped: unchanged when none did, gone when the
     * kind ends with its last shield, and otherwise restarting the regrow clock.
     */
    private HeartOverlay settle(List<Integer> after, long now) {
        if (after.equals(shields)) {
            return this;
        }
        int standing = sum(after);
        if (standing == 0 && kind.endsWhenBare()) {
            // barkskin-bark-hearts-thorn-and-burn: the effect lasts while a bark heart stands
            return NONE;
        }
        return withShields(after, now + kind.regrowInterval(standing));
    }

    private List<Integer> padded(int size) {
        List<Integer> after = new ArrayList<>(shields);
        while (after.size() < size) {
            after.add(0);
        }
        return after;
    }

    private HeartOverlay withShields(List<Integer> after, long nextRegrowAt) {
        return new HeartOverlay(kind, after, expiresAt, nextRegrowAt, fireReadyAt, damageTaken, drainCarry);
    }

    private int leftmostShortSlot(int filledSlots) {
        for (int slot = 0; slot < filledSlots; slot++) {
            if (shieldAt(slot) < FULL_SHIELD) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    private static int rightmostShielded(List<Integer> halves) {
        for (int slot = halves.size() - 1; slot >= 0; slot--) {
            if (halves.get(slot) > 0) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    private static int sum(List<Integer> halves) {
        return halves.stream().mapToInt(Integer::intValue).sum();
    }
}
