package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import java.util.List;

/**
 * Throws a charged Shards release as a rain of glass knives: the glove arm
 * flings out, the knives stream from the hand over the sweep, each into
 * its own slice of the cone with scattered pitch and speed, and each arcs
 * under gravity until it sticks in a block, strikes a mob, or is spent. A
 * knife striking a mob runs the ability's program on it when it arrives.
 * decision shards-sling-then-morph-to-flechettes
 */
public final class KnifeRainSling {

    /** Blocks below the eye the knives leave from, the glove hand's height. */
    private static final double HAND_DROP = 0.3;
    /** Blocks toward the glove side the knives leave from. */
    private static final double HAND_SIDE = 0.35;
    /** Blocks ahead of the eye the knives leave from. */
    private static final double HAND_REACH = 0.4;
    /** Blocks a mob's box is widened by when a knife's path is swept against it. */
    private static final double KNIFE_REACH = 0.25;
    /** Degrees from the look to the player's right, around the vertical. */
    private static final float RIGHT_OF_LOOK = 90f;
    private static final float FLING_VOLUME = 0.9f;
    private static final float FLING_PITCH = 1.5f;
    private static final float CHIME_VOLUME = 1f;
    private static final float CHIME_PITCH = 1.3f;
    private static final int NO_ENTITY = -1;

    private static final String LOG_NO_GOO = "Knife rain rejected: insufficient {} goo";

    private KnifeRainSling() {
    }

    /**
     * Pays the release's cost and reagents once, flings the glove arm and
     * queues each knife to leave the hand on its tick, scattered by the
     * player's own random.
     *
     * @param player    the releasing player
     * @param gooType   the ability's goo type
     * @param ability   the slinging ability
     * @param heldTicks the ticks the use key was held
     */
    static void sling(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability,
                      int heldTicks) {
        sling(player, gooType, ability, heldTicks, player.getRandom());
    }

    /**
     * Pays the release's cost and reagents once, flings the glove arm and
     * queues each knife to leave the hand on its tick.
     *
     * @param player    the releasing player
     * @param gooType   the ability's goo type
     * @param ability   the slinging ability
     * @param heldTicks the ticks the use key was held
     * @param random    the scatter's source
     */
    public static void sling(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability,
                             int heldTicks, RandomSource random) {
        if (!pay(player, gooType, ability)) {
            return;
        }
        Delivery delivery = ability.delivery();
        Charge charge = delivery.charge();
        float share = delivery.chargeShare(heldTicks);
        int count = charge.fleckCount(share);
        List<Vec3> velocities = KnifeRain.scatteredVelocities(player.getYRot(), player.getXRot(), count,
                charge.spreadDegrees(share), random);
        int[] launchTicks = KnifeRain.launchTicks(count, charge.sweepTicks(), random);
        boolean gloveRight = gloveOnTheRight(player);
        EntityVisuals.sendToWatchers(player, new ArmPosePayload(player.getId(), ArmPoseKind.FLING, gloveRight));

        int now = player.level().getServer().getTickCount();
        FleckLaunches launches = GooServerState.of(player.level().getServer()).fleckLaunches();
        for (int index = 0; index < count; index++) {
            Vec3 velocity = velocities.get(index);
            launches.enqueue(now + launchTicks[index], () -> launch(player, gooType, ability, velocity, gloveRight));
        }
    }

    /**
     * Takes the release's goo and reagents and plays the fling, once for the
     * whole rain: a sweep of the arm and a chime of glass, never a snowball.
     *
     * @param player  the releasing player
     * @param gooType the ability's goo type
     * @param ability the slinging ability
     * @return false where the player lacks the goo, which throws nothing
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
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, FLING_VOLUME, FLING_PITCH);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.PLAYERS, CHIME_VOLUME, CHIME_PITCH);
        return true;
    }

    /**
     * Whether the glove is in the player's right hand: the main hand's arm
     * when the glove is there, the other arm when it is in the off hand.
     *
     * @param player the player
     * @return true for a glove in the right hand
     */
    static boolean gloveOnTheRight(ServerPlayer player) {
        boolean inMain = player.getMainHandItem().getItem() instanceof GooGloveItem;
        HumanoidArm gloveArm = inMain ? player.getMainArm() : player.getMainArm().getOpposite();
        return gloveArm == HumanoidArm.RIGHT;
    }

    /**
     * Sends one knife from the glove hand: its flight goes to the players
     * watching, and a knife striking a mob lands its ability on the mob when
     * it arrives.
     *
     * @param player     the releasing player
     * @param gooType    the ability's goo type
     * @param ability    the slinging ability
     * @param velocity   the knife's velocity leaving the hand
     * @param gloveRight whether the glove is in the right hand
     */
    private static void launch(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                               AbilityDefinition ability, Vec3 velocity, boolean gloveRight) {
        if (player.isRemoved()) {
            return;
        }
        ServerLevel level = player.level();
        Vec3 start = handOf(player, gloveRight);
        Flight flight = fly(player, start, velocity);
        EntityVisuals.sendToWatchers(player, new GlassKnifePayload(start, velocity, flight.ticks(), flight.end(),
                flight.ending().ordinal()));
        if (flight.struck() != null) {
            LivingEntity struck = flight.struck();
            GooServerState.of(level.getServer()).gooEffects().enqueue(new GooEffectScheduler.PendingEffect(
                    level.getServer().getTickCount() + flight.ticks(), level, player, gooType, struck.getId(),
                    struck.blockPosition(), null, ability.id().toString(),
                    new GooEffectScheduler.Aim(start, KnifeRain.velocityAt(velocity, flight.ticks()).normalize())));
        }
    }

    /**
     * The glove hand's point, where the knives leave from.
     *
     * @param player     the player
     * @param gloveRight whether the glove is in the right hand
     * @return the point
     */
    private static Vec3 handOf(ServerPlayer player, boolean gloveRight) {
        Vec3 right = Vec3.directionFromRotation(0f, player.getYRot() + RIGHT_OF_LOOK);
        return player.getEyePosition().add(player.getViewVector(1f).scale(HAND_REACH))
                .add(right.scale(gloveRight ? HAND_SIDE : -HAND_SIDE)).subtract(0, HAND_DROP, 0);
    }

    /**
     * Flies a knife tick by tick along its arc until it meets a block or a
     * living mob, or its flight is spent.
     *
     * @param player   the thrower, whom no knife strikes
     * @param start    where it leaves the hand
     * @param velocity its velocity leaving the hand
     * @return how and where its flight ends
     */
    static Flight fly(ServerPlayer player, Vec3 start, Vec3 velocity) {
        Vec3 position = start;
        Vec3 moving = velocity;
        for (int tick = 1; tick <= KnifeRain.MOST_FLIGHT_TICKS; tick++) {
            Vec3 next = position.add(moving);
            BlockHitResult wall = player.level().clip(new ClipContext(position, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            Vec3 stop = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : next;
            LivingEntity struck = firstMobOn(player, position, stop);
            if (struck != null) {
                return new Flight(tick, struck.getBoundingBox().clip(position, stop).orElse(stop),
                        GlassKnifePayload.Ending.STRUCK, struck);
            }
            if (wall.getType() == HitResult.Type.BLOCK) {
                return new Flight(tick, stop, GlassKnifePayload.Ending.STUCK, null);
            }
            position = next;
            moving = KnifeRain.nextVelocity(moving);
        }
        return new Flight(KnifeRain.MOST_FLIGHT_TICKS, position, GlassKnifePayload.Ending.SPENT, null);
    }

    /**
     * The first living mob a knife's path meets in one tick.
     *
     * @param player the thrower, whom no knife strikes
     * @param from   where the knife is
     * @param stop   where its tick's path stops
     * @return the mob, or null for none
     */
    private static @Nullable LivingEntity firstMobOn(ServerPlayer player, Vec3 from, Vec3 stop) {
        AABB swept = new AABB(from, stop).inflate(KNIFE_REACH);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, from, stop, swept,
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != player,
                (float) KNIFE_REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /**
     * How a knife's flight ends.
     *
     * @param ticks  the ticks it flies
     * @param end    where it ends
     * @param ending how it ends
     * @param struck the mob it strikes, or null
     */
    record Flight(int ticks, Vec3 end, GlassKnifePayload.Ending ending, @Nullable LivingEntity struck) {
    }
}
