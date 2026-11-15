package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;

/**
 * A goo item's exact value as it stores it: whole mB and the part under 1 mB.
 * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
 *
 * @param whole the whole mB
 * @param part  the part under 1 mB
 */
public record MixedVolume(int whole, GooFraction part) {

    /** No goo. */
    public static final MixedVolume ZERO = new MixedVolume(0, GooFraction.ZERO);

    /**
     * Carries any whole the part holds into the whole mB, so 5/3 of a part stands as 1
     * more whole mB and 2/3.
     *
     * @param whole the whole mB
     * @param part  the part, any non-negative fraction
     */
    public MixedVolume {
        int carried = part.floor();
        whole += carried;
        part = part.minus(GooFraction.of(carried));
    }

    /**
     * @param whole whole mB with no part
     * @return the mixed volume
     */
    public static MixedVolume ofWhole(int whole) {
        return new MixedVolume(whole, GooFraction.ZERO);
    }

    /**
     * @param exact an exact value
     * @return its whole mB and its part
     */
    public static MixedVolume of(GooFraction exact) {
        return new MixedVolume(0, exact);
    }

    /**
     * @return whole plus part
     */
    public GooFraction exact() {
        return GooFraction.of(whole).plus(part);
    }

    /**
     * Combines two values exactly: the parts sum as fractions and a whole they make
     * moves into the whole mB, so 1/2 and 2/3 join as 1 and 1/6.
     *
     * @param other the value joining this one
     * @return the sum
     */
    public MixedVolume plus(MixedVolume other) {
        return new MixedVolume(whole + other.whole, part.plus(other.part));
    }

    /**
     * @return true when it holds no goo
     */
    public boolean isEmpty() {
        return !exact().isPositive();
    }
}
