package com.mercuriusxeno.goo.ability.reap;

/**
 * The three forms Reap's slashes take in turn, as the Crissaegrim's do in
 * Symphony of the Night, which mirrors and flattens one slash sprite from one
 * attack to the next: a diagonal sloping down to the right, its mirror sloping
 * up, and a flat one. Each says which way its arc slopes and which way the
 * bright band sweeps it.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
public enum SlashForm {
    /** Sloping down from the slasher's left to their right, swept left to right. */
    DOWN_DIAGONAL(-1, 1),
    /** Sloping up from the slasher's left to their right, swept left to right. */
    UP_DIAGONAL(1, 1),
    /** Level, swept right to left. */
    FLAT(0, -1);

    private final int slope;
    private final int sweep;

    SlashForm(int slope, int sweep) {
        this.slope = slope;
        this.sweep = sweep;
    }

    /**
     * Which way the arc slopes toward the slasher's right.
     *
     * @return 1 up, -1 down, 0 level
     */
    public int slope() {
        return slope;
    }

    /**
     * Which way the bright band sweeps the arc.
     *
     * @return 1 from the slasher's left to their right, -1 from right to left
     */
    public int sweep() {
        return sweep;
    }

    /**
     * The form the slash after this one takes.
     *
     * @return the next form, back to the first after the last
     */
    public SlashForm next() {
        SlashForm[] forms = values();
        return forms[(ordinal() + 1) % forms.length];
    }
}
