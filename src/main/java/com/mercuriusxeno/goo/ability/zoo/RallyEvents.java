package com.mercuriusxeno.goo.ability.zoo;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs a rallied mob for its caster until the rally fades: it walks to the
 * caster's nearest enemy and strikes it with the rally's damage on every
 * strike interval; when the rally fades its buffs come off.
 * zoo-rally-arms-the-peaceful
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
        if (!rally.standsAt(level.getGameTime())) {
            endRally(mob);
            return;
        }
        Entity caster = level.getEntity(rally.caster());
        if (caster instanceof LivingEntity living) {
            nearestFoe(mob, living).ifPresent(foe -> fight(level, mob, foe, rally.damage()));
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
