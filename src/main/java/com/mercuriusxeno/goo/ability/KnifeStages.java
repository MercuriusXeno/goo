package com.mercuriusxeno.goo.ability;

/**
 * The knives a Knives hold has formed: they form in stages, each stage
 * taking the same ticks, the first knife included, and each stage adding
 * the next Fibonacci number of knives, 1, 1, 2, 3, 5, so five stages form
 * 12 and a sixth adds 8. After a throw the knives left round up to the
 * lowest stage whose total holds them, and the stage clock restarts at the
 * throw: each stage's ticks later the fan fills to the next stage's total,
 * never past the last.
 * decision knives-form-in-fibonacci-stages-to-twelve
 * decision knives-stage-takes-twelve-ticks-with-no-cooldown
 * decision knives-refill-from-the-stage-the-fan-rounds-up-to
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
        return totalOf(Math.min(stages, heldTicks / stageTicks));
    }

    /**
     * The knives the fan holds some ticks after a throw: the knives left
     * until a stage's ticks have gone, then each stage's total from the one
     * past the lowest stage holding the knives left, never past the last.
     *
     * @param left       the knives the throw left in the fan
     * @param sinceThrow the ticks since the throw
     * @param stages     the stages the charge forms in
     * @param stageTicks the ticks each stage takes
     * @return the knives in the fan
     */
    public static int formedSinceThrow(int left, int sinceThrow, int stages, int stageTicks) {
        if (stageTicks <= 0 || sinceThrow < stageTicks) {
            return left;
        }
        return Math.max(left, totalOf(Math.min(stages, stageHolding(left, stages) + sinceThrow / stageTicks)));
    }

    /**
     * The knives the fan holds a stage after a throw: the knives left round
     * up to the lowest stage holding them and fill to the next stage's total.
     *
     * @param left   the knives the throw left in the fan
     * @param stages the stages the charge forms in
     * @return the knives after the next stage
     */
    public static int refilledFrom(int left, int stages) {
        return formedSinceThrow(left, 1, stages, 1);
    }

    // The lowest stage whose total holds the knives left, 0 for none left, the last where none holds them.
    private static int stageHolding(int left, int stages) {
        int stage = 0;
        while (stage < stages && totalOf(stage) < left) {
            stage++;
        }
        return stage;
    }

    // The knives the first stages reached form between them: 1, 2, 4, 7, 12, 20.
    private static int totalOf(int reached) {
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
