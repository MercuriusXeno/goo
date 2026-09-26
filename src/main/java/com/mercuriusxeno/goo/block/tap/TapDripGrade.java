package com.mercuriusxeno.goo.block.tap;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.Optional;

/**
 * The rate an open tap drips at: one drip of 1 mB per 64, 16, 4 or 1 ticks,
 * in 4x steps, and one step past 1:1 to 4 mB per tick
 * (decision five-rates-in-fourfold-steps).
 */
public enum TapDripGrade {
    ONE_PER_64_TICKS(64, 1),
    ONE_PER_16_TICKS(16, 1),
    ONE_PER_4_TICKS(4, 1),
    ONE_PER_TICK(1, 1),
    FOUR_PER_TICK(1, 4);

    /**
     * The grade a closed valve opens to.
     */
    public static final TapDripGrade SLOWEST = ONE_PER_64_TICKS;
    /**
     * Save key for the grade, stored as its name.
     */
    static final String TAG_DRIP_RATE = "DripRate";
    /**
     * Save key a tap saved before 1:4 existed holds, its grade's interval in ticks.
     */
    static final String TAG_LEGACY_DRIP_GRADE = "DripGrade";

    private final int intervalTicks;
    private final int dripVolume;

    TapDripGrade(int intervalTicks, int dripVolume) {
        this.intervalTicks = intervalTicks;
        this.dripVolume = dripVolume;
    }

    /**
     * @return ticks between drips at this grade
     */
    public int intervalTicks() {
        return intervalTicks;
    }

    /**
     * @return mB one drip draws at this grade
     */
    public int dripVolume() {
        return dripVolume;
    }

    /**
     * @return true at 1:1 and 1:4, the grades drawn as a pouring stream
     *         rather than drips (decision one-to-one-draws-a-stream)
     */
    public boolean pours() {
        return intervalTicks == 1;
    }

    /**
     * The grade one valve click leaves: off opens to the slowest grade, each
     * grade steps to the next faster one, and the fastest closes the valve.
     *
     * @param current the grade the valve runs at, or empty when closed
     * @return the grade after the click, or empty when the click closes the valve
     */
    public static Optional<TapDripGrade> afterValveClick(Optional<TapDripGrade> current) {
        if (current.isEmpty()) {
            return Optional.of(SLOWEST);
        }
        int next = current.get().ordinal() + 1;
        TapDripGrade[] grades = values();
        return next < grades.length ? Optional.of(grades[next]) : Optional.empty();
    }

    /**
     * @param intervalTicks ticks between drips, as a tap saved before 1:4 held it
     * @return the 1 mB grade dripping at that interval, or the slowest when none does
     */
    static TapDripGrade ofLegacyInterval(int intervalTicks) {
        for (TapDripGrade grade : values()) {
            if (grade.intervalTicks == intervalTicks && grade.dripVolume == 1) {
                return grade;
            }
        }
        return SLOWEST;
    }

    /**
     * @param output the tap's save output
     */
    void save(ValueOutput output) {
        output.putString(TAG_DRIP_RATE, name());
    }

    /**
     * Reads a saved grade. A tap saved before 1:4 existed holds its interval
     * instead, and an interval no grade drips at, such as the dropped 256,
     * reads the slowest; a save holding neither reads the slowest.
     *
     * @param input the tap's save input
     * @return the saved grade
     */
    static TapDripGrade load(ValueInput input) {
        Optional<String> name = input.getString(TAG_DRIP_RATE);
        if (name.isPresent()) {
            for (TapDripGrade grade : values()) {
                if (grade.name().equals(name.get())) {
                    return grade;
                }
            }
            return SLOWEST;
        }
        return ofLegacyInterval(input.getIntOr(TAG_LEGACY_DRIP_GRADE, SLOWEST.intervalTicks));
    }
}
