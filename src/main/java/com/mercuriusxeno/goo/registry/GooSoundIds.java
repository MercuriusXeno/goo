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

    /**
     * A portal standing: the mod's own synthesized zap opening into a brief
     * hum, like a portal gun shot landing
     * (decision portal-placement-plays-a-zap-that-opens-into-a-hum).
     */
    public static final Identifier PORTAL_PLACE = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.portal_place");

    /**
     * A body stepping through a portal or a wormhole mouth: the mod's own
     * synthesized quick soft airy whoosh, at both ends
     * (decision stepping-through-plays-a-soft-whoosh-at-both-ends).
     */
    public static final Identifier PORTAL_CROSS = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.portal_cross");

    /**
     * A standing portal or wormhole mouth: the mod's own synthesized soft
     * ambient hum, one seamless loop
     * (decision standing-portals-loop-a-soft-ambient-hum).
     */
    public static final Identifier PORTAL_HUM = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.portal_hum");

    /**
     * An Outsider room gaining a full block on every side: a dull low wub,
     * a synthesized placeholder the operator may swap
     * (decision full-block-growth-wubs-and-raises-void-motes).
     */
    public static final Identifier OUTSIDER_WUB = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.outsider_wub");

    /**
     * A door conjured before a player channeling Outsider toward the pocket:
     * a rising shimmer, a synthesized placeholder the operator may swap
     * (decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player).
     */
    public static final Identifier OUTSIDER_CONJURE =
            Identifier.fromNamespaceAndPath(Goo.MODID, "effects.outsider_conjure");

    /**
     * Void's hole opening: the Outsider room's wub under a subtitle naming the
     * hole, played pitched up (decision hole-opens-with-the-outsider-wub-pitched-up).
     */
    public static final Identifier HOLE_OPEN = Identifier.fromNamespaceAndPath(Goo.MODID, "effects.hole_open");

    private GooSoundIds() {
    }
}
