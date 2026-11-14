package com.mercuriusxeno.goo.ability.zoo;

import com.mercuriusxeno.goo.Goo;
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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs a rallied mob for its caster until the caster strikes it or casts Rally
 * on it again: it walks to the caster's nearest enemy within reach of the
 * caster, a player who attacked the caster first, and strikes it with the
 * rally's damage on reaching it and every strike interval after; with no enemy
 * in reach it follows the caster. It wears its share of the caster's armor,
 * read again every refresh interval. The caster's own hit or the caster's
 * second Rally cast, both dealing no damage, ends the rally and takes its
 * buffs off; a hit or a cast from anyone else leaves it.
 * zoo-rally-arms-the-peaceful
 * zoo-rally-holds-for-the-brew
 * rally-is-a-mob-effect-on-one-target
 * rallied-mobs-follow-the-player-who-rallied-them
 * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
 * rally-minions-get-half-your-armor-and-a-floor
 * rally-cast-on-your-rallied-mob-releases-it
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RallyEvents {

    /** The modifier a rally lifts max health with. */
    public static final Identifier HEALTH_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "rally_health");
    /** The modifier a rally lifts movement speed with. */
    public static final Identifier SPEED_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "rally_speed");
    /** The modifier a rally lends the rallier's armor with. */
    public static final Identifier ARMOR_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "rally_armor");
    /** The modifier a rally lends the rallier's armor toughness with. */
    public static final Identifier TOUGHNESS_MODIFIER =
            Identifier.fromNamespaceAndPath(Goo.MODID, "rally_armor_toughness");
    /** Ticks between one read of the rallier's armor and the next, a second, so a gear change reaches the mob. */
    static final int ARMOR_REFRESH_TICKS = 20;
    /** How far around its rallier a rallied mob looks for the rallier's enemies, keeping it near the rallier. */
    private static final double FOE_REACH = 8.0;
    /** Ticks between one strike and the next, three quarters of a second. */
    static final int STRIKE_INTERVAL_TICKS = 15;
    private static final double CHASE_SPEED = 1.2;
    /** The pace a rallied mob walks to its rallier at, its buffed speed carrying it. */
    private static final double FOLLOW_SPEED = 1.0;
    /** Ticks between one path to a moving rallier and the next. */
    private static final int REPATH_TICKS = 10;

    private RallyEvents() {
    }

    /**
     * Strikes or chases the caster's enemy while the rally stands.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || !mob.hasData(GooAttachments.RALLIED)
                // rallied-mob-falls-downed-instead-of-dying: a downed mob lies still, fighting and following no one
                || mob.hasData(GooAttachments.DOWNED)) {
            return;
        }
        Rallied rally = mob.getData(GooAttachments.RALLIED);
        // rally-minions-get-half-your-armor-and-a-floor: an offline or absent rallier leaves the last armor read
        if (level.getEntity(rally.caster()) instanceof LivingEntity caster) {
            if (mob.tickCount % ARMOR_REFRESH_TICKS == 0) {
                wearRalliersArmor(mob, caster, rally.armor());
            }
            act(level, mob, caster, rally);
        }
    }

    /**
     * Lends a rallied mob its share of its rallier's armor and toughness as it stands now.
     * rally-minions-get-half-your-armor-and-a-floor
     *
     * @param mob     the rallied mob
     * @param rallier the entity that rallied it
     * @param armor   the rally's armor share and floor
     */
    public static void wearRalliersArmor(Mob mob, LivingEntity rallier, RallyArmor armor) {
        lendModifier(mob.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER,
                armor.armorFrom(rallier.getAttributeValue(Attributes.ARMOR)));
        lendModifier(mob.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_MODIFIER,
                armor.toughnessFrom(rallier.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
    }

    /**
     * Ends a mob's rally on a hit from its rallier, and cancels that hit so
     * it deals no damage; a hit from anyone else lands and leaves the rally
     * standing. Ending a rally isn't an attack: a cancelled hit leaves the
     * rallier recording no struck mob, so its other rallied mobs never take
     * the freed mob as their foe, while a second hit lands and makes it one.
     * rally-is-a-mob-effect-on-one-target
     *
     * @param event the incoming damage event, before the damage lands
     */
    @SubscribeEvent
    public static void onRalliedHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()
                || !mob.hasData(GooAttachments.RALLIED)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (mob.getData(GooAttachments.RALLIED).brokenBy(attacker == null ? null : attacker.getUUID())) {
            event.setCanceled(true);
            endRally(mob);
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
     * Walks a mob toward the one it follows, re-pathing as that one moves:
     * a rallied mob its rallier, a led mob the player holding Kin.
     * rallied-mobs-follow-the-player-who-rallied-them
     * leader-leads-passive-mobs-at-a-short-distance
     *
     * @param mob      the following mob
     * @param followed the entity it follows
     */
    static void follow(Mob mob, LivingEntity followed) {
        if (mob.getNavigation().isDone() || mob.tickCount % REPATH_TICKS == 0) {
            mob.getNavigation().moveTo(followed, FOLLOW_SPEED);
        }
    }

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
     * Takes a rally and its buffs off a mob.
     *
     * @param mob the mob
     */
    public static void endRally(Mob mob) {
        mob.removeData(GooAttachments.RALLIED);
        removeModifier(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER);
        removeModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER);
        removeModifier(mob.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER);
        removeModifier(mob.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_MODIFIER);
        mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
    }

    /**
     * Lifts a mob's max health and speed by the rally's shares, each a
     * multiple of the whole, leaving its health where it stands: a rally
     * heals none of it.
     * zoo-rally-arms-the-peaceful
     *
     * @param mob         the rallied mob
     * @param healthShare the share max health grows by
     * @param speedShare  the share movement speed grows by
     */
    public static void buff(Mob mob, double healthShare, double speedShare) {
        replaceModifier(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER, healthShare);
        replaceModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER, speedShare);
    }

    private static void replaceModifier(@Nullable AttributeInstance attribute, Identifier id, double share) {
        if (attribute != null) {
            attribute.removeModifier(id);
            // zoo-rally-arms-the-peaceful: permanent, so the buff saves with the rally it belongs to
            attribute.addPermanentModifier(new AttributeModifier(id, share,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void lendModifier(@Nullable AttributeInstance attribute, Identifier id, double amount) {
        if (attribute == null) {
            return;
        }
        AttributeModifier standing = attribute.getModifier(id);
        // an unchanged rallier leaves the modifier, so the refresh syncs no attribute to clients
        if (standing == null || Double.compare(standing.amount(), amount) != 0) {
            attribute.removeModifier(id);
            // permanent, so the lent armor saves with the rally it belongs to
            attribute.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
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
                target == null ? null : target.getUUID(), rallied.distanceToSqr(near),
                near.hasData(GooAttachments.DOWNED));
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
