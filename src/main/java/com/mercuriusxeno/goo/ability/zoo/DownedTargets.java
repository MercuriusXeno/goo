package com.mercuriusxeno.goo.ability.zoo;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import java.util.Optional;

/**
 * Keeps every mob off a downed mob for as long as it lies downed: the
 * moment it goes down each mob holding it as a target, an anger or an attack memory lets it go, and no mob takes it as a
 * target again until it stands or dies.
 * nothing-targets-a-downed-mob
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class DownedTargets {

    /** How far around a downed mob the mobs holding it are found, past any mob's follow range. */
    private static final double HOLDER_REACH = 64.0;

    private DownedTargets() {
    }

    /**
     * Turns a mob away from a downed target it would take.
     *
     * @param event the change target event
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && !target.level().isClientSide() && target.hasData(GooAttachments.DOWNED)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /**
     * Lets every mob near a mob just downed drop it: as its target, its
     * attack memory, its anger and the hurt that would pick it again.
     *
     * @param level  the level
     * @param downed the mob just downed
     */
    static void dropHoldsOn(ServerLevel level, Mob downed) {
        for (Mob holder : level.getEntitiesOfClass(Mob.class, downed.getBoundingBox().inflate(HOLDER_REACH),
                near -> near != downed)) {
            letGo(level, holder, downed);
        }
    }

    private static void letGo(ServerLevel level, Mob holder, Mob downed) {
        if (holder.getTarget() == downed) {
            holder.setTarget(null);
        }
        if (holder.getLastHurtByMob() == downed) {
            holder.setLastHurtByMob(null);
        }
        if (holder instanceof NeutralMob neutral && neutral.isAngryAt(downed, level)) {
            neutral.stopBeingAngry();
        }
        forgetTheAttack(holder.getBrain(), downed);
    }

    private static void forgetTheAttack(Brain<?> brain, Mob downed) {
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).equals(Optional.of(downed))) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
