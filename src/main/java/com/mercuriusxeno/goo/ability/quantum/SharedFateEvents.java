package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.Optional;

/**
 * Runs shared fates: each tick a linked pair mirrors health, each taking
 * the other's change since the last tick, so a hit on either lowers both
 * and a heal of either raises both; a status effect landing on one lands on
 * the other. A pair mirrored to no health falls together.
 * shared-fate-pools-health-and-status
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SharedFateEvents {

    /** Set while an effect is copied to a partner, so the copy is not copied back. */
    private static boolean copying;

    private SharedFateEvents() {
    }

    /**
     * Mirrors a linked pair's health once a tick, from the creature whose id sorts first.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity creature) || !(creature.level() instanceof ServerLevel level)
                || !creature.isAlive()) {
            return;
        }
        Optional<LivingEntity> partner = FateLinks.partnerOf(creature);
        if (partner.isPresent() && creature.getUUID().compareTo(partner.get().getUUID()) < 0) {
            mirror(level, creature, partner.get());
        }
    }

    /**
     * Lands a status effect landing on a linked creature on its partner too.
     *
     * @param event the effect added event
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity creature = event.getEntity();
        if (copying || creature.level().isClientSide()) {
            return;
        }
        FateLinks.partnerOf(creature).ifPresent(partner -> {
            copying = true;
            try {
                partner.addEffect(new MobEffectInstance(event.getEffectInstance()), event.getEffectSource());
            } finally {
                copying = false;
            }
        });
    }

    /**
     * Mirrors one pair: each takes its own change since the last mirror plus its partner's.
     *
     * @param level  the level
     * @param first  one creature
     * @param second its partner
     */
    static void mirror(ServerLevel level, LivingEntity first, LivingEntity second) {
        SharedFate firstFate = first.getData(GooAttachments.SHARED_FATE);
        SharedFate secondFate = second.getData(GooAttachments.SHARED_FATE);
        float firstChange = first.getHealth() - firstFate.lastHealth();
        float secondChange = second.getHealth() - secondFate.lastHealth();
        float firstHealth = settle(level, first, firstFate.lastHealth()
                + SharedFate.mirroredChange(firstChange, secondChange));
        float secondHealth = settle(level, second, secondFate.lastHealth()
                + SharedFate.mirroredChange(secondChange, firstChange));
        remember(first, firstFate, firstHealth);
        remember(second, secondFate, secondHealth);
    }

    // Writing the link syncs it to every watcher, so it is written only when the health moved.
    private static void remember(LivingEntity creature, SharedFate fate, float health) {
        if (Float.compare(health, fate.lastHealth()) != 0) {
            creature.setData(GooAttachments.SHARED_FATE, fate.mirroredAt(health));
        }
    }

    private static float settle(ServerLevel level, LivingEntity creature, float health) {
        if (health <= 0f) {
            creature.kill(level);
            return 0f;
        }
        creature.setHealth(Math.min(health, creature.getMaxHealth()));
        return creature.getHealth();
    }
}
