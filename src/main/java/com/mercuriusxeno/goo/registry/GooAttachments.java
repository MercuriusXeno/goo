package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.frost.Frozen;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.hex.Lifetap;
import com.mercuriusxeno.goo.ability.nourish.Nourish;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.program.EntityCounters;
import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.ability.spray.Spored;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.item.SoulBoundStacks;
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
     * The spores a mob carries, bursting another spray from its corpse when
     * it dies before they fade, saved with the mob (decision
     * mycosis-spore-stream-buds-and-poisons).
     */
    public static final Supplier<AttachmentType<Spored>> SPORED =
            ATTACHMENT_TYPES.register("spored",
                    () -> AttachmentType.builder(() -> Spored.NONE).serialize(Spored.CODEC).build());

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
