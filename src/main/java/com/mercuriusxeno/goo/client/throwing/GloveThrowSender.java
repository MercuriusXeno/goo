package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.StackKey;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.BlobThrowHandler;
import com.mercuriusxeno.goo.network.BlobThrowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Client-only helper that resolves the player's aim target and sends
 * a {@link BlobThrowPayload} to the server. Tracks in-flight blob
 * counts per chain marker so the client can block throws that would
 * exceed max stacks without waiting for server acknowledgement.
 */
public final class GloveThrowSender {

    /**
     * Sentinel value indicating no entity target.
     */
    private static final int NO_ENTITY = -1;

    /**
     * In-flight throws toward chain markers, keyed by block position.
     */
    private static final Map<BlockPos, Integer> IN_FLIGHT = new HashMap<>();

    /**
     * Empty sentinel for an ability the client holds no synced chain block for.
     */
    private static final int[] UNKNOWN_STACKS = new int[0];

    private GloveThrowSender() {
    }

    /**
     * Resolves the current aim target and sends the throw packet for the
     * held glove's selection. Blocks the throw if in-flight blobs would
     * exceed the marker's max stacks, and arms a throw-block freeze when maxed.
     *
     * @param player the local player
     * @return true when a payload was sent, the one press the arm swings for
     */
    public static boolean sendThrow(Player player) {
        GloveSelection selection = heldSelection(player);
        ResourceKey<GooTypeDefinition> gooType = selection == null ? null : selection.getGooType();
        if (gooType == null || ThrowFreezeState.isThrowBlocked()) {
            return false;
        }
        TargetResult target = AimTracker.currentTarget();
        BlobThrowPayload payload = affordablePayload(player, target, gooType, selection.abilityId());
        if (payload == null) {
            return false;
        }
        if (wouldExceedMaxStacks(target, gooType, selection.abilityId())) {
            ThrowFreezeState.armThrowBlock();
            return false;
        }
        ThrowFreezeState.arm(target);
        trackInFlight(target, selection.abilityId());
        sendPayload(payload);
        return true;
    }

    /**
     * Builds the throw payload for the aimed target when the player can afford it.
     *
     * @param player    the local player
     * @param target    the resolved aim target
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @return the payload, or null for no target or an unaffordable throw
     */
    private static @Nullable BlobThrowPayload affordablePayload(Player player, TargetResult target,
            ResourceKey<GooTypeDefinition> gooType, String abilityId) {
        BlobThrowPayload payload = targetToPayload(target, gooType, abilityId, lineOrigin());
        if (payload == null || !affordsThrow(AbilitySyncHandler.findAbility(abilityId), keyedStacksAt(payload),
                amount -> GooSourceScanner.hasEnough(player, gooType, amount))) {
            return null;
        }
        return payload;
    }

    /**
     * Whether the player can afford a throw priced the way the server
     * prices it, checked before any swing, packet or sound
     * (decision unaffordable-click-does-nothing).
     *
     * @param ability        the selected ability's synced copy, or null when none synced
     * @param existingStacks the stacks the payload's target marker already holds
     * @param holdsAtLeast   whether the player holds at least an mB amount of the type
     * @return true when the holdings cover the cost
     */
    static boolean affordsThrow(@Nullable ClientAbility ability, int existingStacks, IntPredicate holdsAtLeast) {
        return holdsAtLeast.test(priceThrow(ability, existingStacks));
    }

    /**
     * Prices a throw the way the server does, falling back to its flat cost
     * for an ability the client holds no synced copy of.
     *
     * @param ability        the selected ability's synced copy, or null when none synced
     * @param existingStacks the stacks the target marker already holds
     * @return the cost in mB
     */
    static int priceThrow(@Nullable ClientAbility ability, int existingStacks) {
        return ability == null ? BlobThrowHandler.THROW_COST : ability.throwCost(existingStacks);
    }

    /**
     * The cost of the held glove's throw at the aimed target, priced as
     * {@link #sendThrow} prices it: the first throw when nothing is aimed at
     * (decision crosshair-panel-shows-source-and-cost).
     *
     * @param player the local player
     * @return the cost in mB, or empty when the glove holds no selection
     */
    public static OptionalInt aimedThrowCost(Player player) {
        GloveSelection selection = heldSelection(player);
        ResourceKey<GooTypeDefinition> gooType = selection == null ? null : selection.getGooType();
        if (gooType == null) {
            return OptionalInt.empty();
        }
        BlobThrowPayload payload = targetToPayload(AimTracker.currentTarget(), gooType, selection.abilityId(),
                lineOrigin());
        int stacks = payload == null ? 0 : keyedStacksAt(payload);
        return OptionalInt.of(priceThrow(AbilitySyncHandler.findAbility(selection.abilityId()), stacks));
    }

    /**
     * Counts the stacks of the payload's target marker the way the server
     * prices a throw: the marker at the target position, keyed to the thrown ability.
     *
     * @param payload the throw payload
     * @return the marker's stack count, or 0 when no marker of the ability stands there
     */
    private static int keyedStacksAt(BlobThrowPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(payload.targetPos()) instanceof ChainMarkerBlockEntity be
                && StackKey.matches(be.getAbilityId(), payload.abilityId())) {
            return be.getStackCount();
        }
        return 0;
    }

    /**
     * Called each client tick to decrement in-flight counters as blobs
     * arrive. Wire to the same client tick as {@link ThrowFreezeState#tick()}.
     */
    public static void tick() {
        // In-flight counts are decremented when BlobFlightManager removes
        // arrived flights. This tick cleans up stale entries.
        Iterator<Map.Entry<BlockPos, Integer>> it = IN_FLIGHT.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= 0) {
                it.remove();
            }
        }
    }

    /**
     * Decrements the in-flight count for a chain marker when a blob
     * arrives. Checks both the given pos and all adjacent positions
     * since the flight payload carries the hit block pos but the
     * in-flight map tracks the canonical marker pos (which may be adjacent).
     *
     * @param pos the target position from the flight payload
     */
    public static void onFlightArrived(BlockPos pos) {
        if (decrementInFlight(pos)) {
            return;
        }
        for (Direction dir : Direction.values()) {
            if (decrementInFlight(pos.relative(dir))) {
                return;
            }
        }
    }

    /**
     * Decrements the in-flight count at pos. Returns true if the entry existed.
     *
     * @param pos the block position to decrement
     * @return true if an in-flight entry existed at pos
     */
    private static boolean decrementInFlight(BlockPos pos) {
        return IN_FLIGHT.computeIfPresent(pos, (k, v) -> v > 1 ? v - 1 : null) != null;
    }

    /**
     * Clears all in-flight tracking (on disconnect or dimension change).
     */
    public static void clearInFlight() {
        IN_FLIGHT.clear();
    }

    /**
     * Returns the number of blobs currently in flight toward the given position.
     *
     * @param pos the target position
     * @return the in-flight count
     */
    public static int getInFlightCount(BlockPos pos) {
        return IN_FLIGHT.getOrDefault(pos, 0);
    }

    /**
     * Returns true if this throw would push a chain marker past max stacks,
     * counting both current stacks and in-flight blobs. Works even before
     * the marker exists on the client by predicting the placement position
     * and reading the stack ceiling from the selected ability's synced
     * chain block (decision diagnose-then-fix-fuse-and-cost).
     *
     * @param target    the resolved aim target
     * @param gooType   the goo type being thrown
     * @param abilityId the selected ability id string
     * @return true if the throw should be blocked
     */
    private static boolean wouldExceedMaxStacks(TargetResult target, ResourceKey<GooTypeDefinition> gooType,
                                                String abilityId) {
        if (target instanceof TargetResult.GlowCrystalTarget gct && gooType == GooTypes.GLOW) {
            return wouldExceedCrystalMax(gct, abilityId);
        }
        BlockPos pos = resolveTrackingPos(target, abilityId);
        return pos != null && wouldExceedMarkerMax(pos, abilityId);
    }

    /**
     * Checks current + pending stacks against the marker's max, using the BE if present.
     *
     * @param pos       the canonical marker position
     * @param abilityId the selected ability id string
     * @return true if the throw should be blocked
     */
    private static boolean wouldExceedMarkerMax(BlockPos pos, String abilityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        int[] currentAndMax = resolveCurrentAndMax(mc.level, pos, abilityId);
        if (currentAndMax.length == 0) {
            return false;
        }
        int pending = IN_FLIGHT.getOrDefault(pos, 0);
        return currentAndMax[0] + pending >= currentAndMax[1];
    }

    /**
     * Returns [current, max] from the selected ability's marker BE or its
     * synced chain block, or empty if unknown.
     *
     * @param level     the client level
     * @param pos       the marker position
     * @param abilityId the selected ability id string
     * @return a 2-element array [current, max], or empty if no ability is synced under the id
     */
    private static int[] resolveCurrentAndMax(ClientLevel level, BlockPos pos, String abilityId) {
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be
                && StackKey.matches(be.getAbilityId(), abilityId)) {
            return new int[]{be.getStackCount(), be.getMaxStacks()};
        }
        ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        if (ability == null) {
            return UNKNOWN_STACKS;
        }
        return new int[]{0, ability.maxStacks()};
    }

    /**
     * Returns true if the crystal is at (or will reach) max size with in-flight blobs.
     *
     * @param gct       the glow crystal target
     * @param abilityId the selected glow ability id string
     * @return true if the crystal cannot accept another blob
     */
    private static boolean wouldExceedCrystalMax(TargetResult.GlowCrystalTarget gct, String abilityId) {
        int current = gct.currentStacks();
        if (current <= 0) {
            return false;
        }
        ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        if (ability == null) {
            return false;
        }
        int pending = IN_FLIGHT.getOrDefault(gct.pos(), 0);
        return current + pending >= ability.maxStacks();
    }

    /**
     * Increments the in-flight count for any throw that would place or
     * stack on a chain marker. Tracks even before the marker exists so
     * rapid throws during flight time are counted.
     *
     * @param target    the resolved aim target
     * @param abilityId the selected ability id string
     */
    private static void trackInFlight(TargetResult target, String abilityId) {
        BlockPos pos = resolveTrackingPos(target, abilityId);
        if (pos != null) {
            IN_FLIGHT.merge(pos, 1, Integer::sum);
        }
    }

    /**
     * Resolves the canonical tracking position for in-flight counting.
     * Returns the position where a chain marker IS or WOULD BE placed.
     * Works before the marker exists so the first burst of throws can
     * be counted against maxStacks during the flight window.
     *
     * @param target    the resolved aim target
     * @param abilityId the selected ability id string
     * @return the canonical marker position, or null for entity/none targets
     */
    private static @Nullable BlockPos resolveTrackingPos(TargetResult target, String abilityId) {
        if (target instanceof TargetResult.ChainMarkerTarget cmt) {
            return cmt.pos();
        }
        if (target instanceof TargetResult.GlowCrystalTarget gct) {
            return gct.pos();
        }
        if (target instanceof TargetResult.BlockTarget bt) {
            return resolveBlockTrackingPos(bt, abilityId);
        }
        return null;
    }

    /**
     * Resolves the tracking position for a block target. If a marker of
     * the selected ability already exists at the hit pos or adjacent,
     * returns its position. Otherwise predicts placement: replaceable
     * blocks are displaced in-place, solid blocks place the marker on the
     * adjacent face.
     *
     * @param bt        the block target to resolve
     * @param abilityId the selected ability id string
     * @return the canonical marker position, or null if level unavailable
     */
    private static @Nullable BlockPos resolveBlockTrackingPos(TargetResult.BlockTarget bt, String abilityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        if (isKeyedMarker(mc.level, bt.pos(), abilityId)) {
            return bt.pos();
        }
        BlockPos adjacent = bt.pos().relative(bt.face());
        if (isKeyedMarker(mc.level, adjacent, abilityId)) {
            return adjacent;
        }
        BlockState state = mc.level.getBlockState(bt.pos());
        return state.canBeReplaced() ? bt.pos() : adjacent;
    }

    /**
     * Whether a chain marker of the selected ability stands at the position
     * (decision diagnose-then-fix-stack-key-match).
     *
     * @param level     the client level
     * @param pos       the block position
     * @param abilityId the selected ability id string
     * @return true for a marker the throw keys onto
     */
    private static boolean isKeyedMarker(ClientLevel level, BlockPos pos, String abilityId) {
        return level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be
                && StackKey.matches(be.getAbilityId(), abilityId);
    }

    /**
     * The point the aim line starts at, the glove blob the player sees, so
     * the flight leaves from where the line was drawn (decision
     * diagnose-then-fix-blob-off-the-line).
     *
     * @return the world-space aim line origin
     */
    private static Vec3 lineOrigin() {
        return GloveAim.handPosition(Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    /**
     * Converts a target result into a throw payload, or null if no valid target.
     *
     * @param target    the aim target
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the payload, or null for no target
     */
    private static @Nullable BlobThrowPayload targetToPayload(TargetResult target,
            ResourceKey<GooTypeDefinition> gooType, String abilityId, Vec3 origin) {
        if (target instanceof TargetResult.None) {
            return null;
        }
        return buildPayload(target, GooTypes.id(gooType), abilityId, origin);
    }

    /**
     * Reads the selection of the glove the player holds, main hand first.
     * Every selection names an ability (decision no-throw-without-ability),
     * so a glove with none selected throws nothing.
     *
     * @param player the local player
     * @return the selection, or null when the glove holds none
     */
    public static @Nullable GloveSelection heldSelection(Player player) {
        ItemStack glove = player.getMainHandItem();
        if (!(glove.getItem() instanceof GooGloveItem)) {
            glove = player.getOffhandItem();
        }
        return GooGloveItem.getSelection(glove);
    }

    /**
     * Builds the payload for non-None targets. Kept separate so the None early-exit
     *
     * @param target    the resolved non-None aim target
     * @param typeId    the goo type registry id
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the constructed throw payload
     * reduces the switch to 4 arms and keeps CC within threshold.
     */
    private static BlobThrowPayload buildPayload(TargetResult target, String typeId, String abilityId,
            Vec3 origin) {
        return switch (target) {
            case TargetResult.EntityTarget et -> entityPayload(typeId, et, abilityId, origin);
            case TargetResult.BlockTarget bt -> blockPayload(typeId, bt, abilityId, origin);
            case TargetResult.ChainMarkerTarget cmt -> chainMarkerPayload(typeId, cmt, abilityId, origin);
            case TargetResult.GlowCrystalTarget gct -> new BlobThrowPayload(typeId, NO_ENTITY,
                    gct.pos(), gct.face().ordinal(), false, abilityId, origin);
            default -> throw new IllegalArgumentException(target.toString());
        };
    }

    /**
     * Builds a throw payload aimed at an entity.
     *
     * @param typeId    the goo type registry id
     * @param et        the entity aim target
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the entity-targeted throw payload
     */
    private static BlobThrowPayload entityPayload(String typeId,
            TargetResult.EntityTarget et, String abilityId, Vec3 origin) {
        return new BlobThrowPayload(typeId, et.entity().getId(), BlockPos.ZERO, NO_ENTITY,
                false, abilityId, origin);
    }

    /**
     * Builds a throw payload aimed at a block face.
     *
     * @param typeId    the goo type registry id
     * @param bt        the block face aim target
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the block-targeted throw payload
     */
    private static BlobThrowPayload blockPayload(String typeId,
            TargetResult.BlockTarget bt, String abilityId, Vec3 origin) {
        return new BlobThrowPayload(typeId, NO_ENTITY, bt.pos(), bt.face().ordinal(),
                bt.grannyArc(), abilityId, origin);
    }

    /**
     * Builds a throw payload aimed at a chain marker, resolving its placed face.
     *
     * @param typeId    the goo type registry id
     * @param cmt       the chain marker aim target
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the chain-marker-targeted throw payload
     */
    private static BlobThrowPayload chainMarkerPayload(String typeId,
            TargetResult.ChainMarkerTarget cmt, String abilityId, Vec3 origin) {
        int faceOrdinal = resolveChainMarkerFace(cmt.pos()).getOpposite().ordinal();
        return new BlobThrowPayload(typeId, NO_ENTITY, cmt.pos(), faceOrdinal, false, abilityId, origin);
    }

    /**
     * Reads the placed face from the chain marker BE so the flight
     * destination lands at the orb's face boundary position.
     *
     * @param pos the chain marker block position
     * @return the placed face, or UP if the BE is unavailable
     */
    private static Direction resolveChainMarkerFace(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            return be.getPlacedFace();
        }
        return Direction.UP;
    }

    /**
     * Sends a custom payload packet to the server.
     *
     * @param payload the payload to send
     */
    private static void sendPayload(BlobThrowPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
