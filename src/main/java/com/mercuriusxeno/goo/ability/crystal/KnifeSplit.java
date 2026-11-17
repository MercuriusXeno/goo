package com.mercuriusxeno.goo.ability.crystal;

import java.util.ArrayList;
import java.util.List;

/**
 * How a Knives throw splits its knives across the painted mobs: evenly in
 * painted order, an uneven remainder going to the mobs painted first. A mob
 * takes no more than its knives-to-kill, the fewest knives that take its
 * health to zero, and its spare knives pass to the next mob in painted order;
 * spares past the last mob wrap round to the earliest mob still short of its
 * kill, as the operator ruled. Knives no mob takes stay in the fan.
 * decision knives-split-evenly-capped-at-the-kill
 * decision knives-with-no-target-stay-in-the-fan
 */
public final class KnifeSplit {

    private KnifeSplit() {
    }

    /**
     * A throw's split.
     *
     * @param perMob the knives each painted mob takes, in painted order
     * @param left   the knives no mob takes, which stay in the fan
     */
    public record Split(List<Integer> perMob, int left) {

        /**
         * @return the knives the split throws
         */
        public int thrown() {
            return perMob.stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Splits the knives across the painted mobs.
     *
     * @param knives       the knives formed
     * @param knivesToKill each painted mob's knives-to-kill, in painted order
     * @return the split, every mob in painted order
     */
    public static Split split(int knives, List<Integer> knivesToKill) {
        int mobs = knivesToKill.size();
        if (mobs == 0) {
            return new Split(List.of(), knives);
        }
        List<Integer> taken = new ArrayList<>(mobs);
        int spare = 0;
        for (int mob = 0; mob < mobs; mob++) {
            int offered = knives / mobs + (mob < knives % mobs ? 1 : 0) + spare;
            int takes = Math.min(offered, knivesToKill.get(mob));
            taken.add(takes);
            spare = offered - takes;
        }
        for (int mob = 0; mob < mobs && spare > 0; mob++) {
            int more = Math.min(spare, knivesToKill.get(mob) - taken.get(mob));
            taken.set(mob, taken.get(mob) + more);
            spare -= more;
        }
        return new Split(List.copyOf(taken), spare);
    }
}
