package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers custom sound events for the goo mod. Sound assets live under
 * {@code src/main/resources/assets/goo/sounds/} and are mapped to event
 * ids via {@code assets/goo/sounds.json}.
 */
public final class GooSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(Registries.SOUND_EVENT, Goo.MODID);

    /** The black hole's sphere expand / destroy / contract "boom". */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE =
        SOUND_EVENTS.register("effects.black_hole",
            () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(Goo.MODID, "effects.black_hole")));

    /** Glow goo throw: laser beam fire sound. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOW_THROW =
        SOUND_EVENTS.register("effects.glow_throw",
            () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(Goo.MODID, "effects.glow_throw")));

    /**
     * The shared ability-down cue every held effect naming no down sound of
     * its own plays when it ends: a descending woosh.
     * held-effects-sound-up-and-down
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> ABILITY_DOWN =
        SOUND_EVENTS.register("effects.ability_down", () -> SoundEvent.createVariableRangeEvent(GooSoundIds.ABILITY_DOWN));

    /** Decay's and Hive's gnat buzz (decision decay-gnats-degrade-each-block-once). */
    public static final DeferredHolder<SoundEvent, SoundEvent> GNAT_BUZZ =
        SOUND_EVENTS.register("effects.gnat_buzz", () -> SoundEvent.createVariableRangeEvent(GooSoundIds.GNAT_BUZZ));


    /** The slime coat's boing, each bounce (decision bounce-ramps-jump-height-while-jump-is-held). */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOING =
        SOUND_EVENTS.register("effects.boing", () -> SoundEvent.createVariableRangeEvent(GooSoundIds.BOING));

    /** Spire's rising rumble (decision spire-rumbles-like-a-dull-earthquake). */
    public static final DeferredHolder<SoundEvent, SoundEvent> EARTH_RUMBLE =
        SOUND_EVENTS.register("effects.earth_rumble",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.EARTH_RUMBLE));

    /** Click's click, rung at 15 under Sculker (decision sculker-echo-intensity-follows-vibration-rank). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CLICK =
        SOUND_EVENTS.register("ability.click", () -> SoundEvent.createVariableRangeEvent(GooSoundIds.CLICK));

    /** A portal standing (decision portal-placement-plays-a-zap-that-opens-into-a-hum). */
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_PLACE =
        SOUND_EVENTS.register("effects.portal_place",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.PORTAL_PLACE));

    /** A body stepping through a gate (decision stepping-through-plays-a-soft-whoosh-at-both-ends). */
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_CROSS =
        SOUND_EVENTS.register("effects.portal_cross",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.PORTAL_CROSS));

    /** A standing portal or mouth's loop (decision standing-portals-loop-a-soft-ambient-hum). */
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_HUM =
        SOUND_EVENTS.register("effects.portal_hum",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.PORTAL_HUM));

    /** An Outsider room gaining a full block (decision full-block-growth-wubs-and-raises-void-motes). */
    public static final DeferredHolder<SoundEvent, SoundEvent> OUTSIDER_WUB =
        SOUND_EVENTS.register("effects.outsider_wub",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.OUTSIDER_WUB));

    /** A door conjured toward the pocket (decision
     * channeling-to-the-pocket-conjures-a-door-that-envelops-the-player). */
    public static final DeferredHolder<SoundEvent, SoundEvent> OUTSIDER_CONJURE =
        SOUND_EVENTS.register("effects.outsider_conjure",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.OUTSIDER_CONJURE));

    /** Void's hole opening (decision hole-opens-with-the-outsider-wub-pitched-up). */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLE_OPEN =
        SOUND_EVENTS.register("effects.hole_open",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.HOLE_OPEN));

    /** Void's hole closing (decision hole-closes-with-the-outsider-wub-reversed-and-pitched-down). */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLE_CLOSE =
        SOUND_EVENTS.register("effects.hole_close",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.HOLE_CLOSE));

    /** A residual Vacuum takes (decision vacuum-sounds-a-suction-wind-and-rising-slurps). */
    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_SLURP =
        SOUND_EVENTS.register("effects.vacuum_slurp",
            () -> SoundEvent.createVariableRangeEvent(GooSoundIds.VACUUM_SLURP));

    private GooSounds() {}
}
