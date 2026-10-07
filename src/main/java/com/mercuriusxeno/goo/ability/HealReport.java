package com.mercuriusxeno.goo.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Which living things a program run healed: those whose health stands
 * higher after it than before. A held stream and a vital tap's drip both
 * report them, so the client raises the restoration effect on each. The
 * health and id reads come in as functions, the living entity's own getters
 * at every call site.
 * vitality-waves-regenerate-and-court
 * vitality-drip-heals-below
 *
 * @param <T>    the living thing's type
 * @param health reads a living thing's health
 * @param id     reads a living thing's entity id
 */
public record HealReport<T>(ToDoubleFunction<T> health, ToIntFunction<T> id) {

    /**
     * Runs a program on a living thing, noting it as healed when its health rose.
     *
     * @param living  the thing the program runs on
     * @param healed  the ids of the things healed so far, which this one joins when it healed
     * @param program the program run
     */
    public void runNoting(T living, List<Integer> healed, Runnable program) {
        double before = health.applyAsDouble(living);
        program.run();
        if (health.applyAsDouble(living) > before) {
            healed.add(id.applyAsInt(living));
        }
    }

    /**
     * Runs a program once over a set of living things it may reach, naming
     * those whose health it raised.
     *
     * @param watched the living things the program may heal
     * @param program the program run
     * @return the ids of the things it healed
     */
    public List<Integer> healedAmong(List<T> watched, Runnable program) {
        double[] before = watched.stream().mapToDouble(health).toArray();
        program.run();
        List<Integer> healed = new ArrayList<>();
        for (int i = 0; i < watched.size(); i++) {
            if (health.applyAsDouble(watched.get(i)) > before[i]) {
                healed.add(id.applyAsInt(watched.get(i)));
            }
        }
        return healed;
    }
}
