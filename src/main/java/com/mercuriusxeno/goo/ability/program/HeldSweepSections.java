package com.mercuriusxeno.goo.ability.program;

import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.function.Supplier;

/**
 * The profiler sections each part of a held sweep's work reports its time
 * under, on the server and on the caster's client, so a profile of a
 * lagging Scry, Glimmer or Magnet names the part the frame or the tick
 * went to.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
 */
public final class HeldSweepSections {

    /** The client moving every front on: the shell walk, the lit walls, the fields dropped and the mobs painted. */
    public static final String FRONT_UPKEEP = "goo_held_sweep_front_upkeep";
    /** The client gathering the walls a frame draws from every live pulse. */
    public static final String WALL_LIST = "goo_held_sweep_wall_list";
    /** The client finding the nearest shape a sorted draw sorts by. */
    public static final String SORT_KEY = "goo_held_sweep_sort_key";
    /** The client emitting and submitting every lit wall's quads. */
    public static final String WALL_DRAW = "goo_held_sweep_wall_draw";
    /** The client emitting and submitting every travelling front's sphere. */
    public static final String FRONT_DRAW = "goo_held_sweep_front_draw";
    /** The client gathering the ore blocks showing this frame. */
    public static final String ORE_LIST = "goo_held_sweep_ore_list";
    /** The client emitting and submitting the revealed ore's two passes. */
    public static final String ORE_DRAW = "goo_held_sweep_ore_draw";
    /** The server scanning the entities around one Scry front and striking those it crossed. */
    public static final String SCRY_STRIKE = "goo_held_sweep_scry_strike";
    /** The server walking the band one ore-sense front crossed for the veins it first reached. */
    public static final String ORE_WALK = "goo_held_sweep_ore_walk";
    /** The server sending the caster every live pulse's payload. */
    public static final String PAYLOAD_SENDS = "goo_held_sweep_payload_sends";

    private HeldSweepSections() {
    }

    /**
     * Runs a part of the work under its profiler section.
     *
     * @param section the section the part reports its time under
     * @param work    the part
     */
    public static void profiled(String section, Runnable work) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push(section);
        try {
            work.run();
        } finally {
            profiler.pop();
        }
    }

    /**
     * Runs a part of the work under its profiler section and answers what it made.
     *
     * @param section the section the part reports its time under
     * @param work    the part
     * @param <T>     what the part makes
     * @return what the part made
     */
    public static <T> T profiled(String section, Supplier<T> work) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push(section);
        try {
            return work.get();
        } finally {
            profiler.pop();
        }
    }
}
