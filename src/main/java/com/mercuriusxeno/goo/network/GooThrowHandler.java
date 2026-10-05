package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Server-side handler for goo throw requests. Validates the client's claim,
 * depletes goo from the player's inventory, broadcasts the flight to nearby
 * players, and schedules the effect application after the goo "arrives."
 */
public final class GooThrowHandler {

    /** Cost of one throw (1 goo = 1,000 mB). */
    public static final int THROW_COST = 1000;
    /** Maximum throw range in blocks. */
    public static final double MAX_RANGE = 64.0;
    private static final double MAX_RANGE_SQUARED = MAX_RANGE * MAX_RANGE;

    /** Block center offset (half-block). */
    private static final double BLOCK_CENTER = 0.5;

    /** Log: player not holding glove. */
    private static final String LOG_NO_GLOVE = "Throw rejected: player {} not holding glove";
    /** Log: unknown goo type. */
    private static final String LOG_BAD_TYPE = "Throw rejected: unknown goo type '{}'";
    /** Log: target out of range. */
    private static final String LOG_OUT_OF_RANGE = "Throw rejected: target out of range ({} blocks)";
    /** Log: insufficient goo for throw. */
    private static final String LOG_NO_GOO = "Throw rejected: insufficient {} goo";
    /** Log: partial depletion warning. */
    private static final String LOG_PARTIAL_DEPLETE = "Partial depletion ({}/{}) for {} throw - proceeding anyway";
    /** Log: throw executed successfully. */
    private static final String LOG_THROW_OK = "Throw executed: {} by {} -> arrival in {} ticks";

    private GooThrowHandler() {}

    /**
     * Handles the throw payload on the server thread.
     *
     * @param payload the throw payload data
     * @param context the network context
     */
    public static void handle(GooThrowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) { return; }
            execute(player, payload);
        });
    }

    /**
     * Validates and executes the throw: the glove, the type, the range and
     * the goo in the player's inventory are checked, the goo is depleted,
     * the flight is broadcast and the effect scheduled for arrival. A mob
     * ability aimed at an entity within reach touches it at once instead,
     * and a self ability runs on the player.
     * decision mob-ability-touches-at-reach
     * decision self-delivery-runs-on-player
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     */
    public static void execute(ServerPlayer player, GooThrowPayload payload) {
        if (!validateGlove(player)) { return; }
        ResourceKey<GooTypeDefinition> gooType = validateGooType(payload);
        if (gooType == null) { return; }
        AbilityDefinition ability = thrownAbility(player.level(), payload.abilityId(), gooType);
        if (ability == null) {
            throwFlight(player, payload, gooType);
        } else {
            deliver(player, payload, gooType, ability);
        }
    }

    /**
     * Sends a named ability by its delivery: a self ability on the player, a
     * touch on an entity within reach, a flight otherwise.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param ability the thrown ability
     */
    private static void deliver(ServerPlayer player, GooThrowPayload payload, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        if (ability.delivery().kind() == DeliveryKind.SELF) {
            GooSelfHandler.invoke(player, gooType, ability);
        } else if (touchesTarget(player, payload, ability)) {
            GooTouchHandler.touch(player, payload, gooType);
        } else {
            throwFlight(player, payload, gooType);
        }
    }

    /**
     * Whether the throw lands as a touch on its target entity, measured to
     * the player's entity interaction range.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param ability the thrown ability
     * @return true when the ability touches rather than flies
     */
    private static boolean touchesTarget(ServerPlayer player, GooThrowPayload payload, AbilityDefinition ability) {
        boolean entityTarget = payload.targetEntityId() >= 0;
        return GooTouchHandler.touches(ability.delivery(), ability.badge(), entityTarget,
                entityTarget ? targetDistanceSquared(player, payload) : Double.MAX_VALUE,
                player.entityInteractionRange());
    }

    /**
     * Throws a flight at the payload's target once the range and the goo in
     * the player's inventory check out.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     */
    private static void throwFlight(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType) {
        if (!validateRange(player, payload)) { return; }
        int cost = resolveThrowCost(player, payload, gooType);
        if (!validateSupply(player, gooType, cost)) { return; }
        double distSq = targetDistanceSquared(player, payload);
        depleteAndThrow(player, payload, gooType, distSq, cost);
    }

    /** Validates glove is held, logging rejection if not.
     *
     * @param player the throwing player
     * @return true if valid
     */
    static boolean validateGlove(ServerPlayer player) {
        boolean held = player.getMainHandItem().getItem() instanceof GooGloveItem
            || player.getOffhandItem().getItem() instanceof GooGloveItem;
        if (held) { return true; }
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_NO_GLOVE, player.getName().getString()); }
        return false;
    }

    /** Validates and resolves the goo type from the payload, logging rejection if invalid.
     *
     * @param payload the throw payload data
     * @return the resolved goo type, or null if invalid
     */
    private static ResourceKey<GooTypeDefinition> validateGooType(GooThrowPayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null && Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_BAD_TYPE, payload.gooTypeId());
        }
        return gooType;
    }

    /** Validates target is within max throw range, logging rejection if not.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @return true if in range
     */
    private static boolean validateRange(ServerPlayer player, GooThrowPayload payload) {
        double distSq = targetDistanceSquared(player, payload);
        if (distSq <= MAX_RANGE_SQUARED) { return true; }
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_OUT_OF_RANGE, Math.sqrt(distSq)); }
        return false;
    }

    /** Validates the player has enough goo, logging rejection if not.
     *
     * @param player  the throwing player
     * @param gooType the goo type to check
     * @param cost    the resolved mB cost for this throw
     * @return true if supply is sufficient
     */
    private static boolean validateSupply(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, int cost) {
        if (GooSourceScanner.hasEnough(player, gooType, cost)) { return true; }
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_NO_GOO, GooTypes.id(gooType)); }
        return false;
    }

    /** Resolves the throw cost from the ability definition, falling back to THROW_COST.
     *
     * @param player  the throwing player
     * @param payload the throw payload
     * @param gooType the resolved goo type
     * @return the cost in mB for this throw
     */
    static int resolveThrowCost(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition def = thrownAbility(player.level(), payload.abilityId(), gooType);
        return def == null ? THROW_COST : def.cost();
    }

    /**
     * The ability a throw names, when the registry holds it for the thrown type.
     *
     * @param level     the server level
     * @param abilityId the thrown ability id string, empty when the throw names none
     * @param gooType   the thrown goo type
     * @return the ability, or null when the throw names none of the type
     */
    static @Nullable AbilityDefinition thrownAbility(ServerLevel level, String abilityId,
            ResourceKey<GooTypeDefinition> gooType) {
        Identifier id = abilityId.isEmpty() ? null : Identifier.tryParse(abilityId);
        if (id == null) { return null; }
        AbilityDefinition def = AbilityRegistry.of(level).getAbility(id);
        return def == null || def.gooType() != gooType ? null : def;
    }

    /**
     * The delivery a flight flies by: the named ability's, or a plain arc
     * where the throw names none, which lands nothing (decisions
     * standing-abilities-name-arc-or-beam, no-throw-without-ability).
     *
     * @param level     the server level
     * @param abilityId the thrown ability id string
     * @param gooType   the thrown goo type
     * @return the delivery
     */
    public static Delivery flightDelivery(ServerLevel level, String abilityId, ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition def = thrownAbility(level, abilityId, gooType);
        return def == null ? Delivery.ARC : def.delivery();
    }

    /** Depletes goo, broadcasts the flight, and schedules the delayed effect.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param distSq  squared distance to target (pre-validated)
     * @param cost    the resolved mB cost for this throw
     */
    private static void depleteAndThrow(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType, double distSq, int cost) {
        int depleted = GooSourceScanner.deplete(player, gooType, cost);
        if (depleted < cost && Goo.LOGGER.isWarnEnabled()) {
            Goo.LOGGER.warn(LOG_PARTIAL_DEPLETE, depleted, cost, GooTypes.id(gooType));
        }

        double distance = Math.sqrt(distSq);
        GooTypeDefinition definition = GooTypes.definition(player.level().registryAccess(), gooType);
        Delivery delivery = flightDelivery(player.level(), payload.abilityId(), gooType);
        int travelTicks = delivery.travelTicks(distance, definition.levity(), definition.baseFlightTime());
        broadcastFlight(player, payload, delivery, travelTicks);
        GooServerState.of(player.level().getServer()).gooEffects()
                .scheduleEffect(player, payload, gooType, delivery, travelTicks);

        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_THROW_OK, GooTypes.id(gooType), player.getName().getString(), travelTicks); }
    }

    /** Builds and broadcasts the flight payload to tracking players and the thrower.
     *
     * @param player      the throwing player
     * @param payload     the throw payload data
     * @param delivery    the delivery the flight flies by
     * @param travelTicks the number of ticks until arrival
     */
    private static void broadcastFlight(ServerPlayer player, GooThrowPayload payload, Delivery delivery,
            int travelTicks) {
        Vec3 hand = ThrowArc.clampToReach(player.getEyePosition(), payload.origin(),
                ThrowArc.HAND_REACH * player.getScale());
        GooFlightPayload flight = buildFlightPayload(hand, payload, delivery, travelTicks);
        PacketDistributor.sendToPlayersTrackingEntity(player, flight);
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no flight.
        if (player.connection.hasChannel(flight)) {
            PacketDistributor.sendToPlayer(player, flight);
        }
    }

    /** Builds the flight payload from the throw origin, throw data, and travel time.
     *
     * @param hand        the world-space throw origin, clamped within reach
     * @param payload     the throw payload data
     * @param delivery    the delivery the flight flies by
     * @param travelTicks the number of ticks until arrival
     * @return the constructed flight payload
     */
    private static GooFlightPayload buildFlightPayload(Vec3 hand, GooThrowPayload payload, Delivery delivery,
            int travelTicks) {
        return new GooFlightPayload(
                hand.x, hand.y, hand.z,
                payload.gooTypeId(),
                payload.targetEntityId(),
                payload.targetPos(),
                payload.targetFace(),
                travelTicks,
                payload.grannyArc(),
                payload.abilityId(),
                delivery
        );
    }

    /**
     * Returns squared distance from the player to the packet's target.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @return squared distance in blocks
     */
    static double targetDistanceSquared(ServerPlayer player, GooThrowPayload payload) {
        if (payload.targetEntityId() >= 0) {
            Entity target = player.level().getEntity(payload.targetEntityId());
            if (target == null) { return Double.MAX_VALUE; }
            return player.distanceToSqr(target);
        }
        BlockPos pos = payload.targetPos();
        return player.distanceToSqr(pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER);
    }

    /**
     * Converts a Direction ordinal (from the network payload) to a Direction.
     * Returns null for out-of-range values (e.g. -1 for entity targets).
     *
     * @param ordinal the direction ordinal from the payload
     * @return the corresponding direction, or null if invalid
     */
    static Direction directionFromOrdinal(int ordinal) {
        Direction[] dirs = Direction.values();
        if (ordinal >= 0 && ordinal < dirs.length) {
            return dirs[ordinal];
        }
        return null;
    }
}
