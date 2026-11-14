package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The heal step's params: the health one pulse restores, or takes from an
 * undead target, and the ticks between a target's pulses. A target pulses
 * on the first tick a heal reaches it and once each {@code every} ticks
 * after, keeping its own clock, so a mob walking into a held cone is healed
 * at once and every target the cone holds keeps the one pace.
 * diagnose-and-correct-heal-spamming-zombie-noises
 *
 * @param amount the health one pulse restores
 * @param every  the ticks between a target's pulses
 */
public record HealPulse(Expr amount, int every) {

    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_EVERY = "every";
    /** The game tick each target's next pulse comes due, kept while the target stands, never saved. */
    private static final Map<Entity, Long> NEXT_PULSE = new WeakHashMap<>();

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HealPulse> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(HealPulse::amount),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_EVERY).forGetter(HealPulse::every)
    ).apply(inst, HealPulse::new));

    /**
     * Whether a target's pulse comes due on a game tick, starting its next
     * wait when it does: a target no heal has reached pulses at once.
     *
     * @param target the entity the heal reaches
     * @param now    the game tick
     * @return true when the pulse lands this tick
     */
    boolean dueOn(Entity target, long now) {
        if (now < NEXT_PULSE.getOrDefault(target, now)) {
            return false;
        }
        NEXT_PULSE.put(target, now + every);
        return true;
    }
}
