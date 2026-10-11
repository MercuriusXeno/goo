package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Keeps phased entities on their own plane: a hit lands only between two
 * entities on the same side of phase, a mob never takes a target across it,
 * and a mob whose target crosses it drops that target. A faded phase is
 * ended here.
 * phase-shares-a-plane-between-the-phased
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PhaseEvents {

    private PhaseEvents() {
    }

    /**
     * Cancels a hit dealt across phase, by the attacker or by its projectile.
     *
     * @param event the incoming damage event, before the damage lands
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.level().isClientSide() && crossesPhase(event.getSource(), phased(victim))) {
            event.setCanceled(true);
        }
    }

    /**
     * Turns a mob away from a target across phase.
     *
     * @param event the change target event
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && !OutOfPhase.sharePlane(phased(event.getEntity()), phased(target))) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /**
     * Drops a mob's standing target once phase lies between them, as when
     * the mob or its target is phased after the mob took aim.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) {
            LivingEntity target = mob.getTarget();
            if (target != null && !OutOfPhase.sharePlane(phased(mob), phased(target))) {
                mob.setTarget(null);
            }
        }
    }

    /**
     * Whether an entity stands out of phase on the server; a faded phase is ended here.
     *
     * @param entity the entity, or null for none
     * @return true while its phase stands
     */
    public static boolean phased(@Nullable Entity entity) {
        if (entity == null || entity.level().isClientSide() || !entity.hasData(GooAttachments.OUT_OF_PHASE)) {
            return false;
        }
        if (entity.getData(GooAttachments.OUT_OF_PHASE).standsAt(entity.level().getGameTime())) {
            return true;
        }
        entity.removeData(GooAttachments.OUT_OF_PHASE);
        return false;
    }

    // A projectile stands on its shooter's plane, so the shooter judges the hit; a hit with no entity behind it
    // comes from the world, which phase leaves to the hit's own source.
    private static boolean crossesPhase(DamageSource source, boolean victimPhased) {
        Entity actor = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        return actor != null && !OutOfPhase.sharePlane(phased(actor), victimPhased);
    }
}
