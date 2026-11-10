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


    /**
     * The slime coat's boing, each bounce: the vanilla slime's own squelches,
     * short and dry (decision bounce-ramps-jump-height-while-jump-is-held).
     */
    public static final Identifier BOING = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.boing");

    /**
     * Spire's rise: the mod's own synthesized dull earthquake rumble, looped
     * at the rising stone for the whole climb
     * (decision spire-rumbles-like-a-dull-earthquake).
     */
    public static final Identifier EARTH_RUMBLE = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.earth_rumble");

    /**
     * Click's click: vanilla's comparator click under an id of the mod's own,
     * so Sculker's sub-rank table rings it at 15 and a real comparator keeps
     * its own rank (decision sculker-echo-intensity-follows-vibration-rank).
     */
    public static final Identifier CLICK = Identifier.fromNamespaceAndPath(Goo.MODID, "ability.click");

    private GooSoundIds() {
    }
}
