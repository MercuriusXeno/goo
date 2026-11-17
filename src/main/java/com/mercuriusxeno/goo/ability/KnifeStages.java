package com.mercuriusxeno.goo.ability;

/**
 * The knives a Knives hold has formed: they form in stages, each stage
 * taking the same ticks, the first knife included, and each stage adding
 * the next Fibonacci number of knives, 1, 1, 2, 3, 5, so five stages form
 * 12 and a sixth adds 8.
 * decision knives-form-in-fibonacci-stages-to-twelve
 * decision knives-stage-takes-twelve-ticks-with-no-cooldown
 */
public final class KnifeStages {

    private KnifeStages() {
    }

    /**
     * The knives formed once a hold has run some ticks: none until the first
     * stage's ticks have gone, then each whole stage's knives added, never
     * past the stages the charge names.
     *
     * @param heldTicks  the ticks the hold has run
     * @param stages     the stages the charge forms in
     * @param stageTicks the ticks each stage takes
     * @return the knives formed
     */
    public static int formedAt(int heldTicks, int stages, int stageTicks) {
        if (stageTicks <= 0 || heldTicks < 0) {
            return 0;
        }
        int reached = Math.min(stages, heldTicks / stageTicks);
        int formed = 0;
        int stage = 1;
        int next = 1;
        for (int done = 0; done < reached; done++) {
            formed += stage;
            int after = stage + next;
            stage = next;
            next = after;
        }
        return formed;
    }
}
