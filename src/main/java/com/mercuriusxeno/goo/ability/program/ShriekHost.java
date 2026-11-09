package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can shriek: cry like a sculk shrieker from where it stands and
 * call every hostile within reach to it
 * (decision shriek-blob-cries-once-and-calls-earshot).
 */
public interface ShriekHost extends StepHost {

    /**
     * Shrieks like a sculk shrieker and calls every hostile within reach.
     *
     * @param reach how far across the ground the shriek is heard, in blocks
     */
    void shriek(double reach);
}
