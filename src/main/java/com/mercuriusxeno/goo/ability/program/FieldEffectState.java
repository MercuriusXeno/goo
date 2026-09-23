package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * What a {@link FieldEffectStep} carries across ticks: the strikes in
 * flight, the ticks before the next strike may start, the charges spent on
 * the current stack, and the strike timing its renderer reads. A step is
 * an immutable definition, so the host keeps this state for it (capability
 * {@link HostCapability#FIELD_EFFECT}); the marker keeps it on its block
 * entity, which saves it and syncs it to the client, where the metal spike
 * visual reads the strikes.
 */
public final class FieldEffectState {

    private static final String TAG_STRIKES = "FieldStrikes";
    private static final String TAG_COOLDOWN = "FieldCooldown";
    private static final String TAG_CHARGES_SPENT = "FieldChargesSpent";
    private static final String TAG_STRIKE_TICK = "FieldStrikeTick";
    private static final String TAG_STRIKE_TICKS = "FieldStrikeTicks";
    private static final int STRIDE = 5;
    private static final int OFFSET_X = 1;
    private static final int OFFSET_Y = 2;
    private static final int OFFSET_Z = 3;
    private static final int OFFSET_AGE = 4;

    private List<FieldStrike> strikes = List.of();
    private int cooldown;
    private int chargesSpent;
    private int strikeTick;
    private int strikeTicks;

    /**
     * Returns the strikes in flight, oldest first.
     *
     * @return the strikes
     */
    public List<FieldStrike> strikes() {
        return strikes;
    }

    /**
     * Replaces the strikes in flight.
     *
     * @param strikes the strikes, oldest first
     */
    public void setStrikes(List<FieldStrike> strikes) {
        this.strikes = List.copyOf(strikes);
    }

    /**
     * Adds a strike in flight after the others.
     *
     * @param strike the strike just chosen
     */
    public void addStrike(FieldStrike strike) {
        List<FieldStrike> grown = new ArrayList<>(strikes);
        grown.add(strike);
        strikes = List.copyOf(grown);
    }

    /**
     * Tests whether a strike in flight already aims at the entity.
     *
     * @param entityId the entity's id
     * @return true when the entity is being struck
     */
    public boolean isStriking(int entityId) {
        return strikes.stream().anyMatch(strike -> strike.entityId() == entityId);
    }

    /**
     * Returns the ticks before the next strike may start.
     *
     * @return the cooldown, zero when a strike may start
     */
    public int cooldown() {
        return cooldown;
    }

    /**
     * Sets the ticks before the next strike may start.
     *
     * @param ticks the cooldown
     */
    public void setCooldown(int ticks) {
        this.cooldown = ticks;
    }

    /**
     * Returns the charges spent on the current stack.
     *
     * @return the charges spent
     */
    public int chargesSpent() {
        return chargesSpent;
    }

    /**
     * Sets the charges spent on the current stack.
     *
     * @param charges the charges spent
     */
    public void setChargesSpent(int charges) {
        this.chargesSpent = charges;
    }

    /**
     * Returns the strike age at which the strike lands.
     *
     * @return the landing tick
     */
    public int strikeTick() {
        return strikeTick;
    }

    /**
     * Returns how many ticks a strike stays in flight.
     *
     * @return the strike length
     */
    public int strikeTicks() {
        return strikeTicks;
    }

    /**
     * Records the strike timing the running step declares, which the
     * renderer reads to phase each strike's animation.
     *
     * @param landingTick the strike age at which the strike lands
     * @param length      how many ticks a strike stays in flight
     */
    public void time(int landingTick, int length) {
        this.strikeTick = landingTick;
        this.strikeTicks = length;
    }

    /**
     * Writes the state onto the block entity's value stream.
     *
     * @param output the value output
     */
    public void save(ValueOutput output) {
        int[] packed = new int[strikes.size() * STRIDE];
        for (int i = 0; i < strikes.size(); i++) {
            FieldStrike strike = strikes.get(i);
            int base = i * STRIDE;
            packed[base] = strike.entityId();
            packed[base + OFFSET_X] = Float.floatToRawIntBits(strike.x());
            packed[base + OFFSET_Y] = Float.floatToRawIntBits(strike.y());
            packed[base + OFFSET_Z] = Float.floatToRawIntBits(strike.z());
            packed[base + OFFSET_AGE] = strike.age();
        }
        output.putIntArray(TAG_STRIKES, packed);
        output.putInt(TAG_COOLDOWN, cooldown);
        output.putInt(TAG_CHARGES_SPENT, chargesSpent);
        output.putInt(TAG_STRIKE_TICK, strikeTick);
        output.putInt(TAG_STRIKE_TICKS, strikeTicks);
    }

    /**
     * Reads the state back from the block entity's value stream.
     *
     * @param input the value input
     */
    public void load(ValueInput input) {
        int[] packed = input.getIntArray(TAG_STRIKES).orElse(new int[0]);
        List<FieldStrike> read = new ArrayList<>(packed.length / STRIDE);
        for (int base = 0; base + STRIDE <= packed.length; base += STRIDE) {
            read.add(new FieldStrike(packed[base],
                    Float.intBitsToFloat(packed[base + OFFSET_X]),
                    Float.intBitsToFloat(packed[base + OFFSET_Y]),
                    Float.intBitsToFloat(packed[base + OFFSET_Z]),
                    packed[base + OFFSET_AGE]));
        }
        strikes = List.copyOf(read);
        cooldown = input.getIntOr(TAG_COOLDOWN, 0);
        chargesSpent = input.getIntOr(TAG_CHARGES_SPENT, 0);
        strikeTick = input.getIntOr(TAG_STRIKE_TICK, 0);
        strikeTicks = input.getIntOr(TAG_STRIKE_TICKS, 0);
    }
}
