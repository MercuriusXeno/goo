package com.mercuriusxeno.goo.ability.program;

import net.minecraft.resources.Identifier;

/**
 * A host that can set off a vibration at its anchor carrying no source
 * entity, so sculk sensors trip and a warden walks to the spot with no one
 * to grow angry at, and a shrieker, which answers only a player, stays silent
 * (decision click-vibration-carries-no-source-entity).
 */
public interface VibrationHost extends StepHost {

    /**
     * Emits the game event at the host's anchor with no source entity; an id
     * no registry holds is logged and skipped.
     *
     * @param event the game event id, one the vibrations tag carries
     */
    void vibrate(Identifier event);
}
