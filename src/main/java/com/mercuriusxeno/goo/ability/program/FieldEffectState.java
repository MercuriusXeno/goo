package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * What a {@link FieldEffectStep} carries across ticks: the strikes in
 * flight, the ticks before the next strike may start, the charges spent on
 * the current stack, the ticks the field has run and the ticks its
 * teardown has run, and the charges left. A step is an immutable
 * definition, so the host keeps this run state for it (capability
 * {@link HostCapability#FIELD_EFFECT}); the marker keeps it on its block
 * entity, which saves it and syncs it to the client, where the metal spike
 * visual reads the strikes and the crystal cloud visual reads the radius
 * fraction and the charge density. The step's params stay on the step,
 * which the client reads from the synced ability definition (decision
 * capability-interfaces-derive-host-kind).
 */
public final class FieldEffectState {

    private static final String TAG_STRIKES = "FieldStrikes";
    private static final String TAG_COOLDOWN = "FieldCooldown";
    private static final String TAG_CHARGES_SPENT = "FieldChargesSpent";
    private static final String TAG_FIELD_TICKS = "FieldTicks";
    private static final String TAG_TEARDOWN_TICKS = "FieldTeardownTicks";
    private static final String TAG_CHARGES_LEFT = "FieldChargesLeft";
    private static final String TAG_MAX_CHARGES = "FieldMaxCharges";
    private static final int STRIDE = 5;
    private static final int OFFSET_X = 1;
    private static final int OFFSET_Y = 2;
    private static final int OFFSET_Z = 3;
    private static final int OFFSET_AGE = 4;

    private List<FieldStrike> strikes = List.of();
    private int cooldown;
    private int chargesSpent;
    private int fieldTicks;
    private int teardownTicks;
    private int chargesLeft;
    private int maxCharges;

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
     * Returns how many ticks the field has run, counting the current one.
     *
     * @return the field ticks
     */
    public int fieldTicks() {
        return fieldTicks;
    }

    /**
     * Counts one more tick of the field.
     */
    public void countTick() {
        fieldTicks++;
    }

    /**
     * Returns how many ticks the teardown has run; zero while the budget lasts.
     *
     * @return the teardown ticks
     */
    public int teardownTicks() {
        return teardownTicks;
    }

    /**
     * Sets how many ticks the teardown has run.
     *
     * @param ticks the teardown ticks, zero while the budget lasts
     */
    public void setTeardownTicks(int ticks) {
        this.teardownTicks = ticks;
    }


    /**
     * Records the charges left in the budget, raising the peak the density
     * is measured against.
     *
     * @param charges the charges left over every stack
     */
    public void recordCharges(int charges) {
        this.chargesLeft = charges;
        this.maxCharges = Math.max(maxCharges, charges);
    }

    /**
     * Returns the charges left as a fraction of the most the budget held.
     *
     * @return the density in [0, 1], zero before any budget stood
     */
    public float density() {
        return maxCharges > 0 ? (float) chargesLeft / maxCharges : 0f;
    }

    /**
     * Returns the field's size as a fraction of its radius: rising over the
     * expand ticks after the fuse, falling over the contract ticks once the
     * budget is spent, whole between.
     *
     * @param expandTicks   ticks the field takes to expand after the fuse
     * @param contractTicks ticks the field takes to contract once its budget is spent
     * @return the fraction in [0, 1]
     */
    public float radiusFraction(int expandTicks, int contractTicks) {
        if (teardownTicks > 0) {
            return contractTicks > 0 ? Math.max(0f, 1f - (float) teardownTicks / contractTicks) : 0f;
        }
        return expandTicks > 0 ? Math.min(1f, (float) fieldTicks / expandTicks) : 1f;
    }

    /**
     * Answers whether the field is expanding or contracting.
     *
     * @param expandTicks   ticks the field takes to expand after the fuse
     * @param contractTicks ticks the field takes to contract once its budget is spent
     * @return true while its size is below whole
     */
    public boolean isAnimating(int expandTicks, int contractTicks) {
        return radiusFraction(expandTicks, contractTicks) < 1f;
    }

    /**
     * Writes the state onto the block entity's value stream.
     *
     * @param output the value output
     */
    public void save(ValueOutput output) {
        output.putIntArray(TAG_STRIKES, packStrikes());
        output.putInt(TAG_COOLDOWN, cooldown);
        output.putInt(TAG_CHARGES_SPENT, chargesSpent);
        output.putInt(TAG_FIELD_TICKS, fieldTicks);
        output.putInt(TAG_TEARDOWN_TICKS, teardownTicks);
        output.putInt(TAG_CHARGES_LEFT, chargesLeft);
        output.putInt(TAG_MAX_CHARGES, maxCharges);
    }

    /**
     * Reads the state back from the block entity's value stream.
     *
     * @param input the value input
     */
    public void load(ValueInput input) {
        strikes = unpackStrikes(input.getIntArray(TAG_STRIKES).orElse(new int[0]));
        cooldown = input.getIntOr(TAG_COOLDOWN, 0);
        chargesSpent = input.getIntOr(TAG_CHARGES_SPENT, 0);
        fieldTicks = input.getIntOr(TAG_FIELD_TICKS, 0);
        teardownTicks = input.getIntOr(TAG_TEARDOWN_TICKS, 0);
        chargesLeft = input.getIntOr(TAG_CHARGES_LEFT, 0);
        maxCharges = input.getIntOr(TAG_MAX_CHARGES, 0);
    }

    /**
     * Packs the strikes as five ints each: entity id, the aimed point's
     * coordinates as float bits, and age.
     *
     * @return the packed strikes
     */
    private int[] packStrikes() {
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
        return packed;
    }

    /**
     * Reads strikes packed by {@link #packStrikes}.
     *
     * @param packed the packed strikes
     * @return the strikes, oldest first
     */
    private static List<FieldStrike> unpackStrikes(int[] packed) {
        List<FieldStrike> read = new ArrayList<>(packed.length / STRIDE);
        for (int base = 0; base + STRIDE <= packed.length; base += STRIDE) {
            read.add(new FieldStrike(packed[base],
                    Float.intBitsToFloat(packed[base + OFFSET_X]),
                    Float.intBitsToFloat(packed[base + OFFSET_Y]),
                    Float.intBitsToFloat(packed[base + OFFSET_Z]),
                    packed[base + OFFSET_AGE]));
        }
        return List.copyOf(read);
    }
}
