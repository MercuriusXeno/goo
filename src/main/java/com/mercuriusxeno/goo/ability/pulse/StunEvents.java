package com.mercuriusxeno.goo.ability.pulse;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.List;

/**
 * Stuns a mob Zap reaches and wakes it when the stun ends: the stun drops
 * the mob's target and the memories a brain hunts by, halts its path, and
 * holds its AI off until the wake.
 * zap-ticks-the-device-and-stuns
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class StunEvents {

    /** The memories a brain-driven mob attacks or flees by. */
    private static final List<MemoryModuleType<?>> HUNT_MEMORIES = List.of(MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.ANGRY_AT, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.NEAREST_ATTACKABLE, MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET);

    private StunEvents() {
    }

    /**
     * Stuns a living thing for a number of ticks; one that is no mob has no
     * AI to stun and is left alone.
     *
     * @param target the living thing Zap reached
     * @param ticks  the ticks the stun lasts
     */
    public static void stun(LivingEntity target, int ticks) {
        if (!(target instanceof Mob mob)) {
            return;
        }
        Stunned standing = mob.hasData(GooAttachments.STUNNED) ? mob.getData(GooAttachments.STUNNED) : null;
        mob.setData(GooAttachments.STUNNED,
                Stunned.renewed(standing, mob.level().getGameTime() + ticks, mob.isNoAi()));
        forget(mob);
        mob.setNoAi(true);
    }

    /**
     * Wakes each stunned mob whose stun has ended, its AI back as it stood
     * before the stun.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob) || !mob.hasData(GooAttachments.STUNNED)) {
            return;
        }
        Stunned stunned = mob.getData(GooAttachments.STUNNED);
        if (stunned.wokeBy(mob.level().getGameTime())) {
            mob.removeData(GooAttachments.STUNNED);
            mob.setNoAi(stunned.wasNoAi());
        }
    }

    /**
     * Makes a mob forget what it was doing: its target, the mob that hurt
     * it, its path, and the memories its brain hunts by.
     *
     * @param mob the stunned mob
     */
    private static void forget(Mob mob) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        Brain<?> brain = mob.getBrain();
        HUNT_MEMORIES.forEach(brain::eraseMemory);
    }
}
