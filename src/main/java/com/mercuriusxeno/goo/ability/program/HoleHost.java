package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can open its caster's portable hole on the struck face.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
public interface HoleHost extends StepHost {

    /**
     * Opens the caster's hole on the struck face: swallowing on a top face,
     * pouring the shelf out on a bottom face.
     *
     * @param lifetime the ticks the hole stands
     * @param refresh  the ticks left each swallow raises the hole's time left to when less remains
     * @return true when the hole opened
     */
    boolean openPortableHole(int lifetime, int refresh);
}
