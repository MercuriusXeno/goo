package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Slings a charged release as a backhand sweep of goo flecks: the flecks
 * fan across a cone in front of the player, leaving the glove hand one
 * after another from the off-hand side to the glove side, their count and
 * spread following the charge. Each fleck flies to the first mob or block
 * on its line, morphing in flight into its delivery's travel form, and a
 * fleck striking a mob runs the ability's program on it.
 * decision shards-sling-then-morph-to-flechettes
 */
public final class FleckSling {

    /** Blocks a fleck flies where the delivery names no range. */
    static final double DEFAULT_RANGE = 16.0;
    /** Blocks below the eye the flecks leave from, the glove hand's height. */
    private static final double HAND_DROP = 0.35;
    /** Blocks ahead of the eye the flecks leave from. */
    private static final double HAND_REACH = 0.5;
    /** Blocks a mob's box is widened by when a fleck's line is swept against it. */
    private static final double SWEEP_INFLATE = 1.0;
    /** Degrees from the look to the player's right, around the vertical. */
    private static final float RIGHT_OF_LOOK = 90f;
    /** The share of the cone either side of the look. */
    private static final double HALF = 0.5;
    /** The sweep's turn for a glove in the right hand, off-hand side first. */
    private static final double LEFT_TO_RIGHT = 1;
    /** The sweep's turn for a glove in the left hand. */
    private static final double RIGHT_TO_LEFT = -LEFT_TO_RIGHT;
    private static final int NO_ENTITY = -1;
    private static final int NO_FACE = -1;

    private static final String LOG_NO_GOO = "Sling rejected: insufficient {} goo";

    private FleckSling() {
    }

    /**
     * Pays the release's cost and reagents once, then queues each fleck of
     * the sweep to leave on its tick.
     *
     * @param player    the releasing player
     * @param gooType   the ability's goo type
     * @param ability   the slinging ability
     * @param heldTicks the ticks the use key was held
     */
    static void sling(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability,
                      int heldTicks) {
        if (!pay(player, gooType, ability)) {
            return;
        }
        Delivery delivery = ability.delivery();
        Charge charge = delivery.charge();
        float share = delivery.chargeShare(heldTicks);
        int count = charge.fleckCount(share);
        Vec3 right = Vec3.directionFromRotation(0f, player.getYRot() + RIGHT_OF_LOOK);
        List<Vec3> fan = fanDirections(player.getViewVector(1f), right, count, charge.spreadDegrees(share),
                gloveOnTheRight(player));

        ServerLevel level = player.level();
        int now = level.getServer().getTickCount();
        FleckLaunches launches = GooServerState.of(level.getServer()).fleckLaunches();
        for (int index = 0; index < count; index++) {
            Vec3 direction = fan.get(index);
            launches.enqueue(now + charge.launchDelay(index, count),
                    () -> launch(player, gooType, ability, direction));
        }
    }

    /**
     * Takes the release's goo and reagents and plays the throw, once for
     * the whole sweep.
     *
     * @param player  the releasing player
     * @param gooType the ability's goo type
     * @param ability the slinging ability
     * @return false where the player lacks the goo, which slings nothing
     */
    private static boolean pay(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                               AbilityDefinition ability) {
        if (!GooSourceScanner.hasEnough(player, gooType, ability.cost())) {
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_NO_GOO, GooTypes.id(gooType));
            }
            return false;
        }
        GooSourceScanner.deplete(player, gooType, ability.cost());
        GooThrowHandler.consumeReagents(player, ability.id().toString(), gooType);
        GooEffectScheduler.playThrowSound(player, ability.delivery());
        return true;
    }

    /**
     * The directions a sweep's flecks fly, in the order they leave: evenly
     * across the cone around the look, from the off-hand side to the glove
     * side, the look alone for a single fleck.
     *
     * @param look          the player's unit look
     * @param right         the unit vector to the player's right, level with the ground
     * @param count         the flecks in the sweep
     * @param spreadDegrees the cone, edge to edge, in degrees
     * @param gloveOnRight  whether the glove hand is the player's right
     * @return a unit direction per fleck, in launch order
     */
    static List<Vec3> fanDirections(Vec3 look, Vec3 right, int count, double spreadDegrees, boolean gloveOnRight) {
        List<Vec3> fan = new ArrayList<>(count);
        if (count <= 1) {
            fan.add(look);
            return fan;
        }
        double spread = Math.toRadians(spreadDegrees);
        double step = spread / (count - 1);
        double sweep = gloveOnRight ? LEFT_TO_RIGHT : RIGHT_TO_LEFT;
        for (int index = 0; index < count; index++) {
            double angle = sweep * (step * index - spread * HALF);
            fan.add(look.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).normalize());
        }
        return fan;
    }

    /**
     * Whether the glove is in the player's right hand: the main hand's arm
     * when the glove is there, the other arm when it is in the off hand.
     *
     * @param player the player
     * @return true for a glove in the right hand
     */
    private static boolean gloveOnTheRight(ServerPlayer player) {
        boolean inMain = player.getMainHandItem().getItem() instanceof GooGloveItem;
        HumanoidArm gloveArm = inMain ? player.getMainArm() : player.getMainArm().getOpposite();
        return gloveArm == HumanoidArm.RIGHT;
    }

    /**
     * Sends one fleck from the glove hand along its direction: the flight
     * goes to the players watching, and a fleck striking a mob lands its
     * ability on the mob when its flight arrives.
     *
     * @param player    the releasing player
     * @param gooType   the ability's goo type
     * @param ability   the slinging ability
     * @param direction the fleck's unit direction
     */
    private static void launch(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability,
                               Vec3 direction) {
        if (player.isRemoved()) {
            return;
        }
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Delivery delivery = ability.delivery();
        Strike strike = strikeAlong(player, eye, direction, delivery.range() > 0 ? delivery.range() : DEFAULT_RANGE);
        Vec3 hand = eye.add(direction.scale(HAND_REACH)).subtract(0, HAND_DROP, 0);
        GooTypeDefinition definition = GooTypes.definition(level.registryAccess(), gooType);
        int travelTicks = delivery.travelTicks(hand.distanceTo(strike.end()), definition.levity(),
                definition.baseFlightTime());
        String abilityId = ability.id().toString();
        EntityVisuals.sendToWatchers(player, new GooFlightPayload(hand.x, hand.y, hand.z, GooTypes.id(gooType),
                strike.entityId(), strike.landing(), strike.face(), travelTicks, false, abilityId, delivery,
                strike.end()));
        if (strike.entityId() != NO_ENTITY) {
            GooServerState.of(level.getServer()).gooEffects().enqueue(new GooEffectScheduler.PendingEffect(
                    level.getServer().getTickCount() + travelTicks, level, player, gooType, strike.entityId(),
                    strike.landing(), null, abilityId, new GooEffectScheduler.Aim(eye, direction)));
        }
    }

    /**
     * What a fleck's line meets: the first living mob before the first
     * block, else the block, else the range's end.
     *
     * @param player    the releasing player, whom the line never strikes
     * @param eye       the line's start
     * @param direction the line's unit direction
     * @param range     the blocks the line reaches
     * @return the strike
     */
    private static Strike strikeAlong(ServerPlayer player, Vec3 eye, Vec3 direction, double range) {
        BlockHitResult wall = player.level().clip(new ClipContext(eye, eye.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = wall.getLocation();
        LivingEntity struck = firstMobOnTheLine(player, eye, stop);
        if (struck != null) {
            return new Strike(struck.getId(), struck.getBoundingBox().getCenter(), struck.blockPosition(), NO_FACE);
        }
        int face = wall.getType() == HitResult.Type.BLOCK ? wall.getDirection().ordinal() : NO_FACE;
        return new Strike(NO_ENTITY, stop, BlockPos.containing(stop), face);
    }

    /**
     * The first living mob a fleck's line meets before it stops.
     *
     * @param player the releasing player, whom the line never strikes
     * @param from   the line's start, the player's eye
     * @param stop   where the line stops, at the first block or the range's end
     * @return the mob, or null for none
     */
    private static @Nullable LivingEntity firstMobOnTheLine(ServerPlayer player, Vec3 from, Vec3 stop) {
        AABB swept = player.getBoundingBox().expandTowards(stop.subtract(from)).inflate(SWEEP_INFLATE);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, stop, swept,
                candidate -> candidate instanceof LivingEntity living && living.isAlive(), from.distanceToSqr(stop));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /**
     * Where a fleck flies and what it strikes there.
     *
     * @param entityId the struck mob's id, or -1 for none
     * @param end      the point the flight flies to
     * @param landing  the block the flight lands in
     * @param face     the struck block face's ordinal, or -1 for none
     */
    private record Strike(int entityId, Vec3 end, BlockPos landing, int face) {
    }
}
