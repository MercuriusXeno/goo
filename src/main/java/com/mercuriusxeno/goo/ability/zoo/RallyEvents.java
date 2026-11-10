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
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs a rallied mob for its caster until the rally fades: it walks to the
 * caster's nearest enemy within reach of the caster, a player who attacked the
 * caster first, and strikes it with the rally's damage on reaching it and every
 * strike interval after; with no enemy in reach it follows the caster; when
 * the rally fades its buffs come off. While the
 * caster's held effect stands, the rally re-runs around the caster, and when
 * the held effect ends every mob it rallied stands down.
 * zoo-rally-arms-the-peaceful
 * zoo-rally-holds-for-the-brew
 * rallied-mobs-follow-the-player-who-rallied-them
 * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RallyEvents {

    /** The modifier a rally lifts max health with. */
    public static final Identifier HEALTH_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "rally_health");
    /** The modifier a rally lifts movement speed with. */
    public static final Identifier SPEED_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "rally_speed");
    /** How far around its rallier a rallied mob looks for the rallier's enemies, keeping it near the rallier. */
    private static final double FOE_REACH = 8.0;
    /** Ticks between one strike and the next, three quarters of a second. */
    static final int STRIKE_INTERVAL_TICKS = 15;
    private static final double CHASE_SPEED = 1.2;
    /** The pace a rallied mob walks to its rallier at, its buffed speed carrying it. */
    private static final double FOLLOW_SPEED = 1.0;
    /** Ticks between one path to a moving rallier and the next. */
    private static final int REPATH_TICKS = 10;
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
            act(level, mob, living, rally);
        }
    }

    /**
     * Fights the caster's foe in reach, or with none follows the caster.
     * rallied-mobs-follow-the-player-who-rallied-them
     *
     * @param level  the mob's level
     * @param mob    the rallied mob
     * @param caster the entity that rallied it
     * @param rally  the mob's rally
     */
    private static void act(ServerLevel level, Mob mob, LivingEntity caster, Rallied rally) {
        List<RallyFoes.Candidate<LivingEntity>> nearby = candidatesNear(mob, caster);
        Optional<LivingEntity> foe = RallyFoes.pick(caster.getUUID(), standingAttacker(mob, rally, nearby),
                uuidOf(caster.getLastHurtByMob()), uuidOf(caster.getLastHurtMob()), nearby);
        switch (RallyFollow.choose(foe.isPresent(), mob.distanceToSqr(caster))) {
            case FIGHT -> fight(level, mob, foe.orElseThrow(), rally.damage());
            case FOLLOW -> follow(mob, caster);
            case HOLD -> mob.getNavigation().stop();
        }
    }

    /**
     * The attacker the rally records while it stands as a foe, clearing the
     * record once the attacker has died or left the mob's reach.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @param mob    the rallied mob
     * @param rally  the mob's rally
     * @param nearby the mobs in the rallied mob's reach
     * @return the attacker's UUID, or null for none
     */
    private static @Nullable UUID standingAttacker(Mob mob, Rallied rally,
                                                   List<RallyFoes.Candidate<LivingEntity>> nearby) {
        UUID attacker = rally.attacker().orElse(null);
        if (attacker != null && !RallyFoes.attackerStands(attacker, nearby)) {
            mob.setData(GooAttachments.RALLIED, rally.withoutAttacker());
            return null;
        }
        return attacker;
    }

    /**
     * Records another player who damages a rallier, in melee or by a
     * projectile, as the attacker on every mob that rallier rallied, so each
     * turns on the attacker ahead of other foes.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @param event the damage event, after the damage landed
     */
    @SubscribeEvent
    public static void onRallierHurt(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer rallier
                && event.getSource().getEntity() instanceof Player attacker && attacker != rallier) {
            UUID attackerId = attacker.getUUID();
            for (Mob mob : ralliedBy(rallier)) {
                mob.setData(GooAttachments.RALLIED, mob.getData(GooAttachments.RALLIED).withAttacker(attackerId));
            }
        }
    }

    /**
     * Walks a rallied mob toward its rallier, re-pathing as the rallier moves.
     * rallied-mobs-follow-the-player-who-rallied-them
     *
     * @param mob     the rallied mob
     * @param rallier the entity that rallied it
     */
    private static void follow(Mob mob, LivingEntity rallier) {
        if (mob.getNavigation().isDone() || mob.tickCount % REPATH_TICKS == 0) {
            mob.getNavigation().moveTo(rallier, FOLLOW_SPEED);
        }
    }

    /**
     * Re-runs the rally of each held effect laying it on the holder every
     * refresh interval, so a peaceful mob coming within reach joins and a
     * rallied mob's expiry stays past the next re-run while the held effect stands.
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
    /**
     * Answers whether a rallied mob in melee range strikes this tick: once a
     * full cooldown has passed since its last strike, so a mob that has not
     * struck lately strikes the tick it reaches its foe.
     * rallied-mobs-follow-the-player-who-rallied-them
     *
     * @param tickCount      the mob's tick count
     * @param lastStrikeTick the mob's tick count at its last strike, 0 for none
     * @return true on a strike tick
     */
    static boolean strikeReady(int tickCount, int lastStrikeTick) {
        return tickCount - lastStrikeTick >= STRIKE_INTERVAL_TICKS;
    }

    static boolean refreshesOn(long startedAt, long now) {
        long since = now - startedAt;
        return since > 0 && since % REFRESH_TICKS == 0;
    }

    /**
     * Takes the rally off every loaded mob a holder rallied, the tick the
     * held effect laying it ends.
     * zoo-rally-holds-for-the-brew
     *
     * @param player the holder
     */
    public static void standDown(ServerPlayer player) {
        ralliedBy(player).forEach(RallyEvents::endRally);
    }

    /**
     * Every loaded mob a player rallied.
     *
     * @param player the rallier
     * @return the mobs whose rally names the player as caster
     */
    private static List<Mob> ralliedBy(ServerPlayer player) {
        UUID holder = player.getUUID();
        List<Mob> rallied = new ArrayList<>();
        for (Entity entity : player.level().getAllEntities()) {
            if (entity instanceof Mob mob && mob.hasData(GooAttachments.RALLIED)
                    && mob.getData(GooAttachments.RALLIED).caster().equals(holder)) {
                rallied.add(mob);
            }
        }
        return rallied;
    }

    /**
     * Answers whether a rallied mob stands down: its rally has faded, or its
     * caster is a player whose held effect no longer lays the rally, ended by a
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
     * @param player  the holder
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
        // a foe in reach behind a wall or fence takes no strike, as vanilla melee asks line of sight too
        if (!mob.isWithinMeleeAttackRange(foe) || !mob.getSensing().hasLineOfSight(foe)) {
            mob.getNavigation().moveTo(foe, CHASE_SPEED);
            return;
        }
        mob.getLookControl().setLookAt(foe);
        if (strikeReady(mob.tickCount, mob.getLastHurtMobTimestamp())) {
            mob.swing(mob.getUsedItemHand());
            foe.hurtServer(level, mob.damageSources().mobAttack(mob), damage);
            // the strike stamps the mob's last-hurt tick, which strikeReady counts the cooldown from
            mob.setLastHurtMob(foe);
        }
    }

    /**
     * The foes a rallied mob weighs: the living entities within reach of its
     * rallier, so it fights near the rallier, each weighed by its distance
     * from the rallied mob.
     * rallied-mobs-follow-the-player-who-rallied-them
     *
     * @param mob    the rallied mob
     * @param caster the entity that rallied it
     * @return the candidates, the mob, the caster and every rallied mob left out
     */
    private static List<RallyFoes.Candidate<LivingEntity>> candidatesNear(Mob mob, LivingEntity caster) {
        return mob.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(FOE_REACH),
                        near -> near != mob && near != caster && !near.hasData(GooAttachments.RALLIED))
                .stream().map(near -> candidate(mob, near)).toList();
    }

    private static RallyFoes.Candidate<LivingEntity> candidate(Mob rallied, LivingEntity near) {
        LivingEntity target = near instanceof Mob nearMob ? nearMob.getTarget() : null;
        return new RallyFoes.Candidate<>(near, near.getUUID(), near.isAlive(), isUnprovokedHostile(near),
                target == null ? null : target.getUUID(), rallied.distanceToSqr(near));
    }

    /**
     * Whether an entity is a monster hostile to players before it aggresses:
     * an Enemy that is neither neutral until provoked nor charmed, as Charm weighs it.
     *
     * @param near the entity
     * @return true for a hostile monster
     */
    private static boolean isUnprovokedHostile(LivingEntity near) {
        return near instanceof Enemy && !(near instanceof NeutralMob) && !near.hasData(GooAttachments.CHARMED);
    }

    private static @Nullable UUID uuidOf(@Nullable LivingEntity entity) {
        return entity == null ? null : entity.getUUID();
    }
}
