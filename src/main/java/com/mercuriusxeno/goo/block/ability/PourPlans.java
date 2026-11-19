package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.type.Adhesion;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The pours connected goo cells are working through on a server: each
 * network's resting counts, kept between its steps so every cell heads for
 * the same rest and moves one way only, and the game time it last stepped,
 * so the network steps once a tick whichever of its cells ticks first. It
 * holds no goo and saves nothing: each cell's layers stand in its block
 * state, and a plan the level no longer matches, goo landed, broken or
 * poured off a ledge, is dropped and worked out anew from the cells.
 * connected-goo-cells-form-one-network-that-works-out-the-pour
 */
public final class PourPlans {

    /** A tick before any game time, which no plan has stepped on. */
    private static final long NEVER = Long.MIN_VALUE;
    private static final PourNetwork.Step NOTHING_THIS_TICK = new PourNetwork.Step(Map.of(), List.of());

    private final Map<ResourceKey<Level>, Map<BlockPos, Plan>> byLevel = new HashMap<>();

    /**
     * One network's pour under way.
     */
    private static final class Plan {
        private final Map<BlockPos, Integer> rest;
        /** The layers the cells hold after the last step, which the level matches while the plan stands. */
        private final Map<BlockPos, Integer> expected;
        private long steppedAt = NEVER;

        private Plan(Map<BlockPos, Integer> rest, Map<BlockPos, Integer> expected) {
            this.rest = rest;
            this.expected = expected;
        }

        private boolean matches(PourNetwork.Ground ground) {
            return expected.entrySet().stream()
                    .allMatch(cell -> ground.cellAt(cell.getKey()).orElse(0) == cell.getValue());
        }
    }

    /**
     * The step a cell's network takes at a game time, worked out anew where no plan the level matches holds the
     * cell.
     *
     * @param level    the level the cell stands in
     * @param now      the game time
     * @param cell     the cell whose pour tick runs
     * @param ground   the places around the cell, as the level holds them
     * @param cohesion the goo's cohesion step
     * @param adhesion the goo's adhesion rung
     * @return the step, one moving nothing where the network already stepped this tick, empty where it rests
     */
    Optional<PourNetwork.Step> stepAt(ResourceKey<Level> level, long now, BlockPos cell,
                                             PourNetwork.Ground ground, int cohesion, Adhesion adhesion) {
        Map<BlockPos, Plan> plans = byLevel.computeIfAbsent(level, key -> new HashMap<>());
        Plan plan = standingPlan(plans, cell, ground);
        if (plan == null) {
            plan = planFor(plans, cell, ground, cohesion, adhesion);
        }
        if (plan == null) {
            return Optional.empty();
        }
        if (plan.steppedAt == now) {
            return Optional.of(NOTHING_THIS_TICK);
        }
        PourNetwork.Step step = PourNetwork.stepToward(plan.expected, plan.rest);
        plan.expected.putAll(step.layers());
        plan.steppedAt = now;
        if (plan.expected.equals(plan.rest)) {
            forget(plans, plan);
        }
        return Optional.of(step);
    }

    /**
     * The plan holding a cell that the level still matches; a plan it no longer matches is dropped.
     *
     * @param plans  the level's plans, by cell
     * @param cell   the cell
     * @param ground the places around the cell
     * @return the plan, null where none stands
     */
    private static Plan standingPlan(Map<BlockPos, Plan> plans, BlockPos cell, PourNetwork.Ground ground) {
        Plan plan = plans.get(cell);
        if (plan != null && !plan.matches(ground)) {
            forget(plans, plan);
            return null;
        }
        return plan;
    }

    /**
     * Works a cell's network out and holds its plan for each of its cells, dropping any plan it overlaps.
     *
     * @param plans    the level's plans, by cell
     * @param cell     the cell whose pour tick runs
     * @param ground   the places around the cell
     * @param cohesion the goo's cohesion step
     * @param adhesion the goo's adhesion rung
     * @return the plan, null where every cell already rests
     */
    private static Plan planFor(Map<BlockPos, Plan> plans, BlockPos cell, PourNetwork.Ground ground, int cohesion,
                                Adhesion adhesion) {
        Map<BlockPos, Integer> rest = PourNetwork.rest(cell, ground, cohesion, adhesion);
        Map<BlockPos, Integer> standing = new LinkedHashMap<>();
        rest.keySet().forEach(place -> standing.put(place, ground.cellAt(place).orElse(0)));
        if (standing.equals(rest)) {
            return null;
        }
        Set<Plan> overlapped = new HashSet<>();
        rest.keySet().forEach(place -> {
            Plan old = plans.get(place);
            if (old != null) {
                overlapped.add(old);
            }
        });
        overlapped.forEach(old -> forget(plans, old));
        Plan plan = new Plan(rest, standing);
        rest.keySet().forEach(place -> plans.put(place, plan));
        return plan;
    }

    /**
     * Drops every plan, as a server stop does.
     */
    public void clear() {
        byLevel.clear();
    }

    private static void forget(Map<BlockPos, Plan> plans, Plan plan) {
        plan.rest.keySet().forEach(place -> plans.remove(place, plan));
    }
}
