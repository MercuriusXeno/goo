package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.LeadRelease;
import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.ability.ThrowRange;
import com.mercuriusxeno.goo.ability.program.ScanVisualStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TargetGatedStep;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.GateRay;
import com.mercuriusxeno.goo.ability.typhoon.ShulkerSpew;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.entity.RollingGoo;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.lunar.LunarGravity;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.AimReach;
import com.mercuriusxeno.goo.throwing.GateArc;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Server-side handler for goo throw requests. Validates the client's claim,
 * depletes goo from the player's inventory, broadcasts the flight to nearby
 * players, and schedules the effect application after the goo "arrives."
 */
public final class GooThrowHandler {

    /** Cost of one throw (1 goo = 1,000 mB). */
    public static final int THROW_COST = 1000;
    /** How far off a gate's face a throw's face point may stand and still read as on it. */
    private static final double FACE_SLACK = 0.05;

    /** Block center offset (half-block). */
    private static final double BLOCK_CENTER = 0.5;

    /** Log: player not holding glove. */
    private static final String LOG_NO_CASTER = "Throw rejected: player {} holds no glove, nor raw goo of type {}";
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
    private static final String LOG_PARTIAL_DEPLETE = "Could not charge {} mB for {} throw - proceeding anyway";
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
     * on the player, a spontaneous cast at its aimed target.
     *
     * @param player  the releasing player
     * @param payload the charge payload
     */
    public static void releaseCharge(ServerPlayer player, GooChargePayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null || !validateCaster(player, gooType)) {
            return;
        }
        AbilityDefinition ability = usableAbility(player, payload.abilityId(), gooType);
        if (ability != null && holdsReagents(player, ability)) {
            fireCharge(player, gooType, ability, payload);
        }
    }

    /**
     * Fires a released charge by its delivery: on the player for a self
     * ability, at the aimed target at the size the hold reached for a
     * spontaneous cast sized by its charge.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @param player    the releasing player
     * @param gooType   the ability's goo type
     * @param ability   the charged ability
     * @param payload   the charge payload, its hold and its aimed target
     */
    private static void fireCharge(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                   AbilityDefinition ability, GooChargePayload payload) {
        if (ability.delivery().kind() == DeliveryKind.SELF) {
            GooSelfHandler.release(player, gooType, ability, payload.heldTicks());
        } else if (ability.delivery().sizesByCharge()) {
            payload.aimed().ifPresent(aimed -> GooChargedCastHandler.cast(player, gooType, ability, payload, aimed));
        }
    }

    /**
     * Handles the frame a player's glove arm took as a press held, let go or
     * dropped: the players watching draw the same frame, the player's own
     * client having posed it already.
     * decision ability-json-names-its-arm-pose
     * decision use-key-throw-swings-no-more
     *
     * @param payload the pose payload
     * @param context the network context
     */
    public static void handleGlovePose(GlovePosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && validateCaster(player)) {
                EntityVisuals.sendToTrackers(player, new ArmPosePayload(player.getId(), payload.frame(),
                        GooCasters.casterOnTheRight(player)));
            }
        });
    }

    /**
     * Validates and executes the throw: the glove, the type, the range and
     * the goo in the player's inventory are checked, the goo is depleted,
     * the flight is broadcast and the effect scheduled for arrival. A mob
     * ability aimed at an entity within reach touches it at once instead,
     * and a self ability runs on the player: on command, or held from the release
     * of a durative charge. A throw naming no ability the player may
     * use, or consuming an item the player lacks, is refused whole, draining nothing,
     * and so is one whose ability refuses the block it strikes.
     * decision mob-ability-touches-at-reach
     * decision self-delivery-runs-on-player
     * decision durative-self-abilities-wash-over-the-view-in-place-of-eating
     * decision ability-hidden-until-recipes-known
     * decision ability-json-names-its-reagent
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     */
    public static void execute(ServerPlayer player, GooThrowPayload payload) {
        ResourceKey<GooTypeDefinition> gooType = validateGooType(payload);
        if (gooType == null || !validateCaster(player, gooType)) { return; }
        AbilityDefinition ability = usableAbility(player, payload.abilityId(), gooType);
        if (ability == null) {
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_UNUSABLE, payload.abilityId(), player.getName().getString());
            }
            return;
        }
        // aim-and-shots-carry-through-linked-gates: a throw naming a gate the server finds untrue goes nowhere
        if (payload.gate().isPresent() && hopOf(player, payload, ThrowRange.of(ability)).isEmpty()) {
            return;
        }
        if (!ability.delivery().stacksJavelins() && cleared(player, payload, ability)) {
            deliver(player, payload, gooType, ability);
        }
    }


    /**
     * Whether a throw goes ahead: the ability is throwable and no step of it
     * refuses the block the throw strikes.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param ability the thrown ability
     * @return true when the throw is delivered
     */
    private static boolean cleared(ServerPlayer player, GooThrowPayload payload, AbilityDefinition ability) {
        return throwable(player, ability) && !refusesTarget(player, payload, ability);
    }

    /**
     * Whether the release's target refuses it: a face a step cannot take, the thrower's own minion, or a mob a step refuses for
     * the thrower.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param ability the thrown ability
     * @return true when the target refuses the throw
     */
    private static boolean refusesTarget(ServerPlayer player, GooThrowPayload payload, AbilityDefinition ability) {
        return refusedAtFace(player, payload, ability)
                || aimsAnOwnMinion(player, payload, ability) || aimsAMobItsStepsRefuse(player, payload, ability);
    }

    /**
     * Whether a throw names a mob a step of its ability refuses for the
     * thrower, such as Charm at a led mob or Lead at another player's
     * led mob, which is refused whole, before anything is paid.
     * refused-rally-or-charm-spends-no-goo
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param ability the thrown ability
     * @return true when a step refuses the named mob for the thrower
     */
    private static boolean aimsAMobItsStepsRefuse(ServerPlayer player, GooThrowPayload payload,
                                                  AbilityDefinition ability) {
        Entity target = payload.targetEntityId() < 0 ? null : player.level().getEntity(payload.targetEntityId());
        return target != null && Step.refusedByAny(ability.behaviors(), player, target);
    }

    /**
     * Whether a harmful mob ability is thrown at a mob its thrower led or
     * charmed, which is refused whole, before anything is paid, so neither a
     * touch nor a flight lands on it.
     * mob-targeted-attacks-refuse-your-minions
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param ability the thrown ability
     * @return true when the throw names the thrower's own minion
     */
    private static boolean aimsAnOwnMinion(ServerPlayer player, GooThrowPayload payload, AbilityDefinition ability) {
        if (payload.targetEntityId() < 0
                || !OwnMinions.sparesOwnMinions(ability.badge(), ability.delivery().kind(),
                        ability.hasTag(AbilityTags.HARMFUL))) {
            return false;
        }
        Entity target = player.level().getEntity(payload.targetEntityId());
        return target != null && OwnMinions.ownMinionOf(player, target);
    }

    /**
     * Refuses a throw at a block face a step of its ability cannot take,
     * before anything is paid, the step showing the refusal there; Quantum's
     * Tunnel refuses a face with no opening ahead within its reach.
     * hole-closes-the-space-to-an-opening-ahead
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param ability the thrown ability
     * @return true when the throw was refused
     */
    private static boolean refusedAtFace(ServerPlayer player, GooThrowPayload payload, AbilityDefinition ability) {
        Direction face = directionFromOrdinal(payload.targetFace());
        if (payload.targetEntityId() >= 0 || face == null) {
            return false;
        }
        for (Step step : ability.behaviors()) {
            if (step instanceof TargetGatedStep gated && !gated.admitsFace(player, payload.targetPos(), face)) {
                gated.refuseAtFace(player, payload.targetPos(), face);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an ability may be thrown: never one sized by its charge, which
     * casts on its charge's release
     * (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob), nor one
     * cast by its footprint (decision spire-rips-walls-and-platforms), and only
     * with every reagent it consumes held.
     *
     * @param player  the throwing player
     * @param ability the thrown ability
     * @return true when the throw goes ahead
     */
    private static boolean throwable(ServerPlayer player, AbilityDefinition ability) {
        return !ability.delivery().sizesByCharge() && !ability.hasTag(AbilityTags.FOOTPRINT)
                && holdsReagents(player, ability);
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
     * scan along the look, a spontaneous cast at the aimed target, a charm-like
     * cast's dart at the mob it took, and any other thrown from the hand.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param ability the thrown ability
     */
    private static void deliver(ServerPlayer player, GooThrowPayload payload, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        if (ability.delivery().kind() == DeliveryKind.SELF) {
            GooSelfHandler.deliver(player, gooType, ability);
        } else if (ability.delivery().kind() == DeliveryKind.SCAN) {
            // click-is-a-hit-scan-drawn-as-a-delayed-wave
            GooScanHandler.scan(player, payload, gooType, ability);
        } else if (ability.delivery().kind() == DeliveryKind.SPONTANEOUS) {
            castAtTarget(player, payload, gooType, ability);
        } else if (ability.delivery().envelops()) {
            castAtTakenMob(player, payload, gooType, ability);
        } else {
            throwFromTheHand(player, payload, gooType, ability);
        }
    }

    /**
     * Throws an ability from the hand: a touch on an entity within reach, a
     * roll, a throw at a point, or a flight at its target.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param ability the thrown ability
     */
    private static void throwFromTheHand(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        if (touchesTarget(player, payload, ability)) {
            GooTouchHandler.touch(player, payload, gooType);
        } else if (ability.delivery().rolls()) {
            rollGoo(player, payload, gooType, ability);
        } else if (aimedBadge(ability).aimsAPoint()) {
            throwAtPoint(player, payload, gooType, ThrowRange.of(ability));
        } else if (!aimsNoMob(aimedBadge(ability), payload.targetEntityId() >= 0)) {
            throwFlight(player, payload, gooType, ThrowRange.of(ability));
        }
    }

    /**
     * Sends a charm-like cast's release at the mob its press took, near or
     * far, never touching it at reach: the blob darts to it, or spews a
     * shulker bullet at it; a release naming no living mob the server holds
     * is refused whole, unpaid.
     * charm-like-abilities-lock-the-highlighted-mob-on-right-click
     *
     * @param player  the casting player
     * @param payload the release, naming the taken mob
     * @param gooType the validated goo type
     * @param ability the cast ability
     */
    private static void castAtTakenMob(ServerPlayer player, GooThrowPayload payload,
                                       ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        Entity taken = payload.targetEntityId() < 0 ? null : player.level().getEntity(payload.targetEntityId());
        if (!takesMob(taken)) {
            return;
        }
        if (ability.delivery().spewsAShulkerBullet()) {
            spewShulkerBullet(player, payload, gooType, taken, ThrowRange.of(ability));
        } else {
            throwFlight(player, payload, gooType, ThrowRange.of(ability));
        }
    }

    /**
     * Spews a shulker bullet from the held blob at the taken mob, once the
     * range and the goo in the player's inventory check out, paying as a
     * throw pays.
     * charm-like-abilities-lock-the-highlighted-mob-on-right-click
     *
     * @param player  the casting player
     * @param payload the release, its origin the held blob
     * @param gooType the validated goo type
     * @param taken   the mob the press took
     * @param range   the cast ability's range in blocks
     */
    private static void spewShulkerBullet(ServerPlayer player, GooThrowPayload payload,
                                          ResourceKey<GooTypeDefinition> gooType, Entity taken, double range) {
        if (!validateRange(player, payload, range)) { return; }
        GooFraction cost = resolveThrowCost(player, payload, gooType);
        if (!validateSupply(player, gooType, cost)) { return; }
        if (!AbilityCharges.charge(player, gooType, cost) && Goo.LOGGER.isWarnEnabled()) {
            Goo.LOGGER.warn(LOG_PARTIAL_DEPLETE, cost, GooTypes.id(gooType));
        }
        consumeReagents(player, payload.abilityId(), gooType);
        ShulkerSpew.spew(player, taken, ThrowArc.clampToReach(player.getEyePosition(), payload.origin(),
                ThrowArc.HAND_REACH * player.getScale()));
    }

    /**
     * Whether an entity a charm-like cast names is a mob it may take: a
     * living entity still alive.
     *
     * @param named the entity the release names, or null where the server holds none of its id
     * @return true for a living entity alive
     */
    static boolean takesMob(@Nullable Entity named) {
        return named instanceof LivingEntity living && living.isAlive();
    }

    /**
     * The badge a thrown ability aims as, Dart's held javelin aiming as a mob ability.
     * decision dart-forms-on-right-click-and-throws-on-left-click
     *
     * @param ability the thrown ability
     * @return the badge its throw reads
     */
    static AbilityBadge aimedBadge(AbilityDefinition ability) {
        return ability.badge().aimsAs(ability.delivery().kind());
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
     * Throws an ability aiming a point at that point, capped onto the
     * ability's range along the throw's line rather than refused, so aiming
     * at the sky throws to the range's end.
     * aim-point-follows-the-cursor
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param range   the thrown ability's range in blocks
     */
    private static void throwAtPoint(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType, double range) {
        Vec3 eye = player.getEyePosition();
        Optional<GateArc.Hop> hop = hopOf(player, payload, range);
        if (hop.isPresent()) {
            // aim-and-shots-carry-through-linked-gates: a point through a gate is reached or refused, never capped
            double length = throughDistance(eye, hop.get(), payload.targetPoint());
            GooFraction cost = resolveThrowCost(player, payload, gooType);
            if (!AimReach.takes(length, range) || !validateSupply(player, gooType, cost)) {
                return;
            }
            depleteAndThrow(player, payload, gooType, length * length, cost);
            return;
        }
        Vec3 capped = pointThrownAt(eye, payload.targetPoint(), range);
        GooThrowPayload aimed = capped.equals(payload.targetPoint()) ? payload : payload.aimedAt(capped);
        GooFraction cost = resolveThrowCost(player, aimed, gooType);
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
        if (!AbilityCharges.charge(player, gooType, ability.cost().exactIn(GooValues.of(player.level())))) {
            logNoGoo(gooType);
            return;
        }
        consumeReagents(player, payload.abilityId(), gooType);
        Vec3 hand = ThrowArc.clampToReach(player.getEyePosition(), payload.origin(),
                ThrowArc.HAND_REACH * player.getScale());
        GooEffectScheduler.playThrowSound(player, ability.delivery());
        RollingGoo.roll(player.level(), player, ability, hand, player.getLookAngle());
    }

    /**
     * The point a throw aiming a point is thrown at: the aimed point, capped
     * onto the thrown ability's range along the line from the eye.
     * aim-point-follows-the-cursor
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     *
     * @param eye   the thrower's eye
     * @param point the aimed point
     * @param range the thrown ability's range in blocks
     * @return the point the throw lands at
     */
    static Vec3 pointThrownAt(Vec3 eye, Vec3 point, double range) {
        return capToRange(eye, point, range);
    }

    /**
     * Whether a release's aimed point stands within its ability's range of
     * the eye, the measure the client's indicator lights by, the point held
     * onto the target the release names.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     * decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast
     *
     * @param eye     the caster's eye
     * @param target  the named target's box, the block's cell or the mob's bounding box
     * @param claimed the point the release claims it aimed
     * @param range   the ability's range in blocks
     * @return true for a release the server takes
     */
    public static boolean takesAim(Vec3 eye, AABB target, Vec3 claimed, double range) {
        return AimReach.takes(aimedLength(eye, Optional.empty(), target, claimed), range);
    }

    /**
     * How far a release's aimed point lies from the eye, through the gate it
     * passed where it names one, the point held onto the target it names.
     * decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast
     *
     * @param eye     the caster's eye
     * @param hop     the gate the release passed, empty on the near side
     * @param target  the named target's box
     * @param claimed the point the release claims it aimed
     * @return the length in blocks
     */
    static double aimedLength(Vec3 eye, Optional<GateArc.Hop> hop, AABB target, Vec3 claimed) {
        Vec3 aimed = AimReach.onTarget(target, claimed);
        return hop.map(passed -> AimReach.lengthThrough(eye, passed, aimed)).orElseGet(() -> eye.distanceTo(aimed));
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
                entityTarget ? targetDistanceSquared(player, payload, ThrowRange.of(ability)) : Double.MAX_VALUE,
                player.entityInteractionRange());
    }

    /**
     * Throws a flight at the payload's target once the range and the goo in
     * the player's inventory check out.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param range   the thrown ability's range in blocks
     */
    private static void throwFlight(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType, double range) {
        if (!validateRange(player, payload, range)) { return; }
        GooFraction cost = resolveThrowCost(player, payload, gooType);
        if (!validateSupply(player, gooType, cost)) { return; }
        double distSq = targetDistanceSquared(player, payload, range);
        depleteAndThrow(player, payload, gooType, distSq, cost);
    }

    /**
     * Makes a spontaneous cast at the aimed target the release names, once
     * the range and the goo check out: it pays as a throw does and its
     * program runs there this tick, with no flight and nothing thrown; a
     * program naming a visual darts it from the palm blob to the place.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @param player  the casting player
     * @param payload the release: the cast origin and the aimed block, face and point
     * @param gooType the validated goo type
     * @param ability the cast ability
     */
    private static void castAtTarget(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        if (!validateRange(player, payload, ThrowRange.of(ability))) { return; }
        GooFraction cost = resolveThrowCost(player, payload, gooType);
        if (!validateSupply(player, gooType, cost)) { return; }
        AbilityCharges.charge(player, gooType, cost);
        consumeReagents(player, payload.abilityId(), gooType);
        GooEffectScheduler.castNow(player, payload, gooType);
        Vec3 castOrigin = ThrowArc.clampToReach(player.getEyePosition(), payload.origin(),
                ThrowArc.HAND_REACH * player.getScale());
        ScanVisualStep.in(ability.behaviors()).ifPresent(visual -> EntityVisuals.sendToWatchers(player,
                CastDart.wave(castOrigin, payload.targetPoint(), visual)));
    }

    /**
     * Validates a caster is held, a glove or raw goo, for a step following a
     * cast whose type it does not carry.
     *
     * @param player the casting player
     * @return true where the player holds a caster
     */
    static boolean validateCaster(ServerPlayer player) {
        return GooCasters.holdsCaster(player);
    }

    /**
     * Validates the player's caster casts the type, logging rejection if not:
     * a glove casts any type, raw goo held bare handed only its own.
     * decision raw-goo-items-work-bare-handed-for-the-held-type
     *
     * @param player  the casting player
     * @param gooType the cast's goo type
     * @return true where the cast may go ahead
     */
    static boolean validateCaster(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType) {
        if (GooCasters.castsType(player, gooType)) { return true; }
        if (Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_NO_CASTER, player.getName().getString(), GooTypes.id(gooType));
        }
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

    /** Validates the aimed point is within the ability's range of the eye, logging rejection if not.
     * decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param range   the ability's range in blocks
     * @return true if in range
     */
    private static boolean validateRange(ServerPlayer player, GooThrowPayload payload, double range) {
        Optional<AABB> target = targetBox(player, payload);
        Optional<GateArc.Hop> hop = hopOf(player, payload, range);
        if (target.isEmpty() || payload.gate().isPresent() && hop.isEmpty()) { return false; }
        double length = aimedLength(player.getEyePosition(), hop, target.get(), payload.targetPoint());
        if (AimReach.takes(length, range)) { return true; }
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_OUT_OF_RANGE, length); }
        return false;
    }

    /**
     * The box of the target a release names: the mob's bounding box, or the aimed block's cell.
     *
     * @param player  the releasing player
     * @param payload the release
     * @return the box, empty for a mob the server holds no entity of
     */
    private static Optional<AABB> targetBox(ServerPlayer player, GooThrowPayload payload) {
        if (payload.targetEntityId() < 0) {
            return Optional.of(new AABB(payload.targetPos()));
        }
        return Optional.ofNullable(player.level().getEntity(payload.targetEntityId())).map(Entity::getBoundingBox);
    }

    /** Validates the player has enough goo, logging rejection if not.
     *
     * @param player  the throwing player
     * @param gooType the goo type to check
     * @param cost    the resolved exact mB cost for this throw, the player's remainder paying first
     * @return true if supply is sufficient
     */
    private static boolean validateSupply(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                          GooFraction cost) {
        if (AbilityCharges.affords(player, gooType, cost)) { return true; }
        logNoGoo(gooType);
        return false;
    }

    private static void logNoGoo(ResourceKey<GooTypeDefinition> gooType) {
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_NO_GOO, GooTypes.id(gooType)); }
    }

    /** Resolves the throw cost from the ability definition, falling back to THROW_COST.
     * A Lead throw releasing the thrower's own led mob costs nothing
     * (decision rally-release-cast-costs-nothing).
     *
     * @param player  the throwing player
     * @param payload the throw payload
     * @param gooType the resolved goo type
     * @return the exact cost in mB for this throw
     */
    static GooFraction resolveThrowCost(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition def = thrownAbility(player.level(), payload.abilityId(), gooType);
        if (def == null) { return GooFraction.of(THROW_COST); }
        Entity aimed = payload.targetEntityId() >= 0 ? player.level().getEntity(payload.targetEntityId()) : null;
        if (LeadRelease.releasesOwnLead(def.behaviors(), player, aimed)) { return GooFraction.ZERO; }
        // ability-cost-builds-as-a-one-type-by-type-value
        return def.cost().exactIn(GooValues.of(player.level()));
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
     * @param cost    the resolved exact mB cost for this throw
     */
    private static void depleteAndThrow(ServerPlayer player, GooThrowPayload payload,
            ResourceKey<GooTypeDefinition> gooType, double distSq, GooFraction cost) {
        if (!AbilityCharges.charge(player, gooType, cost) && Goo.LOGGER.isWarnEnabled()) {
            Goo.LOGGER.warn(LOG_PARTIAL_DEPLETE, cost, GooTypes.id(gooType));
        }
        consumeReagents(player, payload.abilityId(), gooType);

        double distance = Math.sqrt(distSq);
        GooTypeDefinition definition = GooTypes.definition(player.level().registryAccess(), gooType);
        Delivery delivery = flightDelivery(player.level(), payload.abilityId(), gooType);
        // decision lunar-gravity-is-one-sixth-of-the-overworlds: the blob floats longer to the same aim in lunar
        int travelTicks = LunarGravity.flightTicksIn(player.level().dimension(),
                delivery.travelTicks(distance, definition.levity(), definition.baseFlightTime()));
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
        // thrown-blob-leaves-the-hand-with-a-fading-drift: the drift is the known movement, since the server
        // moves a player by its client's packets and leaves its delta movement at rest
        // (decision diagnose-then-restore-razor-harm)
        GooFlightPayload flight = buildFlightPayload(hand, payload, delivery, travelTicks,
                player.getKnownMovement());
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no flight.
        EntityVisuals.sendToWatchers(player, flight);
    }

    /** Builds the flight payload from the throw origin, throw data, and travel time.
     *
     * @param hand        the world-space throw origin, clamped within reach
     * @param payload     the throw payload data
     * @param delivery    the delivery the flight flies by
     * @param travelTicks the number of ticks until arrival
     * @param travel      the thrower's travel velocity, blocks per tick
     * @return the constructed flight payload
     */
    private static GooFlightPayload buildFlightPayload(Vec3 hand, GooThrowPayload payload, Delivery delivery,
            int travelTicks, Vec3 travel) {
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
                payload.targetPoint(),
                payload.gate(),
                payload.facePoint(),
                travel
        );
    }

    /**
     * The hop a throw through a gate makes, where the server finds it true.
     *
     * @param pairs     the server's linked pairs
     * @param gate      the gate the throw names
     * @param facePoint where the throw says its aim met the gate's face
     * @param eye       the thrower's eye
     * @param range     the thrown ability's range in blocks
     * @return the hop, empty where the throw is refused
     */
    static Optional<GateArc.Hop> admittedHop(List<GatePassage.Pair> pairs, Gate gate, Vec3 facePoint, Vec3 eye,
                                             double range) {
        boolean onTheFace = Math.abs(gate.out(facePoint)) <= FACE_SLACK && gate.within(facePoint);
        boolean seenFromTheEye = gate.out(eye) > 0 && AimReach.takes(eye.distanceTo(facePoint), range);
        return onTheFace && seenFromTheEye ? GateRay.hopThrough(pairs, gate, facePoint) : Optional.empty();
    }

    /**
     * The hop a throw's payload names, where the server finds it true.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param range   the thrown ability's range in blocks
     * @return the hop, empty for a throw naming no gate or one the server refuses
     */
    static Optional<GateArc.Hop> hopOf(ServerPlayer player, GooThrowPayload payload, double range) {
        return payload.gate().flatMap(gate -> admittedHop(GatePassage.pairsOf(player.level().getServer()), gate,
                payload.facePoint(), player.getEyePosition(), range));
    }

    /**
     * How far a throw through a gate flies.
     *
     * @param from   where the throw leaves from
     * @param hop    the gate it passes
     * @param target where it lands
     * @return its length in blocks
     */
    static double throughDistance(Vec3 from, GateArc.Hop hop, Vec3 target) {
        return from.distanceTo(hop.facePoint()) + hop.partnerPoint().distanceTo(target);
    }

    /**
     * Returns squared distance from the player to the packet's target.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param range   the thrown ability's range in blocks, which a gate's face must stand within
     * @return squared distance in blocks
     */
    static double targetDistanceSquared(ServerPlayer player, GooThrowPayload payload, double range) {
        if (payload.gate().isPresent()) {
            // aim-and-shots-carry-through-linked-gates: a throw through a gate flies its two legs
            Optional<GateArc.Hop> hop = hopOf(player, payload, range);
            Entity aimed = payload.targetEntityId() >= 0 ? player.level().getEntity(payload.targetEntityId()) : null;
            Vec3 target = aimed != null ? aimed.getBoundingBox().getCenter() : Vec3.atCenterOf(payload.targetPos());
            double length = hop.map(passed -> throughDistance(player.getEyePosition(), passed, target))
                    .orElse(Double.MAX_VALUE);
            return length * length;
        }
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
    public static @Nullable Direction directionFromOrdinal(int ordinal) {
        Direction[] dirs = Direction.values();
        if (ordinal >= 0 && ordinal < dirs.length) {
            return dirs[ordinal];
        }
        return null;
    }
}
