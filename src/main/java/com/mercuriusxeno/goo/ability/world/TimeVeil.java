package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.stasis.StasisEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Slows time inside a chronosphere: every non-player living entity and
 * every projectile keeps only a share of its motion each tick, and a living
 * one wears an unseen slowness as deep as the share, so a mob's own stride
 * crawls too; a mob also thinks at the share of its pace, its AI running one
 * tick in so many, so its attacks, aim and turns crawl with its stride;
 * players pass through untouched.
 * chronosphere-hastes-players-slows-mobs
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class TimeVeil {

    /** Ticks the slowness outlasts the veil's last touch, so it holds between ticks and ends soon after. */
    static final int SLOWNESS_HOLD_TICKS = 3;
    /** The speed a level of slowness takes away. */
    private static final double SLOWNESS_PER_LEVEL = 0.15;
    /** Slack under a whole number of slowness levels, so a share landing on one does not round up past it. */
    private static final double LEVEL_SLACK = 1e-9;
    private static final int MAX_AMPLIFIER = 255;

    /** The mobs the veil holds, each until its veil lets go, by mob; lost on a restart with the veil's hold. */
    private static final Map<Mob, Veiled> VEILED = new WeakHashMap<>();

    /**
     * One mob the veil holds.
     *
     * @param until    the game time the veil lets it go unless it touches it again
     * @param aiPeriod the ticks between one AI tick it runs and the next
     * @param aiWasOff true when the mob had no AI before the veil, which the veil then leaves alone
     */
    record Veiled(long until, int aiPeriod, boolean aiWasOff) {
    }

    private TimeVeil() {
    }

    /**
     * Answers whether the veil slows an entity: a living entity or a
     * projectile, never a player.
     *
     * @param entity the entity
     * @return true when the veil slows it
     */
    static boolean slows(Entity entity) {
        return !(entity instanceof Player) && (entity instanceof LivingEntity || entity instanceof Projectile);
    }

    /**
     * The slowness amplifier that leaves a living entity about the share of
     * its speed the veil leaves its motion: each level takes 15 percent.
     *
     * @param slow the share of its motion kept, 0 to 1
     * @return the amplifier, zero for the lightest slowness
     */
    static int slownessAmplifier(double slow) {
        int levels = (int) Math.ceil((1.0 - slow) / SLOWNESS_PER_LEVEL - LEVEL_SLACK);
        return Math.clamp(levels - 1, 0, MAX_AMPLIFIER);
    }

    /**
     * The ticks between one AI tick a veiled mob runs and the next, so it
     * thinks at the share of its pace the veil leaves it.
     *
     * @param slow the share of its motion kept, 0 to 1
     * @return the period, one for no slowing
     */
    static int aiPeriod(double slow) {
        return slow <= 0 ? MAX_AMPLIFIER : Math.max(1, (int) Math.round(1.0 / slow));
    }

    /**
     * Whether a veiled mob runs its AI this tick.
     *
     * @param now    the game time
     * @param period the ticks between its AI ticks
     * @return true on one tick in each period
     */
    static boolean thinksAt(long now, int period) {
        return Math.floorMod(now, period) == 0;
    }

    /**
     * Slows what the veil holds this tick.
     *
     * @param level  the level
     * @param center the veil's center
     * @param radius the veil's radius in blocks
     * @param slow   the share of its motion each keeps, 0 to 1
     */
    public static void slowWithin(ServerLevel level, Vec3 center, double radius, double slow) {
        if (radius <= 0) {
            return;
        }
        double radiusSquared = radius * radius;
        AABB reach = new AABB(center, center).inflate(radius);
        int amplifier = slownessAmplifier(slow);
        long until = level.getGameTime() + SLOWNESS_HOLD_TICKS;
        for (Entity entity : level.getEntities((Entity) null, reach,
                found -> slows(found) && found.getBoundingBox().getCenter().distanceToSqr(center) <= radiusSquared)) {
            slowOne(entity, slow, amplifier, until);
        }
    }

    /**
     * Slows one entity the veil holds this tick: its motion, its stride and,
     * for a mob, the pace it thinks at.
     *
     * @param entity    the entity
     * @param slow      the share of its motion kept, 0 to 1
     * @param amplifier the slowness amplifier for that share
     * @param until     the game time the veil lets it go unless it touches it again
     */
    private static void slowOne(Entity entity, double slow, int amplifier, long until) {
        entity.setDeltaMovement(entity.getDeltaMovement().scale(slow));
        entity.hurtMarked = true;
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_HOLD_TICKS, amplifier,
                    true, false, false));
        }
        if (entity instanceof Mob mob) {
            VEILED.compute(mob, (held, veiled) -> new Veiled(until, aiPeriod(slow),
                    veiled == null ? mob.isNoAi() : veiled.aiWasOff()));
        }
    }

    /**
     * Runs a veiled mob's AI one tick in each period and hands it back once
     * the veil lets go; a mob held still by another ability is left to it.
     *
     * @param event the entity tick event, before the entity ticks
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        Veiled veiled = VEILED.get(mob);
        if (veiled != null && !veiled.aiWasOff()) {
            paceAi(mob, veiled, mob.level().getGameTime());
        }
    }

    /**
     * Sets whether a veiled mob thinks this tick, handing its AI back once the
     * veil lets go, and dropping it where another ability holds it still.
     *
     * @param mob    the veiled mob
     * @param veiled how the veil holds it
     * @param now    the game time
     */
    private static void paceAi(Mob mob, Veiled veiled, long now) {
        if (heldStillElsewhere(mob)) {
            VEILED.remove(mob);
        } else if (now > veiled.until()) {
            VEILED.remove(mob);
            mob.setNoAi(false);
        } else {
            mob.setNoAi(!thinksAt(now, veiled.aiPeriod()));
        }
    }

    private static boolean heldStillElsewhere(Mob mob) {
        return StasisEvents.held(mob) || mob.hasData(GooAttachments.REWINDING);
    }
}
