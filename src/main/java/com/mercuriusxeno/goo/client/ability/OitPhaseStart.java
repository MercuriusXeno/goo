package com.mercuriusxeno.goo.client.ability;

/**
 * What a level pass readies as its OIT phase starts, before the phase opens any pass: the copies of the level's
 * depth that Drain's vortex and Bore's drill clip against, which 26.3 refuses inside the phase's open passes.
 * decision goo-translucency-draws-through-order-independent-transparency
 */
public final class OitPhaseStart {

    private OitPhaseStart() {
    }

    /** Readies what the OIT phase's goo draws read, the opaque depth whole by now. */
    public static void beforeTheOitPhase() {
        MarchedBoxPass.copyDepthBeforeTheOitPhase();
    }
}
