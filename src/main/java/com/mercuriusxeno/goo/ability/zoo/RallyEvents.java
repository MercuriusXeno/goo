package com.mercuriusxeno.goo.ability.zoo;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.held.LaidState;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs a rallied mob for its caster until the rally fades: it walks to the
 * caster's nearest enemy and strikes it with the rally's damage on every
 * strike interval; when the rally fades its buffs come off. While the
 * drinker's brew holds, the rally re-runs around the drinker, and when the
 * brew ends every mob it rallied stands down.
 * zoo-rally-arms-the-peaceful
 * zoo-rally-holds-for-the-brew
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RallyEvents {

    /** The modifier a rally lifts max health with. */
    public static final Identifier HEALTH_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "rally_health");
    /** The modifier a rally lifts movement speed with. */
    public static final Identifier SPEED_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "rally_speed");
    /** How far around itself a rallied mob looks for the caster's enemies. */
    private static final double FOE_REACH = 16.0;
    /** Ticks between one strike and the next, a melee goal's cooldown. */
    static final int STRIKE_INTERVAL_TICKS = 20;
    private static final double CHASE_SPEED = 1.2;
    /** Ticks between one re-run of a held rally and the next, well inside zoo_rally.json's duration. */
    static final int REFRESH_TICKS = 20;
    private static final String LOG_RALLY_REFUSED = "Rally {} refused its re-run: {}";

    private RallyEvents() {
    }

    /**
     * Strikes or chases the caster's enemy while the rally stands, and takes
     * the buffs off once it fades.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || !mob.hasData(GooAttachments.RALLIED)) {
            return;
        }
        Rallied rally = mob.getData(GooAttachments.RALLIED);
        Entity caster = level.getEntity(rally.caster());
        if (standsDown(level, rally, caster)) {
            endRally(mob);
            return;
        }
        if (caster instanceof LivingEntity living) {
            nearestFoe(mob, living).ifPresent(foe -> fight(level, mob, foe, rally.damage()));
        }
    }

    /**
     * Re-runs the rally of each held effect laying it on the drinker every
     * refresh interval, so a peaceful mob coming within reach joins and a
     * rallied mob's expiry stays past the next re-run while the brew holds.
     * zoo-rally-holds-for-the-brew
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.HELD_EFFECTS)) {
            return;
        }
        long now = player.level().getGameTime();
        for (HeldEffects.Held held : player.getData(GooAttachments.HELD_EFFECTS).held()) {
            if (held.lays().contains(LaidState.RALLY) && refreshesOn(held.startedAt(), now)) {
                rerun(player, held.ability());
            }
        }
    }

    /**
     * Answers whether a held rally re-runs on a game time: every refresh
     * interval after the eat that started it, which ran it once already.
     * zoo-rally-holds-for-the-brew
     *
     * @param startedAt the game time the held effect started at
     * @param now       the game time
     * @return true on a refresh tick
     */
    static boolean refreshesOn(long startedAt, long now) {
        long since = now - startedAt;
        return since > 0 && since % REFRESH_TICKS == 0;
    }

    /**
     * Takes the rally off every loaded mob a drinker rallied, the tick the
     * brew laying it ends.
     * zoo-rally-holds-for-the-brew
     *
     * @param player the drinker
     */
    public static void standDown(ServerPlayer player) {
        UUID drinker = player.getUUID();
        List<Mob> rallied = new ArrayList<>();
        for (Entity entity : player.level().getAllEntities()) {
            if (entity instanceof Mob mob && mob.hasData(GooAttachments.RALLIED)
                    && mob.getData(GooAttachments.RALLIED).caster().equals(drinker)) {
                rallied.add(mob);
            }
        }
        rallied.forEach(RallyEvents::endRally);
    }

    /**
     * Answers whether a rallied mob stands down: its rally has faded, or its
     * caster is a drinker whose brew no longer lays the rally, ended by a
     * route that cleared no mob, such as death, or while the mob lay unloaded.
     * zoo-rally-holds-for-the-brew
     *
     * @param level  the mob's level
     * @param rally  the mob's rally
     * @param caster the loaded caster, null when it is not loaded
     * @return true where the mob should stand down
     */
    private static boolean standsDown(ServerLevel level, Rallied rally, @Nullable Entity caster) {
        return !rally.standsAt(level.getGameTime())
                || caster instanceof ServerPlayer player && !holdsTheRally(player);
    }

    private static boolean holdsTheRally(ServerPlayer player) {
        return player.hasData(GooAttachments.HELD_EFFECTS) && player.getData(GooAttachments.HELD_EFFECTS).held()
                .stream().anyMatch(held -> held.lays().contains(LaidState.RALLY));
    }

    /**
     * Runs the steps of an ability's program that rally, leaving the rest,
     * such as its roar, to the eat.
     *
     * @param player  the drinker
     * @param ability the held ability
     */
    private static void rerun(ServerPlayer player, Identifier ability) {
        AbilityDefinition definition = AbilityRegistry.of(player.level()).getAbility(ability);
        if (definition == null) {
            return;
        }
        List<Step> rallying = definition.behaviors().stream()
                .filter(step -> LaidState.laidBy(List.of(step)).contains(LaidState.RALLY)).toList();
        try {
            ProgramBehavior.forHost(rallying, HostKind.PLAYER).tick(new PlayerHost(player.level(), player));
        } catch (ProgramLoadException e) {
            if (Goo.LOGGER.isErrorEnabled()) {
                Goo.LOGGER.error(LOG_RALLY_REFUSED, ability, e.getMessage());
            }
        }
    }

    /**
     * Takes a rally and its buffs off a mob.
     *
     * @param mob the mob
     */
    public static void endRally(Mob mob) {
        mob.removeData(GooAttachments.RALLIED);
        removeModifier(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER);
        removeModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER);
        mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
    }

    /**
     * Lifts a mob's max health and speed by the rally's shares, each a
     * multiple of the whole, and heals it to its new max.
     *
     * @param mob         the rallied mob
     * @param healthShare the share max health grows by
     * @param speedShare  the share movement speed grows by
     */
    public static void buff(Mob mob, double healthShare, double speedShare) {
        replaceModifier(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER, healthShare);
        replaceModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER, speedShare);
        mob.setHealth(mob.getMaxHealth());
    }

    private static void replaceModifier(@Nullable AttributeInstance attribute, Identifier id, double share) {
        if (attribute != null) {
            attribute.removeModifier(id);
            // zoo-rally-arms-the-peaceful: permanent, so the buff saves with the rally it belongs to
            attribute.addPermanentModifier(new AttributeModifier(id, share,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeModifier(@Nullable AttributeInstance attribute, Identifier id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
    }

    private static void fight(ServerLevel level, Mob mob, LivingEntity foe, float damage) {
        mob.setTarget(foe);
        if (!mob.isWithinMeleeAttackRange(foe)) {
            mob.getNavigation().moveTo(foe, CHASE_SPEED);
            return;
        }
        mob.getLookControl().setLookAt(foe);
        if (mob.tickCount % STRIKE_INTERVAL_TICKS == 0) {
            mob.swing(mob.getUsedItemHand());
            foe.hurtServer(level, mob.damageSources().mobAttack(mob), damage);
        }
    }

    private static Optional<LivingEntity> nearestFoe(Mob mob, LivingEntity caster) {
        return RallyFoes.pick(caster.getUUID(), uuidOf(caster.getLastHurtByMob()), uuidOf(caster.getLastHurtMob()),
                mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(FOE_REACH),
                                near -> near != mob && near != caster && !near.hasData(GooAttachments.RALLIED))
                        .stream().map(near -> candidate(mob, near)).toList());
    }

    private static RallyFoes.Candidate<LivingEntity> candidate(Mob rallied, LivingEntity near) {
        LivingEntity target = near instanceof Mob nearMob ? nearMob.getTarget() : null;
        return new RallyFoes.Candidate<>(near, near.getUUID(), near.isAlive(),
                target == null ? null : target.getUUID(), rallied.distanceToSqr(near));
    }

    private static @Nullable UUID uuidOf(@Nullable LivingEntity entity) {
        return entity == null ? null : entity.getUUID();
    }
}
