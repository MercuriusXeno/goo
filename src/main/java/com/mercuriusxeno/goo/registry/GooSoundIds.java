package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;

/**
 * The ids of goo's own sound events, held apart from {@link GooSounds} so
 * code naming a sound reads its id without binding the sound registry.
 */
public final class GooSoundIds {

    /**
     * The shared ability-down cue every held effect naming no down sound of
     * its own plays when it ends: a descending woosh.
     * held-effects-sound-up-and-down
     */
    public static final Identifier ABILITY_DOWN = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.ability_down");

    /**
     * Decay's and Hive's gnat buzz: one bee loop clip, so every play sounds
     * alike where the bee's own event picks among five
     * (decision decay-gnats-degrade-each-block-once).
     */
    public static final Identifier GNAT_BUZZ = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.gnat_buzz");

    private GooSoundIds() {
    }
}
