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

/**
 * The heart overlay standing over a player's health bar: a shield layer over
 * the real heart slots, which drains before real health and exposes it when
 * gone (decision overlay-hearts-are-an-elemental-overshield). Kindle's layer
 * is one ember per shielded slot; a real heart with no ember over it reads
 * as ash (decision kindle-ember-hearts-ash-and-retaliate). Times are absolute
 * game times, so the overlay changes, and syncs, only when a heart does.
 *
 * @param kind       the overlay's kind
 * @param embers     per heart slot, from the left, whether an ember shields it
 * @param expiresAt  the game time the overlay ends at; zero when none stands
 * @param reigniteAt    the game time the next ash heart reignites at
 * @param igniteReadyAt the game time fire can next reignite the overlay at
 */
public record HeartOverlay(HeartKind kind, List<Boolean> embers, long expiresAt, long reigniteAt,
                           long igniteReadyAt) {

    /**
     * The overlay a player without a heart brew holds.
     */
    public static final HeartOverlay NONE = new HeartOverlay(HeartKind.KINDLE, List.of(), 0L, 0L, 0L);

    /** Health points one ember absorbs before it breaks: worth one heart. */
    static final float EMBER_WORTH = 2.0f;
    /** Real health is ash while Kindle stands, worth half a heart: a hit on it costs double. */
    static final float ASH_COST_MULTIPLIER = 2.0f;
    /** Health points one heart slot holds. */
    static final float HEART_POINTS = 2.0f;
    static final int TICKS_PER_SECOND = 20;
    /** Seconds an ash heart takes to reignite with no ember standing. */
    static final int REIGNITE_BASE_SECONDS = 2;
    /** Each ember standing slows the next reignite by half a second. */
    static final int EMBERS_PER_EXTRA_SECOND = 2;
    /** Ticks after a fire hit reignites the overlay before fire can again. */
    static final long FIRE_REIGNITE_COOLDOWN = 10L * TICKS_PER_SECOND;
    /** The slot answer when no real heart is bare ash. */
    private static final int NO_SLOT = -1;

    private static final String FIELD_KIND = "kind";
    private static final String FIELD_EMBERS = "embers";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_REIGNITE_AT = "reignite_at";
    private static final String FIELD_IGNITE_READY_AT = "ignite_ready_at";

    /**
     * Codec for the saved overlay.
     */
    public static final MapCodec<HeartOverlay> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            HeartKind.CODEC.fieldOf(FIELD_KIND).forGetter(HeartOverlay::kind),
            Codec.BOOL.listOf().fieldOf(FIELD_EMBERS).forGetter(HeartOverlay::embers),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(HeartOverlay::expiresAt),
            Codec.LONG.fieldOf(FIELD_REIGNITE_AT).forGetter(HeartOverlay::reigniteAt),
            Codec.LONG.fieldOf(FIELD_IGNITE_READY_AT).forGetter(HeartOverlay::igniteReadyAt)
    ).apply(inst, HeartOverlay::new));

    /**
     * Codec for the overlay synced to the owning client.
     */
    public static final StreamCodec<ByteBuf, HeartOverlay> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(ordinal -> HeartKind.values()[ordinal], HeartKind::ordinal), HeartOverlay::kind,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()), HeartOverlay::embers,
            ByteBufCodecs.VAR_LONG, HeartOverlay::expiresAt,
            ByteBufCodecs.VAR_LONG, HeartOverlay::reigniteAt,
            ByteBufCodecs.VAR_LONG, HeartOverlay::igniteReadyAt,
            HeartOverlay::new);

    /**
     * Copies the ember list so the record holds it unmodifiable.
     */
    public HeartOverlay {
        embers = List.copyOf(embers);
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
     * Counts the ember hearts standing.
     *
     * @return the ember count
     */
    public int emberCount() {
        return (int) embers.stream().filter(Boolean::booleanValue).count();
    }

    /**
     * Answers whether an ember shields a heart slot.
     *
     * @param slot the heart slot, from the left
     * @return true when an ember stands over it
     */
    public boolean emberAt(int slot) {
        return slot < embers.size() && embers.get(slot);
    }

    /**
     * The ticks an ash heart takes to reignite with a number of embers standing:
     * each ember standing adds half a second to the base two.
     *
     * @param emberCount the embers standing
     * @return the interval in ticks
     */
    static long reigniteInterval(int emberCount) {
        // kindle-ember-hearts-ash-and-retaliate: n / 2 + 2 seconds, so a high bar regrows slower
        return (long) REIGNITE_BASE_SECONDS * TICKS_PER_SECOND + (long) emberCount * TICKS_PER_SECOND / EMBERS_PER_EXTRA_SECOND;
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
     * keeps its hearts; otherwise every present heart takes an ember and a
     * missing heart stays missing.
     *
     * @param brewKind the kind the brew lays
     * @param duration the brew's duration in ticks
     * @param health   the player's real health
     * @param now      the game time
     * @return the overlay after the brew
     */
    public HeartOverlay apply(HeartKind brewKind, int duration, float health, long now) {
        if (stands() && kind == brewKind) {
            // kindle-ember-hearts-ash-and-retaliate: the self ability stacks in duration
            return new HeartOverlay(kind, embers, expiresAt + duration, reigniteAt, igniteReadyAt);
        }
        int slots = filledSlots(health);
        return new HeartOverlay(brewKind, Collections.nCopies(slots, Boolean.TRUE), now + duration,
                now + reigniteInterval(slots), now);
    }

    /**
     * Runs a hit through the overlay: each ember the hit lands on absorbs up to
     * its worth and breaks, rightmost first, and what passes every ember
     * reaches real health at double cost.
     *
     * @param damage the hit's damage
     * @param now    the game time
     * @return the overlay after the hit and the damage real health takes
     */
    public Drained drain(float damage, long now) {
        if (!stands()) {
            return new Drained(this, damage);
        }
        List<Boolean> after = new ArrayList<>(embers);
        float remaining = damage;
        int slot = after.lastIndexOf(Boolean.TRUE);
        while (remaining > 0f && slot >= 0) {
            after.set(slot, Boolean.FALSE);
            remaining = Math.max(0f, remaining - EMBER_WORTH);
            slot = after.lastIndexOf(Boolean.TRUE);
        }
        // overlay-hearts-are-an-elemental-overshield: the bar beneath takes only what passes the overlay
        float remainder = remaining * ASH_COST_MULTIPLIER;
        if (after.equals(embers)) {
            return new Drained(this, remainder);
        }
        HeartOverlay broken = withEmbers(after, now + reigniteInterval(countTrue(after)));
        return new Drained(broken, remainder);
    }

    /**
     * Advances the overlay one tick: it ends at its expiry, water or ice turns
     * every ember to ash, and otherwise the leftmost ash heart reignites when
     * its interval has passed.
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
        if (wet) {
            return extinguish(now);
        }
        return reignite(health, now);
    }

    private HeartOverlay reignite(float health, long now) {
        int ashSlot = leftmostAshSlot(filledSlots(health));
        if (now < reigniteAt || ashSlot == NO_SLOT) {
            return this;
        }
        List<Boolean> after = new ArrayList<>(embers);
        while (after.size() <= ashSlot) {
            after.add(Boolean.FALSE);
        }
        after.set(ashSlot, Boolean.TRUE);
        return withEmbers(after, now + reigniteInterval(countTrue(after)));
    }

    /**
     * Reignites every real heart under a fire hit, which the overlay takes
     * whole in place of damage, at most once per cooldown.
     *
     * @param health the player's real health
     * @param now    the game time
     * @return the overlay with an ember over every real heart, the same instance when none was ash or fire is cooling down
     */
    public HeartOverlay ignite(float health, long now) {
        int slots = filledSlots(health);
        if (!stands() || now < igniteReadyAt || leftmostAshSlot(slots) == NO_SLOT) {
            return this;
        }
        // kindle-ember-hearts-ash-and-retaliate: fire damage deals nothing and reignites every ash heart
        List<Boolean> after = new ArrayList<>(embers);
        while (after.size() < slots) {
            after.add(Boolean.FALSE);
        }
        for (int slot = 0; slot < slots; slot++) {
            after.set(slot, Boolean.TRUE);
        }
        return new HeartOverlay(kind, after, expiresAt, now + reigniteInterval(countTrue(after)),
                now + FIRE_REIGNITE_COOLDOWN);
    }

    private HeartOverlay extinguish(long now) {
        if (emberCount() == 0) {
            return this;
        }
        return withEmbers(Collections.nCopies(embers.size(), Boolean.FALSE), now + reigniteInterval(0));
    }

    private HeartOverlay withEmbers(List<Boolean> after, long nextReigniteAt) {
        return new HeartOverlay(kind, after, expiresAt, nextReigniteAt, igniteReadyAt);
    }

    private int leftmostAshSlot(int filledSlots) {
        for (int slot = 0; slot < filledSlots; slot++) {
            if (!emberAt(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    private static int countTrue(List<Boolean> flags) {
        return (int) flags.stream().filter(Boolean::booleanValue).count();
    }
}
