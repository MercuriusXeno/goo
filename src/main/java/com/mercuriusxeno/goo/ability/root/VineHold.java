package com.mercuriusxeno.goo.ability.root;

/**
 * The vines holding a living thing and the ticks they hold it for: a Vines
 * cell gives one vine at 1 layer, two at 2, three at 4, four at 6 and five
 * from 8, each vine slows by a fifth, so five hold it still, and the root
 * lasts 3 seconds plus 1 for each vine held. A cell touched while vines
 * hold adds its vines to them, to five at most, and refreshes the time left
 * up to that cell's own threshold, never shortening it.
 * vines-root-is-volume-based-one-to-five-vines-by-layers
 * vines-roots-last-three-seconds-plus-one-per-stack-and-each-vine-refreshes
 *
 * @param vines     the vines held, none to five
 * @param ticksLeft the ticks until the vines let go
 */
public record VineHold(int vines, int ticksLeft) {

    /** The hold of a thing no vines hold. */
    public static final VineHold NONE = new VineHold(0, 0);

    /** The most vines a thing can be held by, which hold it still. */
    public static final int MOST_VINES = 5;

    /** The share of its speed each vine held takes. */
    static final double SLOW_PER_VINE = 0.2;

    /** The seconds a root lasts before its vines add theirs. */
    static final int BASE_SECONDS = 3;

    private static final int TICKS_A_SECOND = 20;

    /**
     * The layers at which each vine comes, the first to the fifth.
     * vines-root-is-volume-based-one-to-five-vines-by-layers
     */
    private static final int[] LAYERS_FOR_VINE = {1, 2, 4, 6, 8};

    /**
     * The vines a cell gives by its layers: one at 1 layer, two at 2, three
     * at 4, four at 6 and five from 8.
     *
     * @param layers the layers the cell holds
     * @return the vines it gives, none to five
     */
    public static int vinesFrom(int layers) {
        int vines = 0;
        while (vines < MOST_VINES && layers >= LAYERS_FOR_VINE[vines]) {
            vines++;
        }
        return vines;
    }

    /**
     * The share of its speed a thing held by vines keeps.
     *
     * @param vines the vines held
     * @return 1 for none, falling a fifth a vine to none at five
     */
    public static double speedOf(int vines) {
        return Math.max(0.0, 1.0 - SLOW_PER_VINE * vines);
    }

    /**
     * The ticks a count of vines holds for, the threshold a cell giving them refreshes to.
     *
     * @param vines the vines
     * @return 3 seconds plus 1 a vine, in ticks; none for no vines
     */
    public static int ticksOf(int vines) {
        return vines > 0 ? (BASE_SECONDS + vines) * TICKS_A_SECOND : 0;
    }

    /**
     * The hold after a cell is touched: its vines add to those held, to five
     * at most, and the time left rises to the cell's own threshold where it
     * stands short of it.
     *
     * @param cellLayers the layers the touched cell holds
     * @return the hold after the touch
     */
    public VineHold touched(int cellLayers) {
        int given = vinesFrom(cellLayers);
        return new VineHold(Math.min(MOST_VINES, vines + given), Math.max(ticksLeft, ticksOf(given)));
    }
}
