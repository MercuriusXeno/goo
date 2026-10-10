package com.mercuriusxeno.goo.ability.hex;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Runs a charmed mob for its charmer until the charm fades: it fights the
 * nearest mob targeting the charmer, else the nearest monster hostile to
 * players before that monster aggresses; any target it would take that is a
 * player turns to that foe, or to none; with no foe in reach it follows the
 * charmer, and no hit of its lands on a player.
 * charm-glisten-and-icon-over-the-head
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class CharmEvents {

    /** How far around itself a charmed mob looks for a foe. */
    private static final double FOE_REACH = 16.0;
    /** Ticks between one look for a foe and the next while the mob has none. */
    public static final int LOOK_INTERVAL_TICKS = 10;
    /** The distance past which a charmed mob with no foe walks back to its charmer. */
    private static final double FOLLOW_START = 5.0;
    /** The distance within which it stops walking. */
    private static final double FOLLOW_STOP = 2.5;
    private static final double FOLLOW_SPEED = 1.0;

    private CharmEvents() {
    }

    /**
     * Turns a charmed mob away from a player it would target, onto its
     * nearest foe, or onto none.
     *
     * @param event the change target event
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getNewAboutToBeSetTarget() instanceof Player)) {
            return;
        }
        standingCharm(mob).ifPresent(charm ->
                event.setNewAboutToBeSetTarget(nearestFoe(mob, charm).orElse(null)));
    }

    /**
     * Ends a faded charm, and points a charmed mob with no foe, a player
     * target counting as none, at its nearest foe, or at nothing and back
     * to its charmer.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob) {
            standingCharm(mob).filter(charm -> looksForFoe(mob)).ifPresent(charm -> turnOrFollow(mob, charm));
        }
    }

    /**
     * Holds a charmed mob's hits off every player: a slime's touch, which
     * hurts whatever player it bumps whatever it targets, among them.
     *
     * @param event the incoming damage event, before the damage lands
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getSource().getEntity() instanceof Mob mob
                && standingCharm(mob).isPresent()) {
            event.setCanceled(true);
        }
    }

    /**
     * The charm a mob holds on the server while it stands; a faded charm
     * is ended here.
     *
     * @param mob the mob
     * @return the standing charm, or empty for none
     */
    private static Optional<Charmed> standingCharm(Mob mob) {
        if (mob.level().isClientSide() || !mob.hasData(GooAttachments.CHARMED)) {
            return Optional.empty();
        }
        Charmed charm = mob.getData(GooAttachments.CHARMED);
        if (charm.standsAt(mob.level().getGameTime())) {
            return Optional.of(charm);
        }
        mob.removeData(GooAttachments.CHARMED);
        return Optional.empty();
    }

    private static boolean looksForFoe(Mob mob) {
        return !hasFoe(mob.getTarget()) && mob.tickCount % LOOK_INTERVAL_TICKS == 0;
    }

    private static void turnOrFollow(Mob mob, Charmed charm) {
        Optional<Mob> foe = nearestFoe(mob, charm);
        mob.setTarget(foe.orElse(null));
        if (foe.isEmpty()) {
            followCharmer(mob, charm);
        }
    }

    private static boolean hasFoe(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player);
    }

    private static Optional<Mob> nearestFoe(Mob mob, Charmed charm) {
        return CharmTargets.nearestFoe(charm.charmer(), mob.level()
                .getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(FOE_REACH), near -> near != mob)
                .stream().map(near -> candidate(mob, near)).toList());
    }

    private static CharmTargets.Candidate<Mob> candidate(Mob charmed, Mob near) {
        LivingEntity target = near.getTarget();
        return new CharmTargets.Candidate<>(near, near.isAlive(), isUnprovokedHostile(near),
                target == null ? null : target.getUUID(), charmed.distanceToSqr(near));
    }

    /**
     * Whether a mob is a monster hostile to players before it aggresses:
     * an Enemy that is neither neutral until provoked nor charmed itself.
     *
     * @param mob the mob
     * @return true for a foe the charmed mob may strike first
     */
    private static boolean isUnprovokedHostile(Mob mob) {
        return mob instanceof Enemy && !(mob instanceof NeutralMob) && !mob.hasData(GooAttachments.CHARMED);
    }

    private static void followCharmer(Mob mob, Charmed charm) {
        Player charmer = mob.level().getPlayerByUUID(charm.charmer());
        if (charmer == null) {
            return;
        }
        double distance = mob.distanceTo(charmer);
        if (distance > FOLLOW_START) {
            mob.getNavigation().moveTo(charmer, FOLLOW_SPEED);
        } else if (distance < FOLLOW_STOP) {
            mob.getNavigation().stop();
        }
    }
}
