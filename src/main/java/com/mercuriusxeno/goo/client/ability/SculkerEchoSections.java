package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepSections;
import net.minecraft.util.profiling.Profiler;
import java.util.function.Supplier;

/**
 * The profiler sections and counters each part of Sculker's echo work
 * reports under, per tick, per sound and per frame, so a profile of a
 * lagging echo names the part the tick or the frame went to.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
public final class SculkerEchoSections {

    /** The tick joining each waiting sound to its vibration, and ringing those that waited out. */
    public static final String NOISES = "goo_sculker_noises";
    /** The tick growing every ring: its shell walked, each cell's answer read and each face it crossed lit. */
    public static final String RINGS = "goo_sculker_echo_rings";
    /** The tick sweeping the memory for the faces that have faded. */
    public static final String FADE = "goo_sculker_echo_fade";
    /** The tick letting the faces whose light runs out soonest go, once the memory holds its most. */
    public static final String FAINTEST = "goo_sculker_echo_faintest";
    /** The tick letting go of the kept quads no echo remembers. */
    public static final String QUADS_TRIM = "goo_sculker_echo_quads_trim";
    /** The tick forgetting the heard mobs gone or faded. */
    public static final String HEARD_SWEEP = "goo_sculker_heard_sweep";
    /** One sound or vibration heard: joined to its other half, and rung where its rank is known. */
    public static final String HEAR = "goo_sculker_hear";
    /** A rung sound's search for the mobs standing at its source. */
    public static final String SOUND_OWNERS = "goo_sculker_sound_owners";
    /** A block change dropping the kept quads and answers it reaches, and uncovering the faces it opens. */
    public static final String BLOCK_CHANGED = "goo_sculker_block_changed";
    /** A chunk load or unload dropping the kept quads and answers in and beside it. */
    public static final String CHUNK_FORGET = "goo_sculker_chunk_forget";
    /** The frame weighing each remembered face in view for its strength. */
    public static final String IN_VIEW = "goo_sculker_echo_in_view";
    /** The frame reading the shroom network blocks nearby, the once-a-second scan among them. */
    public static final String SHROOM_NEARBY = "goo_sculker_shroom_nearby";
    /** The once-a-second scan of every section within the shroom vision's reach. */
    public static final String SHROOM_RESCAN = "goo_sculker_shroom_rescan";
    /** The frame keeping the shroom blocks in view and merging them with the lit faces. */
    public static final String SHROOM_MERGE = "goo_sculker_shroom_merge";
    /** The frame handing every drawn block's quads to the vertex consumer, collecting those not kept. */
    public static final String SUBMIT = "goo_sculker_echo_submit";
    /** The frame ending the echo's batch, which uploads and draws its quads. */
    public static final String BATCH_END = "goo_sculker_echo_batch_end";

    /** The cells every ring's shell walked this tick. */
    public static final String SHELL_CELLS_COUNTER = "goo_sculker_echo_shell_cells";
    /** The cells whose answer the world was read for, not kept from an earlier ring. */
    public static final String CELL_READS_COUNTER = "goo_sculker_echo_cell_reads";
    /** The faces a ring lit this tick. */
    public static final String FACES_LIT_COUNTER = "goo_sculker_echo_faces_lit";
    /** The faces the fade sweep weighed this tick. */
    public static final String FADE_WEIGHED_COUNTER = "goo_sculker_echo_fade_weighed";
    /** The faces the full memory sorted to find its faintest this tick. */
    public static final String FAINTEST_SORTED_COUNTER = "goo_sculker_echo_faintest_sorted";
    /** The remembered faces a frame weighed for their strength, those of every section not out of view. */
    public static final String IN_VIEW_WEIGHED_COUNTER = "goo_sculker_echo_in_view_weighed";
    /** The waiting noises one sound or vibration passed over for its other half. */
    public static final String NOISES_WEIGHED_COUNTER = "goo_sculker_noises_weighed";
    /** The block models a frame collected for the lit blocks whose quads were not kept. */
    public static final String MODELS_COLLECTED_COUNTER = "goo_sculker_echo_models_collected";
    /** The quads a frame handed the vertex consumer. */
    public static final String QUADS_SUBMITTED_COUNTER = "goo_sculker_echo_quads_submitted";
    /** The sections the shroom scan read block by block. */
    public static final String SHROOM_SECTIONS_COUNTER = "goo_sculker_shroom_sections_read";
    /** The blocks the shroom scan read. */
    public static final String SHROOM_BLOCKS_COUNTER = "goo_sculker_shroom_blocks_read";
    /** The shroom network blocks a frame read the state of. */
    public static final String SHROOM_STATES_COUNTER = "goo_sculker_shroom_states_read";

    private SculkerEchoSections() {
    }

    /**
     * Runs a part of the work under its profiler section.
     *
     * @param section the section the part reports its time under
     * @param work    the part
     */
    public static void profiled(String section, Runnable work) {
        HeldSweepSections.profiled(section, work);
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
        return HeldSweepSections.profiled(section, work);
    }

    /**
     * Adds to a counter of the work.
     *
     * @param counter the counter
     * @param count   how much the part did
     */
    public static void count(String counter, int count) {
        Profiler.get().incrementCounter(counter, count);
    }
}
