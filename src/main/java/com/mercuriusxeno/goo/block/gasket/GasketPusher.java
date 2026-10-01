package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.block.canister.CanisterSlot;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pushes goo from a reservoir to a gasket partner on a fixed interval.
 * Owns the push timer and endpoint cache lifecycle.
 */
public class GasketPusher {

    /**
     * Release the forced chunk ticket after this many ticks with no transfer.
     */
    static final int IDLE_THRESHOLD = 200;

    private final ResourceHandler<FluidResource> source;
    private final Supplier<@Nullable UUID> gasketId;
    private final Supplier<@Nullable GasketPartner> partner;
    private final Supplier<@Nullable Level> level;
    private final Supplier<BlockPos> ownerPos;
    private final Runnable sync;
    private final Supplier<GasketRegistry> registryAccess;

    private int idleTicks;
    /** The source slot the next push walk starts at. */
    private int nextTurn;
    private @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, UUID> endpointCache;
    private @Nullable ChunkPos forcedChunk;
    /** The block the endpoint cache was built on, or null when none stands. */
    private @Nullable BlockPos targetPos;

    /**
     * Creates a gasket pusher wired to the host entity's state.
     *
     * @param source         the fluid handler to push from (any fluid, not just goo)
     * @param gasketId       supplier for the host's gasket UUID
     * @param partner        supplier for the host's gasket partner
     * @param level          supplier for the host's level (null before setLevel)
     * @param ownerPos       supplier for the host block entity's position (ticket owner)
     * @param sync           callback to sync the host to clients after a push
     * @param registryAccess decoupled access to the gasket registry (avoids direct GasketRegistry.get calls)
     */
    public GasketPusher(ResourceHandler<FluidResource> source,
                        Supplier<@Nullable UUID> gasketId,
                        Supplier<@Nullable GasketPartner> partner,
                        Supplier<@Nullable Level> level,
                        Supplier<BlockPos> ownerPos,
                        Runnable sync,
                        Supplier<GasketRegistry> registryAccess) {
        this.source = source;
        this.gasketId = gasketId;
        this.partner = partner;
        this.level = level;
        this.ownerPos = ownerPos;
        this.sync = sync;
        this.registryAccess = registryAccess;
    }

    /**
     * Forces the chunk of a receiver gasket's transmitter partner.
     * Called on block entity load so the transmitter wakes up and can push.
     * Keeps ServerLevel for dimension checks and chunk forcing, but registry
     * access is decoupled through the interface.
     *
     * @param receiverGasketId the receiver gasket UUID
     * @param registryAccess   decoupled access to the gasket registry
     * @param serverLevel      the server level (for dimension + chunk forcing)
     * @param ownerPos         the receiver block entity's position (ticket owner)
     */
    public static void forceTransmitterChunk(
            @Nullable UUID receiverGasketId,
            Supplier<GasketRegistry> registryAccess,
            ServerLevel serverLevel,
            BlockPos ownerPos) {
        GasketLocation loc = resolveTransmitterLocation(receiverGasketId, registryAccess);
        if (loc == null || !loc.dimension().equals(serverLevel.dimension())) {
            return;
        }
        ChunkPos cp = ChunkPos.containing(loc.pos());
        GooTickets.gasketChunks.forceChunk(
                serverLevel, ownerPos, cp.x(), cp.z(), true, false);
    }

    /**
     * Forces the chunk of the transmitter partner of each occupied slot's top
     * (receiver) gasket, the walk every slotted holder runs after load.
     *
     * @param slots          the holder's canister slots
     * @param registryAccess decoupled access to the gasket registry
     * @param serverLevel    the server level (for dimension + chunk forcing)
     * @param ownerPos       the holder's position (ticket owner)
     */
    public static void forceSlotTransmitterChunks(
            CanisterSlot[] slots,
            Supplier<GasketRegistry> registryAccess,
            ServerLevel serverLevel,
            BlockPos ownerPos) {
        for (CanisterSlot slot : slots) {
            if (!slot.isEmpty()) {
                forceTransmitterChunk(CanisterItem.getMetadata(slot.canister()).topGasketId(),
                        registryAccess, serverLevel, ownerPos);
            }
        }
    }

    /**
     * Resolves the transmitter's location from a receiver gasket UUID, or null if unlinked.
     *
     * @param receiverGasketId the receiver gasket UUID
     * @param registryAccess   decoupled access to the gasket registry
     * @return the transmitter location, or null
     */
    private static @Nullable GasketLocation resolveTransmitterLocation(
            @Nullable UUID receiverGasketId, Supplier<GasketRegistry> registryAccess) {
        if (receiverGasketId == null) {
            return null;
        }
        GasketRegistry registry = registryAccess.get();
        UUID sourceId = registry.getSource(receiverGasketId);
        if (sourceId == null) {
            return null;
        }
        GasketLocation loc = registry.getLocation(sourceId);
        if (loc == null || loc.isEntityTarget()) {
            return null;
        }
        return loc;
    }

    /**
     * Pushes goo to the partner if a target exists, otherwise tracks idle time.
     */
    public void tick() {
        followTargetLocation();
        if (!hasPushableTarget()) {
            trackIdle();
            return;
        }
        ensureChunkForced();
        pushToDestinations();
    }

    /**
     * Releases the forced chunk ticket and clears the endpoint cache.
     */
    public void dispose() {
        unforceChunk();
        endpointCache = null;
        targetPos = null;
    }

    /**
     * Rebuilds the BlockCapabilityCache for the current partner, forcing the target chunk.
     */
    public void rebuildCache() {
        unforceChunk();
        endpointCache = null;
        targetPos = null;
        BlockPos pos = currentTargetPos();
        UUID targetGasketId = pos == null ? null : resolveTargetGasketId();
        if (targetGasketId != null) {
            buildBlockCache(pos, targetGasketId);
        }
    }

    /**
     * Rebuilds the cache once the target gasket's registry location moved, so a
     * receiving canister carried to another slot or host keeps receiving with no
     * re-link (decision diagnose-then-fix-capability-lifetimes).
     */
    private void followTargetLocation() {
        if (!Objects.equals(currentTargetPos(), targetPos)) {
            rebuildCache();
        }
    }

    /**
     * Where the target gasket stands now: the registry's location for it, the one
     * the slot grid moves when its canister changes host. A gasket the registry
     * holds no location for answers the position the tuner stored on the partner.
     *
     * @return the target block position, or null when no block in this level can be pushed to
     */
    @Nullable BlockPos currentTargetPos() {
        if (!canBuildCache()) {
            return null;
        }
        GasketPartner p = partner.get();
        UUID targetGasketId = resolveTargetGasketId();
        if (p == null || p.isEntityTarget() || targetGasketId == null) {
            return null;
        }
        return blockPosIn(registryAccess.get().getLocation(targetGasketId), p);
    }

    /**
     * The block position a registry location names in this pusher's level.
     *
     * @param loc the target gasket's registry location, or null when it holds none
     * @param p   the partner the tuner stored, answered when the registry holds no location
     * @return the position, or null when the location is an entity or another dimension
     */
    private @Nullable BlockPos blockPosIn(@Nullable GasketLocation loc, GasketPartner p) {
        if (loc == null) {
            return p.pos();
        }
        boolean blockHere = !loc.isEntityTarget() && loc.dimension().equals(level.get().dimension());
        return blockHere ? loc.pos() : null;
    }

    /**
     * Forces the target's chunk and creates a BlockCapabilityCache for the target block.
     *
     * @param pos            the target block's position
     * @param targetGasketId the resolved gasket UUID on the partner side
     */
    private void buildBlockCache(BlockPos pos, UUID targetGasketId) {
        ServerLevel serverLevel = (ServerLevel) level.get();
        ChunkPos cp = ChunkPos.containing(pos);
        GooTickets.gasketChunks.forceChunk(
                serverLevel, ownerPos.get(), cp.x(), cp.z(), true, false);
        forcedChunk = cp;
        targetPos = pos;
        endpointCache = BlockCapabilityCache.create(
                GooCapabilities.GASKET_BLOCK, serverLevel, pos, targetGasketId);
    }

    /**
     * Increments idle counter and releases the chunk ticket when the threshold is reached.
     */
    private void trackIdle() {
        idleTicks++;
        if (idleTicks >= IDLE_THRESHOLD && forcedChunk != null) {
            unforceChunk();
        }
    }

    /**
     * Re-forces the target chunk if the ticket was released due to idle.
     */
    private void ensureChunkForced() {
        if (forcedChunk != null || targetPos == null) {
            return;
        }
        if (!(level.get() instanceof ServerLevel serverLevel)) {
            return;
        }
        forcedChunk = ChunkPos.containing(targetPos);
        GooTickets.gasketChunks.forceChunk(
                serverLevel, ownerPos.get(), forcedChunk.x(), forcedChunk.z(), true, false);
    }

    /**
     * Returns true when the reservoir has goo and a valid push target is configured.
     *
     * @return true if pushable target
     */
    private boolean hasPushableTarget() {
        GasketPartner p = partner.get();
        if (isSourceEmpty() || p == null) {
            return false;
        }
        return p.isEntityTarget() || endpointCache != null;
    }

    /**
     * Returns true if the source handler has no fluid in any slot.
     *
     * @return true if all slots are empty
     */
    private boolean isSourceEmpty() {
        for (int i = 0; i < source.size(); i++) {
            if (!source.getResource(i).isEmpty() && source.getAmountAsLong(i) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true when the cache can be built (server-side with a partner).
     *
     * @return true if build cache
     */
    private boolean canBuildCache() {
        Level lvl = level.get();
        return lvl != null && !lvl.isClientSide() && partner.get() != null;
    }

    /**
     * Releases the forced chunk ticket, if one is held.
     */
    private void unforceChunk() {
        if (forcedChunk == null) {
            return;
        }
        Level lvl = level.get();
        if (lvl instanceof ServerLevel serverLevel) {
            GooTickets.gasketChunks.forceChunk(
                    serverLevel, ownerPos.get(), forcedChunk.x(), forcedChunk.z(), false, false);
        }
        forcedChunk = null;
    }

    /**
     * Pushes to the partner's handler when the source holds anything.
     */
    private void pushToDestinations() {
        if (isSourceEmpty()) {
            return;
        }
        ResourceHandler<FluidResource> handler = resolveTarget();
        if (handler != null) {
            pushViaHandler(handler);
        }
    }

    /**
     * The demand the partner states for a resource, the one the link this pusher
     * stands on relays upstream (decision receivers-demand-and-links-relay).
     *
     * @param resource the fluid the link would receive
     * @return the partner's stated demand, or empty when it states none or is unreachable
     */
    public OptionalInt partnerStatedDemand(FluidResource resource) {
        return GasketDemand.statedDemandOf(resolveTarget(), resource);
    }

    /**
     * The partner's fluid handler: through the capability cache for a block, or the
     * player's GASKET_ENTITY for an entity.
     *
     * @return the handler, or null when the partner is unset or unreachable
     */
    private @Nullable ResourceHandler<FluidResource> resolveTarget() {
        GasketPartner p = partner.get();
        if (p == null) {
            return null;
        }
        if (!p.isEntityTarget()) {
            return endpointCache == null ? null : endpointCache.getCapability();
        }
        return level.get() instanceof ServerLevel serverLevel ? resolveEntityHandler(serverLevel, p) : null;
    }

    /**
     * Resolves the fluid handler for an entity-based gasket partner, or null if unavailable.
     *
     * @param serverLevel the server level
     * @param p           the entity-based gasket partner
     * @return the fluid handler, or null if the entity or capability is unavailable
     */
    private @Nullable ResourceHandler<FluidResource> resolveEntityHandler(
            ServerLevel serverLevel, GasketPartner p) {
        UUID targetEntityId = p.entityId();
        if (targetEntityId == null) {
            return null;
        }
        UUID targetGasketId = resolveTargetGasketId();
        if (targetGasketId == null) {
            return null;
        }
        Player player = serverLevel.getPlayerByUUID(targetEntityId);
        if (player == null) {
            return null;
        }
        return player.getCapability(GooCapabilities.GASKET_ENTITY, targetGasketId);
    }

    /**
     * Sends each source slot the lesser of the demand the target states and
     * what the slot holds, in a single transaction per slot. The walk starts at the
     * slot after the one that sent first last time and wraps, so a target asking one
     * type at a time receives the source's types by turns, and an empty slot yields
     * its turn to the next (decision vat-round-robins-gasket-send).
     *
     * @param target the destination fluid handler
     */
    void pushViaHandler(ResourceHandler<FluidResource> target) {
        int slots = source.size();
        boolean moved = false;
        int firstSent = nextTurn;
        for (int step = 0; step < slots; step++) {
            int i = (nextTurn + step) % slots;
            if (sendSlot(target, i)) {
                firstSent = moved ? firstSent : i;
                moved = true;
            }
        }
        if (moved) {
            nextTurn = (firstSent + 1) % slots;
            idleTicks = 0;
            sync.run();
        }
    }

    /**
     * Sends one source slot the lesser of the target's demand and what the slot holds.
     *
     * @param target the destination fluid handler
     * @param slot   the source slot
     * @return true if any fluid moved
     */
    private boolean sendSlot(ResourceHandler<FluidResource> target, int slot) {
        FluidResource resource = source.getResource(slot);
        if (resource.isEmpty()) {
            return false;
        }
        int amount = (int) source.getAmountAsLong(slot);
        int offer = Math.min(GasketDemand.demandOf(target, resource), amount);
        return offer > 0 && transferSlot(target, slot, resource, offer);
    }

    /**
     * Transfers up to {@code offer} mB of the given resource from one source
     * slot to the target handler in a single transaction.
     *
     * @param target   the destination handler
     * @param slot     the source slot index
     * @param resource the fluid resource to transfer
     * @param offer    the maximum amount to transfer
     * @return true if any fluid was transferred
     */
    private boolean transferSlot(ResourceHandler<FluidResource> target,
                                 int slot, FluidResource resource, int offer) {
        try (var tx = Transaction.openRoot()) {
            int extracted = source.extract(slot, resource, offer, tx);
            if (extracted <= 0) {
                return false;
            }
            int inserted = target.insert(resource, extracted, tx);
            if (inserted <= 0) {
                return false;
            }
            if (inserted < extracted) {
                source.insert(slot, resource, extracted - inserted, tx);
            }
            tx.commit();
            return true;
        }
    }

    /**
     * Resolves the gasket UUID on the partner side via the injected registry access.
     * Returns the partner's gasket UUID, or null if unlinked.
     *
     * @return the UUID, or null
     */
    private @Nullable UUID resolveTargetGasketId() {
        UUID id = gasketId.get();
        if (id == null) {
            return null;
        }
        return registryAccess.get().getTarget(id);
    }
}
