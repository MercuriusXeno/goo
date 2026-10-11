package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.frost.Frozen;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.hex.Lifetap;
import com.mercuriusxeno.goo.ability.nether.Undead;
import com.mercuriusxeno.goo.ability.nourish.Nourish;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.program.EntityCounters;
import com.mercuriusxeno.goo.ability.program.Lux;
import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.ability.pulse.Stunned;
import com.mercuriusxeno.goo.ability.rewind.Rewinding;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mercuriusxeno.goo.ability.spray.Spored;
import com.mercuriusxeno.goo.ability.typhoon.Airborn;
import com.mercuriusxeno.goo.ability.typhoon.Floating;
import com.mercuriusxeno.goo.ability.weird.Wobbled;
import com.mercuriusxeno.goo.ability.world.TimeVeiled;
import com.mercuriusxeno.goo.ability.zone.Shifter;
import com.mercuriusxeno.goo.ability.zone.ZoneCurse;
import com.mercuriusxeno.goo.data.KnownItems;
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
     * entity (decision aeon-mob-ritual-drops-spawn-egg).
     */
    public static final Supplier<AttachmentType<EntityCounters>> ENTITY_COUNTERS =
            ATTACHMENT_TYPES.register("entity_counters",
                    () -> AttachmentType.builder(() -> EntityCounters.EMPTY)
                            .serialize(EntityCounters.CODEC)
                            .build());

    /**
     * The heart overlay a heart brew lays over a player's health bar, synced to
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
     * The nourishment Nourish leaves on a player, a food point every interval
     * until it expires, saved with the player while it stands.
     * nourish-restores-hunger-over-time
     */
    public static final Supplier<AttachmentType<Nourish>> NOURISH =
            ATTACHMENT_TYPES.register("nourish",
                    () -> AttachmentType.builder(() -> Nourish.NONE)
                            .serialize(Nourish.CODEC, Nourish::stands)
                            .build());

    /**
     * The self + brew effects a player holds on the glove, each paying its
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
     * A Zap stun's wake, saved with the mob so the stun ends after an unload
     * (decision zap-ticks-the-device-and-stuns).
     */
    public static final Supplier<AttachmentType<Stunned>> STUNNED =
            ATTACHMENT_TYPES.register("stunned",
                    () -> AttachmentType.builder(() -> Stunned.NONE).serialize(Stunned.CODEC).build());

    /**
     * The fungal sight a player holds, lengthening Fungal Shift and synced to
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
     * Marks a mob held in stasis until it is struck, saved with the mob so
     * the hold outlasts a reload; a freed mob drops the attachment.
     * stasis-holds-mob-with-golden-shimmer
     */
    public static final Supplier<AttachmentType<Boolean>> STASIS =
            ATTACHMENT_TYPES.register("stasis",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL.fieldOf("held"), Boolean::booleanValue)
                            .build());

    /**
     * A mob Rewind's stream holds: frozen until shortly after the stream lets
     * go, or shrinking into its egg, saved with the mob so a reload mid-hold
     * still frees it.
     * rewind-fills-while-held
     */
    public static final Supplier<AttachmentType<Rewinding>> REWINDING =
            ATTACHMENT_TYPES.register("rewinding",
                    () -> AttachmentType.builder(() -> Rewinding.NONE).serialize(Rewinding.CODEC).build());

    /**
     * A mob a chronosphere's AI pacing holds, saved with the mob so a reload
     * mid-veil hands back the AI state it had before the veil.
     * chronosphere-hastes-players-slows-mobs
     */
    public static final Supplier<AttachmentType<TimeVeiled>> TIME_VEILED =
            ATTACHMENT_TYPES.register("time_veiled",
                    () -> AttachmentType.builder(() -> TimeVeiled.NONE).serialize(TimeVeiled.CODEC).build());

    /**
     * The warp curse Zone leaves on a mob, saved with the mob while
     * it stands.
     * zone-curses-with-ender-shimmer
     */
    public static final Supplier<AttachmentType<ZoneCurse>> ZONE_CURSE =
            ATTACHMENT_TYPES.register("zone_curse",
                    () -> AttachmentType.builder(() -> ZoneCurse.NONE)
                            .serialize(ZoneCurse.CODEC, ZoneCurse::stands)
                            .build());

    /**
     * The shifter a player holds, saved with the player.
     * shifter-blinks-along-the-cursor-on-hit
     */
    public static final Supplier<AttachmentType<Shifter>> SHIFTER =
            ATTACHMENT_TYPES.register("shifter",
                    () -> AttachmentType.builder(() -> Shifter.NONE)
                            .serialize(Shifter.CODEC)
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
     * The charm a mob holds, the player it fights for and when it fades,
     * saved with the mob and synced to every client drawing it, which floats
     * the charmed heart over its head.
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
     * The lifetap a player holds, its leech fraction and when it fades,
     * saved with the player while it stands.
     * lifetap-trades-regen-for-leech
     */
    public static final Supplier<AttachmentType<Lifetap>> LIFETAP =
            ATTACHMENT_TYPES.register("lifetap",
                    () -> AttachmentType.builder(() -> Lifetap.NONE)
                            .serialize(Lifetap.CODEC, lifetap -> lifetap.expiresAt() > 0L)
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
     * The air control Airborn lays on a player, saved with the player while
     * it stands and synced to the owning client, which steers it in midair,
     * caps its fall and draws the rising wind.
     * airborn-steerable-levitation-and-soft-falls
     */
    public static final Supplier<AttachmentType<Airborn>> AIRBORN =
            ATTACHMENT_TYPES.register("airborn",
                    () -> AttachmentType.builder(() -> Airborn.NONE)
                            .serialize(Airborn.CODEC, airborn -> airborn.expiresAt() > 0L)
                            .sync(GooAttachments::syncsToOwner, Airborn.STREAM_CODEC)
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
