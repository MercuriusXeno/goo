package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.entity.RollingGoo;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.ReagentScanner;
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
    /** Log: a throw naming no ability the player may use, refused. */
    private static final String LOG_UNUSABLE = "Throw rejected: ability '{}' unusable by {}";
    /** Log: a throw whose ability consumes an item the player lacks, refused. */
    private static final String LOG_NO_REAGENT = "Throw rejected: {} lacks a reagent ability '{}' consumes";
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
     * Handles a charged ability's release on the server thread: a known
     * ability of a known type, thrown from a held glove with every reagent
     * it consumes, fires at the charge its hold reached.
     * nova-ring-grows-with-the-hold
     *
     * @param payload the charge payload
     * @param context the network context
     */
    public static void handleCharge(GooChargePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                releaseCharge(player, payload);
            }
        });
    }

    /**
     * Fires a charged ability the player let go, when it may: a self ability
     * on the player, a thrown one as a sweep of flecks.
     * shards-sling-then-morph-to-flechettes
     *
     * @param player  the releasing player
     * @param payload the charge payload
     */
    public static void releaseCharge(ServerPlayer player, GooChargePayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (!validateGlove(player) || gooType == null) {
            return;
        }
        AbilityDefinition ability = usableAbility(player, payload.abilityId(), gooType);
        if (ability != null && holdsReagents(player, ability)) {
            fireCharge(player, gooType, ability, payload.heldTicks());
        }
    }

    /**
     * Fires a released charge by its delivery: on the player for a self
     * ability, as a sweep of flecks for a thrown one that slings.
     *
     * @param player    the releasing player
     * @param gooType   the ability's goo type
     * @param ability   the charged ability
     * @param heldTicks the ticks the use key was held
     */
    private static void fireCharge(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                   AbilityDefinition ability, int heldTicks) {
        if (ability.delivery().kind() == DeliveryKind.SELF) {
            GooSelfHandler.release(player, gooType, ability, heldTicks);
        } else if (ability.delivery().charge().slings()) {
            FleckSling.sling(player, gooType, ability, heldTicks);
        }
    }

    /**
     * Validates and executes the throw: the glove, the type, the range and
     * the goo in the player's inventory are checked, the goo is depleted,
     * the flight is broadcast and the effect scheduled for arrival. A mob
     * ability aimed at an entity within reach touches it at once instead,
     * and a self ability runs on the player: on command, or after the eat
     * for a self + brew ability. A throw naming no ability the player may
     * use, or consuming an item the player lacks, is refused whole, draining nothing.
     * decision mob-ability-touches-at-reach
     * decision self-delivery-runs-on-player
     * decision self-brew-goos-eat-before-the-effect
     * decision ability-hidden-until-recipes-known
     * decision ability-json-names-its-reagent
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     */
    public static void execute(ServerPlayer player, GooThrowPayload payload) {
        if (!validateGlove(player)) { return; }
        ResourceKey<GooTypeDefinition> gooType = validateGooType(payload);
        if (gooType == null) { return; }
        AbilityDefinition ability = usableAbility(player, payload.abilityId(), gooType);
        if (ability == null) {
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_UNUSABLE, payload.abilityId(), player.getName().getString());
            }
            return;
        }
        if (throwable(player, ability)) {
            deliver(player, payload, gooType, ability);
        }
    }

    /**
     * Whether an ability may be thrown: never one sized at will, which opens
     * by its drag (decision black-hole-leaves-a-compression-sphere), and only
     * with every reagent it consumes held.
     *
     * @param player  the throwing player
     * @param ability the thrown ability
     * @return true when the throw goes ahead
     */
    private static boolean throwable(ServerPlayer player, AbilityDefinition ability) {
        return !ability.hasTag(AbilityTags.DRAG_SIZED) && holdsReagents(player, ability);
    }

    /**
     * Whether the player holds one of every item the ability consumes,
     * logging the refusal (decision ability-json-names-its-reagent).
     *
     * @param player  the throwing player
     * @param ability the thrown ability
     * @return true when no reagent is missing
     */
    static boolean holdsReagents(ServerPlayer player, AbilityDefinition ability) {
        if (ReagentScanner.holdsEvery(player, ability.consumes())) {
            return true;
        }
        if (Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_NO_REAGENT, player.getName().getString(), ability.id());
        }
        return false;
    }

    /**
     * Sends a named ability by its delivery: a self ability to the player, a
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
            GooSelfHandler.deliver(player, gooType, ability, payload.pressedFace());
        } else if (touchesTarget(player, payload, ability)) {
            GooTouchHandler.touch(player, payload, gooType);
        } else if (ability.delivery().rolls()) {
            rollGoo(player, payload, gooType, ability);
        } else if (ability.badge().aimsAPoint()) {
            throwAtPoint(player, payload, gooType);
        } else if (!aimsNoMob(ability.badge(), payload.targetEntityId() >= 0)) {
            throwFlight(player, payload, gooType);
        }
    }

    /**
     * Whether a throw is a mob ability aimed at no entity, which is refused
     * whole: a mob ability is never thrown at the world.
     *
     * @param badge        the ability's badge
     * @param entityTarget whether the throw names an entity
     * @return true for a mob ability naming no entity
     */
    static boolean aimsNoMob(AbilityBadge badge, boolean entityTarget) {
        return badge == AbilityBadge.MOB && !entityTarget;
    }

    /**
     * Throws an ability aiming a point at that point, capped onto the throw
     * range along the throw's line rather than refused, so aiming at the sky
     * throws to the range's end.
     * aim-point-follows-the-cursor
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     */
    private static void throwAtPoint(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType) {
        Vec3 eye = player.getEyePosition();
        Vec3 capped = capToRange(eye, payload.targetPoint(), MAX_RANGE);
        GooThrowPayload aimed = capped.equals(payload.targetPoint()) ? payload : payload.aimedAt(capped);
        int cost = resolveThrowCost(player, aimed, gooType);
        if (!validateSupply(player, gooType, cost)) { return; }
        depleteAndThrow(player, aimed, gooType, eye.distanceToSqr(capped), cost);
    }

    /**
     * Sets a rolling goo off from the hand along the player's look, when the
     * player holds its cost: it pays and takes its reagents as any throw does.
     * orb-carries-a-swirling-nova
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param ability the rolling ability
     */
    private static void rollGoo(ServerPlayer player, GooThrowPayload payload, ResourceKey<GooTypeDefinition> gooType,
                                AbilityDefinition ability) {
        if (!validateSupply(player, gooType, ability.cost())) {
            return;
        }
        GooSourceScanner.deplete(player, gooType, ability.cost());
        consumeReagents(player, payload.abilityId(), gooType);
        Vec3 hand = ThrowArc.clampToReach(player.getEyePosition(), payload.origin(),
                ThrowArc.HAND_REACH * player.getScale());
        GooEffectScheduler.playThrowSound(player, ability.delivery());
        RollingGoo.roll(player.level(), player, ability, hand, player.getLookAngle());
    }

    /**
     * Caps a point onto the range around the eye, along the line from the eye.
     *
     * @param eye   the thrower's eye
     * @param point the aimed point
     * @param range the throw range in blocks
     * @return the point, or the point at the range's end along its line
     */
    static Vec3 capToRange(Vec3 eye, Vec3 point, double range) {
        Vec3 line = point.subtract(eye);
        double length = line.length();
        return length <= range ? point : eye.add(line.scale(range / length));
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
     * The ability a throw, stream or selection names, when the player knows
     * every item it requires (decision ability-hidden-until-recipes-known).
     *
     * @param player    the player using the ability
     * @param abilityId the ability id string, empty when the payload names none
     * @param gooType   the goo type the payload names
     * @return the ability, or null when it names none of the type or the player lacks a required item
     */
    static @Nullable AbilityDefinition usableAbility(ServerPlayer player, String abilityId,
            ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition def = thrownAbility(player.level(), abilityId, gooType);
        return def != null && def.isKnownTo(PlayerKnowledge.of(player)) ? def : null;
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

    /** Depletes goo and the ability's reagents, broadcasts the flight, and schedules the delayed effect.
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
        consumeReagents(player, payload.abilityId(), gooType);

        double distance = Math.sqrt(distSq);
        GooTypeDefinition definition = GooTypes.definition(player.level().registryAccess(), gooType);
        Delivery delivery = flightDelivery(player.level(), payload.abilityId(), gooType);
        int travelTicks = delivery.travelTicks(distance, definition.levity(), definition.baseFlightTime());
        broadcastFlight(player, payload, delivery, travelTicks);
        GooServerState.of(player.level().getServer()).gooEffects()
                .scheduleEffect(player, payload, gooType, delivery, travelTicks);

        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_THROW_OK, GooTypes.id(gooType), player.getName().getString(), travelTicks); }
    }

    /**
     * Takes one of each item the thrown ability consumes from the player.
     * decision ability-json-names-its-reagent
     *
     * @param player    the paying player
     * @param abilityId the thrown ability id string
     * @param gooType   the thrown goo type
     */
    static void consumeReagents(ServerPlayer player, String abilityId, ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition def = thrownAbility(player.level(), abilityId, gooType);
        if (def != null) {
            ReagentScanner.consumeOneOfEach(player, def.consumes());
        }
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
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no flight.
        EntityVisuals.sendToWatchers(player, flight);
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
                delivery,
                payload.targetPoint()
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
