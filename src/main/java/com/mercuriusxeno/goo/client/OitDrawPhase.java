package com.mercuriusxeno.goo.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import org.jspecify.annotations.Nullable;

/**
 * Where the sorting path's immediate draws stand while it draws them: on a level stage as before, inside one of 26.3's
 * OIT stages, or replaying at AfterLevel the far-to-near draws an OIT phase already drew. A draw whose render type
 * carries an OIT set draws in each OIT stage through that set's pipeline for the stage and is left out of the replay;
 * a draw whose type carries none is left out of the OIT stages and draws in the replay on its parity pipeline.
 * decision goo-translucency-draws-through-order-independent-transparency
 *
 * @param stage the OIT stage drawn in, or null outside one
 * @param replay whether the phase replays draws an OIT phase already drew
 */
public record OitDrawPhase(@Nullable OitStage stage, boolean replay) {

    /** A level stage, the path drawing as it does with improved transparency off. */
    public static final OitDrawPhase LEVEL = new OitDrawPhase(null, false);

    /** AfterLevel, replaying the far-to-near draws an OIT phase drew. */
    public static final OitDrawPhase AFTER_THE_OIT_PHASE = new OitDrawPhase(null, true);

    private static OitDrawPhase current = LEVEL;

    /**
     * @param stage the OIT stage
     * @return the phase inside that stage
     */
    public static OitDrawPhase inTheOitStage(OitStage stage) {
        return new OitDrawPhase(stage, false);
    }

    /**
     * @return the phase the path's draws stand in now
     */
    public static OitDrawPhase current() {
        return current;
    }

    /**
     * Runs draws standing in a phase.
     *
     * @param phase the phase
     * @param draws the draws
     */
    public static void during(OitDrawPhase phase, Runnable draws) {
        OitDrawPhase outer = current;
        current = phase;
        try {
            draws.run();
        } finally {
            current = outer;
        }
    }

    /**
     * The pipeline a render type's immediate draw draws through in a phase.
     *
     * @param phase  the phase
     * @param parity the type's parity pipeline
     * @param oit    the type's OIT set, or null where it carries none
     * @return the pipeline, or null where the draw draws nothing in this phase
     */
    public static @Nullable RenderPipeline pipelineFor(OitDrawPhase phase, RenderPipeline parity,
                                                       @Nullable OitPipelineSet oit) {
        if (phase.stage() != null) {
            return oit == null ? null : oit.getPipeline(phase.stage());
        }
        if (phase.replay() && oit != null) {
            return null;
        }
        return parity;
    }
}
