package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.LingeringAppliers;
import com.mercuriusxeno.goo.ability.astral.LunarArrival;
import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.ability.astral.WayHome;
import com.mercuriusxeno.goo.ability.deep.Sculker;
import com.mercuriusxeno.goo.ability.end.EndWayBack;
import com.mercuriusxeno.goo.ability.frost.Frozen;
import com.mercuriusxeno.goo.ability.gluttony.Gluttony;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.kinetic.GrabHold;
import com.mercuriusxeno.goo.ability.kinetic.GrabItemLock;
import com.mercuriusxeno.goo.ability.kinetic.Grabbed;
import com.mercuriusxeno.goo.ability.nether.Undead;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.program.EntityCounters;
import com.mercuriusxeno.goo.ability.program.GougeWindUp;
import com.mercuriusxeno.goo.ability.program.Lux;
import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.ability.pulse.Stunned;
import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import com.mercuriusxeno.goo.ability.reserve.Reserve;
import com.mercuriusxeno.goo.ability.revert.Reverting;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mercuriusxeno.goo.ability.seal.Elude;
import com.mercuriusxeno.goo.ability.seal.SealCurse;
import com.mercuriusxeno.goo.ability.spray.Spored;
import com.mercuriusxeno.goo.ability.typhoon.Airborn;
import com.mercuriusxeno.goo.ability.typhoon.Breath;
import com.mercuriusxeno.goo.ability.typhoon.Floating;
import com.mercuriusxeno.goo.ability.weird.Wobbled;
import com.mercuriusxeno.goo.ability.xeno.Eldritch;
import com.mercuriusxeno.goo.ability.yarn.Downed;
import com.mercuriusxeno.goo.ability.yarn.Herd;
import com.mercuriusxeno.goo.ability.yarn.Led;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.data.PalmBuffers;
import com.mercuriusxeno.goo.item.SoulBoundStacks;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

/**
 * Registers the data attachments the mod keeps on game objects.
 */
public final class GooAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Goo.MODID);

    /**
     * The soul-bound stacks a dead player holds until respawn, serialized so a
     * player who leaves before respawning keeps them (decision
     * exorite-kept-through-death).
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SoulBoundStacks>> SOUL_BOUND_STACKS =
            ATTACHMENT_TYPES.register("soul_bound_stacks",
                    () -> AttachmentType.builder(() -> SoulBoundStacks.NONE).serialize(SoulBoundStacks.CODEC).build());

    /**
     * The counters a struck entity keeps between goo hits, saved with the
     * entity (decision rewind-fills-while-held).
     */
    public static final Supplier<AttachmentType<EntityCounters>> ENTITY_COUNTERS =
            ATTACHMENT_TYPES.register("entity_counters",
                    () -> AttachmentType.builder(() -> EntityCounters.EMPTY)
                            .serialize(EntityCounters.CODEC)
                            .build());

    /**
     * The players who applied the lingering goo effects an afflicted entity holds, one per effect, saved with the
     * entity while any stands so each later tick names its applier after a reload.
     * lingering-goo-effect-credits-whoever-applied-it
     */
    public static final Supplier<AttachmentType<LingeringAppliers>> LINGERING_APPLIERS =
            ATTACHMENT_TYPES.register("lingering_appliers",
                    () -> AttachmentType.builder(() -> LingeringAppliers.NONE)
                            .serialize(LingeringAppliers.CODEC, LingeringAppliers::stands)
                            .build());

    /**
     * The heart overlay a heart effect lays over a player's health bar, synced to
     * the owning client for the HUD and left behind at death (decision
     * overlay-hearts-are-an-elemental-overshield).
     */
    public static final Supplier<AttachmentType<HeartOverlay>> HEART_OVERLAY =
            ATTACHMENT_TYPES.register("heart_overlay",
                    () -> AttachmentType.builder(() -> HeartOverlay.NONE)
                            .serialize(HeartOverlay.CODEC, HeartOverlay::stands)
                            .sync(GooAttachments::syncsToOwner, HeartOverlay.STREAM_CODEC)
                            .build());

    /**
     * The reserve Reserve banks behind a player's health and hunger bars, apart
     * from any heart overlay, synced to the owning client for the HUD
     * (decisions reserve-channels-on-jelly and reserve-hearts-sit-behind-the-bar).
     */
    public static final Supplier<AttachmentType<Reserve>> RESERVE =
            ATTACHMENT_TYPES.register("reserve",
                    () -> AttachmentType.builder(() -> Reserve.NONE)
                            .serialize(Reserve.CODEC, Reserve::stands)
                            .sync(GooAttachments::syncsToOwner, Reserve.STREAM_CODEC)
                            .build());

    /**
     * The Gouge wind-up the server runs for a player, synced to its
     * owner so the client's ground shake follows every wind-up the server runs;
     * never saved, since a wind-up lives only inside a hold.
     * decision terrakinesis-shakes-the-blocks-before-the-tear
     */
    public static final Supplier<AttachmentType<GougeWindUp>> GOUGE_WIND_UP =
            ATTACHMENT_TYPES.register("gouge_wind_up",
                    () -> AttachmentType.builder(() -> GougeWindUp.NONE)
                            .sync(GooAttachments::syncsToOwner, GougeWindUp.STREAM_CODEC)
                            .build());

    /**
     * The gluttony Gluttony leaves on a player until it expires, with the
     * overheal and overhunger it banks, saved with the player while it stands
     * and synced to its owner, whose client lets a full glutton start eating.
     * gluttony-overheals-and-overhungers
     */
    public static final Supplier<AttachmentType<Gluttony>> GLUTTONY =
            ATTACHMENT_TYPES.register("gluttony",
                    () -> AttachmentType.builder(() -> Gluttony.NONE)
                            .serialize(Gluttony.CODEC, Gluttony::stands)
                            .sync(GooAttachments::syncsToOwner, Gluttony.STREAM_CODEC)
                            .build());

    /**
     * The durative effects a player holds on the glove, each paying its
     * upkeep every tick until ended, saved with the player while any stands
     * and synced to the owning client.
     * self-effects-trickle-until-ended
     */
    public static final Supplier<AttachmentType<HeldEffects>> HELD_EFFECTS =
            ATTACHMENT_TYPES.register("held_effects",
                    () -> AttachmentType.builder(() -> HeldEffects.NONE)
                            .serialize(HeldEffects.CODEC, held -> !held.isEmpty())
                            .sync(GooAttachments::syncsToOwner, HeldEffects.STREAM_CODEC)
                            .build());

    /**
     * The items a player knows, saved with the player and kept through death
     * (decision knowledge-capability-remembers-destroyed-items).
     */
    public static final Supplier<AttachmentType<KnownItems>> KNOWN_ITEMS =
            ATTACHMENT_TYPES.register("known_items",
                    () -> AttachmentType.builder(() -> KnownItems.NONE)
                            .serialize(KnownItems.CODEC)
                            .copyOnDeath()
                            .build());

    /**
     * A player's palm blob buffers, one per ability that stacks what it
     * throws, saved with the player and kept through death, so the blob
     * remembers how full it was.
     * decision each-ability-has-its-own-buffer-that-fills-only-while-held
     */
    public static final Supplier<AttachmentType<PalmBuffers>> PALM_BUFFERS =
            ATTACHMENT_TYPES.register("palm_buffers",
                    () -> AttachmentType.builder(() -> PalmBuffers.NONE)
                            .serialize(PalmBuffers.CODEC, buffers -> !buffers.fills().isEmpty())
                            .copyOnDeath()
                            .build());

    /**
     * A player's remainders, each goo type's part under 1 mB that a payout
     * leaves and ability costs draw, saved with the player and kept through death.
     * decision every-producer-consumer-and-container-holds-a-remainder-buffer
     */
    public static final Supplier<AttachmentType<GooRemainders>> REMAINDERS =
            ATTACHMENT_TYPES.register("remainders",
                    () -> AttachmentType.builder(() -> GooRemainders.EMPTY)
                            .serialize(GooRemainders.CODEC.fieldOf("remainders"), remainders -> !remainders.isEmpty())
                            .copyOnDeath()
                            .sync(GooAttachments::syncsToOwner, GooRemainders.STREAM_CODEC)
                            .build());

    /**
     * Whether a goo:pocket chunk holds its voidstone floor, saved with the
     * chunk so the floor is laid into a chunk once, and a chunk a save
     * generated before the floor gains it on its first load (decision
     * voidstone-floor-stands-whole-and-growth-moves-the-boundary).
     */
    public static final Supplier<AttachmentType<Boolean>> POCKET_FLOOR_LAID =
            ATTACHMENT_TYPES.register("pocket_floor_laid",
                    () -> AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL.fieldOf("laid"), laid -> laid).build());

    /**
     * The game time Leech goo last slowed an entity, so a thing caught by
     * several cells or by both of a cell's hooks in one tick is slowed once,
     * as soul sand slows it once a move; never saved.
     * leech-goo-slows-the-way-soul-sand-does
     */
    public static final Supplier<AttachmentType<Long>> LEECH_SLOWED_AT =
            ATTACHMENT_TYPES.register("leech_slowed_at", () -> AttachmentType.builder(() -> -1L).build());

    /**
     * The game time an entity last touched Nectar goo, so a thing touching
     * several cells or both of a cell's hooks in one tick is slowed once,
     * and its next jump knows it stood in the goo; never saved.
     * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
     */
    public static final Supplier<AttachmentType<Long>> NECTAR_TOUCHED_AT =
            ATTACHMENT_TYPES.register("nectar_touched_at", () -> AttachmentType.builder(() -> -1L).build());

    /**
     * The game time an entity was last stuck on a Nectar coated wall, its
     * gravity off, so the tick after it leaves the coat gives its gravity
     * back; never saved.
     * operator UAT 2026-11-17: on Nectar you are temporarily Spider-Man; gravity is off while you are stuck
     */
    public static final Supplier<AttachmentType<Long>> NECTAR_STUCK_AT =
            ATTACHMENT_TYPES.register("nectar_stuck_at", () -> AttachmentType.builder(() -> -1L).build());

    /**
     * The game time Spikes goo last struck an entity, so a thing walking on
     * it is struck again only once the spikes step's cooldown has passed,
     * however many cells it crosses; never saved.
     * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
     */
    public static final Supplier<AttachmentType<Long>> SPIKES_STRUCK_AT =
            ATTACHMENT_TYPES.register("spikes_struck_at", () -> AttachmentType.builder(() -> -1L).build());

    /**
     * Where an entity stood before its latest goo teleport, kept for the
     * steps after the teleport in the same program, a ghost trail among
     * them, and never saved (decision ghost-trail-spans-the-blink).
     */
    public static final Supplier<AttachmentType<Vec3>> JUMP_SOURCE =
            ATTACHMENT_TYPES.register("jump_source", () -> AttachmentType.builder(() -> Vec3.ZERO).build());

    /**
     * The Lux a player holds, keeping night vision up and glistening the mob
     * under the crosshair while it stands (decision lux-night-vision-without-particles).
     */
    public static final Supplier<AttachmentType<Lux>> LUX =
            ATTACHMENT_TYPES.register("lux",
                    () -> AttachmentType.builder(() -> Lux.NONE).serialize(Lux.CODEC).build());

    /**
     * The eldritch state a player holds, synced to the owning client, which
     * draws the out-of-phase while it stands.
     * eldritch-sight-reveals-the-out-of-phase
     */
    public static final Supplier<AttachmentType<Eldritch>> ELDRITCH =
            ATTACHMENT_TYPES.register("eldritch",
                    () -> AttachmentType.builder(() -> Eldritch.NONE)
                            .serialize(Eldritch.CODEC)
                            .sync(GooAttachments::syncsToOwner, Eldritch.STREAM_CODEC)
                            .build());


    /**
     * The spores a mob carries, bursting another spray from its corpse when
     * it dies before they fade, saved with the mob (decision
     * mycosis-spore-stream-buds-and-poisons).
     */
    public static final Supplier<AttachmentType<Spored>> SPORED =
            ATTACHMENT_TYPES.register("spored",
                    () -> AttachmentType.builder(() -> Spored.NONE).serialize(Spored.CODEC).build());

    /**
     * Whether a player counts as undead and what the sun deals it, laid by
     * Undead and cleared when its held effect ends
     * (decision undead-nether-hearts-burn-in-sunlight).
     */
    public static final Supplier<AttachmentType<Undead>> UNDEAD =
            ATTACHMENT_TYPES.register("undead",
                    () -> AttachmentType.builder(() -> Undead.NONE)
                            .serialize(Undead.CODEC, Undead::stands)
                            .build());

    /**
     * The fixation Lunar holds a caster in, synced to the caster's client, which holds the look and brings
     * the moon or the star to the caster; a fixation an unload interrupts is dropped, and the caster stays where
     * they stood.
     * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
     */
    public static final Supplier<AttachmentType<LunarFixation>> LUNAR_FIXATION =
            ATTACHMENT_TYPES.register("lunar_fixation",
                    () -> AttachmentType.builder(() -> LunarFixation.NONE)
                            .sync(GooAttachments::syncsToOwner, LunarFixation.STREAM_CODEC)
                            .build());

    /**
     * The mark Lunar leaves on a caster it carried, once they stand at the far end, synced to the caster's
     * client, whose white flood holds over the crossing until the mark arrives and then clears; never saved.
     * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
     */
    public static final Supplier<AttachmentType<LunarArrival>> LUNAR_ARRIVAL =
            ATTACHMENT_TYPES.register("lunar_arrival",
                    () -> AttachmentType.builder(() -> LunarArrival.NONE)
                            .sync(GooAttachments::syncsToOwner, LunarArrival.STREAM_CODEC)
                            .build());

    /**
     * Where a caster left the overworld from when the moon took them, saved with the player until the blue planet
     * carries them back.
     * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
     */
    public static final Supplier<AttachmentType<WayHome>> WAY_HOME =
            ATTACHMENT_TYPES.register("way_home",
                    () -> AttachmentType.builder(() -> WayHome.NONE).serialize(WayHome.CODEC, WayHome::kept).build());

    /**
     * A Zap stun's wake, saved with the mob so the stun ends after an unload
     * (decision zap-ticks-the-device-and-stuns).
     */
    public static final Supplier<AttachmentType<Stunned>> STUNNED =
            ATTACHMENT_TYPES.register("stunned",
                    () -> AttachmentType.builder(() -> Stunned.NONE).serialize(Stunned.CODEC).build());

    /**
     * The fungal sight a player holds, lengthening Transmit and synced to
     * the owning client, which outlines fungus through walls while it stands
     * (decision sight-lengthens-shift-and-outlines-fungus).
     */
    public static final Supplier<AttachmentType<Sight>> SIGHT =
            ATTACHMENT_TYPES.register("sight",
                    () -> AttachmentType.builder(() -> Sight.NONE)
                            .serialize(Sight.CODEC)
                            .sync(GooAttachments::syncsToOwner, Sight.STREAM_CODEC)
                            .build());

    /**
     * A mob's petrify gauge and whether it stands a statue, saved with the mob
     * and synced to every client drawing it, which freezes a statue's pose
     * (decision petrify-stone-encasement-and-calcify-map).
     */
    public static final Supplier<AttachmentType<Petrification>> PETRIFICATION =
            ATTACHMENT_TYPES.register("petrification",
                    () -> AttachmentType.builder(() -> Petrification.NONE)
                            .serialize(Petrification.CODEC, Petrification::started)
                            .sync(GooAttachments::syncsToWatcher, Petrification.STREAM_CODEC)
                            .build());

    /**
     * A mob Revert's stream holds: frozen until shortly after the stream lets
     * go, or shrinking into its egg, saved with the mob so a reload mid-hold
     * still frees it.
     * rewind-fills-while-held
     */
    public static final Supplier<AttachmentType<Reverting>> REVERTING =
            ATTACHMENT_TYPES.register("reverting",
                    () -> AttachmentType.builder(() -> Reverting.NONE).serialize(Reverting.CODEC).build());

    /**
     * An entity Grab holds: suspended until shortly after the channel lets
     * go, saved with the entity so a reload mid-hold still frees it.
     * grab-holds-and-throws-a-physics-body
     */
    public static final Supplier<AttachmentType<Grabbed>> GRABBED =
            ATTACHMENT_TYPES.register("grabbed",
                    () -> AttachmentType.builder(() -> Grabbed.NONE).serialize(Grabbed.CODEC).build());

    /**
     * What a player's Grab holds, server side for the length of the hold.
     * grab-holds-and-throws-a-physics-body
     */
    public static final Supplier<AttachmentType<GrabHold>> GRAB_HOLD =
            ATTACHMENT_TYPES.register("grab_hold", () -> AttachmentType.builder(() -> GrabHold.NONE).build());

    /**
     * The item a player's Grab press locked, server side for the length of the press.
     * grabbed-item-locks-in-and-homes-on-your-current-position
     */
    public static final Supplier<AttachmentType<GrabItemLock>> GRAB_ITEM_LOCK =
            ATTACHMENT_TYPES.register("grab_item_lock",
                    () -> AttachmentType.builder(() -> GrabItemLock.NONE).build());

    /**
     * The warp curse Seal leaves on a mob, saved with the mob while
     * it stands.
     * zone-curses-with-ender-shimmer
     */
    public static final Supplier<AttachmentType<SealCurse>> SEAL_CURSE =
            ATTACHMENT_TYPES.register("seal_curse",
                    () -> AttachmentType.builder(() -> SealCurse.NONE)
                            .serialize(SealCurse.CODEC, SealCurse::stands)
                            .build());

    /**
     * The Elude a player holds, saved with the player.
     * elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage
     */
    public static final Supplier<AttachmentType<Elude>> ELUDE =
            ATTACHMENT_TYPES.register("elude",
                    () -> AttachmentType.builder(() -> Elude.NONE)
                            .serialize(Elude.CODEC)
                            .build());

    /**
     * The vines rooting a mob, saved with it while they stand and synced to
     * every client drawing it, which draws the tangle over its model and the
     * tendrils down to the root.
     * vines-unpack-root-and-thorn
     */
    public static final Supplier<AttachmentType<Rooted>> ROOTED =
            ATTACHMENT_TYPES.register("rooted",
                    () -> AttachmentType.builder(() -> Rooted.NONE)
                            .serialize(Rooted.CODEC, Rooted::stands)
                            .sync(GooAttachments::syncsToWatcher, Rooted.STREAM_CODEC)
                            .build());

    /**
     * The lead a peaceful mob holds until its leader strikes it: the caster
     * it fights for and its strike damage, saved with the mob and synced to
     * every client drawing it, which floats the lead fist over its head and
     * outlines it.
     * zoo-rally-arms-the-peaceful
     * rallied-mobs-wear-a-red-clenched-fist
     */
    public static final Supplier<AttachmentType<Led>> LED =
            ATTACHMENT_TYPES.register("led",
                    () -> AttachmentType.builder(() -> Led.NONE)
                            .serialize(Led.CODEC)
                            .sync(GooAttachments::syncsToWatcher, Led.STREAM_CODEC)
                            .build());

    /**
     * The fall a led mob takes instead of dying, the game time it went
     * down, saved with the mob so its timers survive a reload and synced to
     * every client drawing it, which lays it on its side. A lead ending
     * leaves it standing.
     * rallied-mob-falls-downed-instead-of-dying
     */
    public static final Supplier<AttachmentType<Downed>> DOWNED =
            ATTACHMENT_TYPES.register("downed",
                    () -> AttachmentType.builder(() -> Downed.NONE)
                            .serialize(Downed.CODEC)
                            .sync(GooAttachments::syncsToWatcher, Downed.STREAM_CODEC)
                            .build());

    /**
     * The charm a mob holds, the player it fights for, saved with the mob
     * and synced to every client drawing it, which floats the charmed heart
     * over its head.
     * charm-glisten-and-icon-over-the-head
     */
    public static final Supplier<AttachmentType<Charmed>> CHARMED =
            ATTACHMENT_TYPES.register("charmed",
                    () -> AttachmentType.builder(() -> Charmed.NONE)
                            .serialize(Charmed.CODEC)
                            .sync(GooAttachments::syncsToWatcher, Charmed.STREAM_CODEC)
                            .build());

    /**
     * The wobble a mob holds and when it fades, saved with the mob while it
     * stands; its attacks knock back rather than harm.
     * weird-bounces-and-softens-harm
     */
    public static final Supplier<AttachmentType<Wobbled>> WOBBLED =
            ATTACHMENT_TYPES.register("wobbled",
                    () -> AttachmentType.builder(() -> Wobbled.NONE)
                            .serialize(Wobbled.CODEC, wobbled -> wobbled.expiresAt() > 0L)
                            .build());

    /**
     * A mob's frozen gauge, saved with the mob and synced to every client
     * drawing it, which spreads frost over it and holds its pose at full
     * (decision frozen-gauge-per-mob-encases-when-full).
     */
    public static final Supplier<AttachmentType<Frozen>> FROZEN =
            ATTACHMENT_TYPES.register("frozen",
                    () -> AttachmentType.builder(() -> Frozen.NONE)
                            .serialize(Frozen.CODEC, Frozen::started)
                            .sync(GooAttachments::syncsToWatcher, Frozen.STREAM_CODEC)
                            .build());

    /**
     * An entity's standing out of phase and when it fades, saved with the
     * entity while laid and synced to every client drawing it, which draws it
     * translucent and grey. A plain attachment any ability may lay, which
     * Unveil reads too (decision eldritch-sight-reveals-the-out-of-phase).
     * phase-shares-a-plane-between-the-phased
     */
    public static final Supplier<AttachmentType<OutOfPhase>> OUT_OF_PHASE =
            ATTACHMENT_TYPES.register("out_of_phase",
                    () -> AttachmentType.builder(() -> OutOfPhase.NONE)
                            .serialize(OutOfPhase.CODEC, OutOfPhase::laid)
                            .sync(GooAttachments::syncsToWatcher, OutOfPhase.STREAM_CODEC)
                            .build());

    /**
     * The float Float leaves on a mob while its levitation lasts, saved with
     * the mob and synced to every client drawing it, which pulses the mint
     * platform under its feet.
     * float-blob-levitates-the-mob
     */
    public static final Supplier<AttachmentType<Floating>> FLOATING =
            ATTACHMENT_TYPES.register("floating",
                    () -> AttachmentType.builder(() -> Floating.NONE)
                            .serialize(Floating.CODEC)
                            .sync(GooAttachments::syncsToWatcher, Floating.STREAM_CODEC)
                            .build());

    /**
     * The air control Float lays on a player while held, saved with the
     * player while it stands and synced to the owning client, which steers it
     * in midair, caps its fall and draws the rising wind.
     * tailwind-and-airborn-merge-into-float
     */
    public static final Supplier<AttachmentType<Airborn>> AIRBORN =
            ATTACHMENT_TYPES.register("airborn",
                    () -> AttachmentType.builder(() -> Airborn.NONE)
                            .serialize(Airborn.CODEC, airborn -> airborn.expiresAt() > 0L)
                            .sync(GooAttachments::syncsToOwner, Airborn.STREAM_CODEC)
                            .build());
    /**
     * The Sculker a player holds, saved while it stands and synced to the
     * owning client, which swims it through solid blocks and draws the
     * deep's vision.
     * dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
     */
    public static final Supplier<AttachmentType<Sculker>> SCULKER =
            ATTACHMENT_TYPES.register("sculker",
                    () -> AttachmentType.builder(() -> Sculker.NONE)
                            .serialize(Sculker.CODEC, sculker -> sculker.expiresAt() > 0L)
                            .sync(GooAttachments::syncsToOwner, Sculker.STREAM_CODEC)
                            .build());

    /**
     * The Herd a player holds, saved while it stands and synced to the
     * owning client, which outlines the mobs it senses.
     * leader-is-zoos-brew-ability
     */
    public static final Supplier<AttachmentType<Herd>> HERD =
            ATTACHMENT_TYPES.register("herd",
                    () -> AttachmentType.builder(() -> Herd.NONE)
                            .serialize(Herd.CODEC, Herd::stands)
                            .sync(GooAttachments::syncsToOwner, Herd.STREAM_CODEC)
                            .build());

    /**
     * The air Pneuma banks past a player's vanilla air meter and its airless
     * immunity, saved with the player and synced to the owning client, which
     * draws the reserve above the air bubbles.
     * pneuma-overfills-the-air-meter-with-a-brief-airless-immunity
     */
    public static final Supplier<AttachmentType<Breath>> BREATH =
            ATTACHMENT_TYPES.register("breath",
                    () -> AttachmentType.builder(() -> Breath.NONE)
                            .serialize(Breath.CODEC, Breath::stands)
                            .sync(GooAttachments::syncsToOwner, Breath.STREAM_CODEC)
                            .build());
    /**
     * Where a player the End gate carried to the End portalled in from,
     * saved with the player so the gate they summon there leads back, and
     * synced to them, whose client grays End out in the End without it.
     * decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in
     * decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability
     */
    public static final Supplier<AttachmentType<EndWayBack>> END_WAY_BACK =
            ATTACHMENT_TYPES.register("end_way_back",
                    () -> AttachmentType.builder(() -> EndWayBack.NONE)
                            .serialize(EndWayBack.CODEC.fieldOf("end_way_back"), EndWayBack::stands)
                            .sync(GooAttachments::syncsToOwner, EndWayBack.STREAM_CODEC)
                            .build());

    private GooAttachments() {
    }

    /**
     * Answers whether an entity attachment syncs to a watching client: only
     * over a connection that negotiated the attachment sync channel, which a
     * vanilla client and a gametest mock player lack.
     *
     * @param holder the holder of the attachment
     * @param to     the player the sync would reach
     * @return true when the sync goes out
     */
    private static boolean syncsToWatcher(IAttachmentHolder holder, ServerPlayer to) {
        return to.connection.hasChannel(SyncAttachmentsPayload.TYPE);
    }

    /**
     * Answers whether a player attachment syncs to a client: only to the
     * player holding it, and only over a connection that negotiated the
     * attachment sync channel, which a vanilla client and a gametest mock
     * player lack.
     *
     * @param holder the holder of the attachment
     * @param to     the player the sync would reach
     * @return true when the sync goes out
     */
    private static boolean syncsToOwner(IAttachmentHolder holder, ServerPlayer to) {
        return holder == to && to.connection.hasChannel(SyncAttachmentsPayload.TYPE);
    }
}
