package com.mercuriusxeno.goo.block.tap;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.Optional;

/**
 * The rate an open tap drips at: one drip of 1 mB per 256, 64, 16, 4 or 1
 * ticks, in 4x steps (decision five-rates-in-fourfold-steps).
 */
public enum TapDripGrade {
    ONE_PER_256_TICKS(256),
    ONE_PER_64_TICKS(64),
    ONE_PER_16_TICKS(16),
    ONE_PER_4_TICKS(4),
    ONE_PER_TICK(1);

    /**
     * The grade a closed valve opens to.
     */
    public static final TapDripGrade SLOWEST = ONE_PER_256_TICKS;
    /**
     * Save key for the grade, stored as its interval in ticks.
     */
    static final String TAG_DRIP_GRADE = "DripGrade";

    private final int intervalTicks;

    TapDripGrade(int intervalTicks) {
        this.intervalTicks = intervalTicks;
    }

    /**
     * @return ticks between drips at this grade
     */
    public int intervalTicks() {
        return intervalTicks;
    }

    /**
     * @return true at 1:1, the one grade drawn as a pouring stream rather
     *         than drips (decision one-to-one-draws-a-stream)
     */
    public boolean pours() {
        return this == ONE_PER_TICK;
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
     * @param intervalTicks ticks between drips
     * @return the grade dripping at that interval, or the slowest when none does
     */
    static TapDripGrade ofInterval(int intervalTicks) {
        for (TapDripGrade grade : values()) {
            if (grade.intervalTicks == intervalTicks) {
                return grade;
            }
        }
        return SLOWEST;
    }

    /**
     * @param output the tap's save output
     */
    void save(ValueOutput output) {
        output.putInt(TAG_DRIP_GRADE, intervalTicks);
    }

    /**
     * Reads a saved grade; a save holding none, as a tap saved before grades
     * existed, reads the slowest.
     *
     * @param input the tap's save input
     * @return the saved grade
     */
    static TapDripGrade load(ValueInput input) {
        return ofInterval(input.getIntOr(TAG_DRIP_GRADE, SLOWEST.intervalTicks));
    }
}
