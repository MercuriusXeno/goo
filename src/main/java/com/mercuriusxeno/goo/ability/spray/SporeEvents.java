package com.mercuriusxeno.goo.ability.spray;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Bursts a spored mob's spores from its corpse: a mob dying before its
 * spores fade sprays the ability that spored it once more, around where it
 * fell, sporing what the burst reaches in turn.
 * mycosis-spore-stream-buds-and-poisons
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SporeEvents {

    private SporeEvents() {
    }

    /**
     * Bursts the dying mob's standing spores.
     *
     * @param event the death event
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dying = event.getEntity();
        if (!(dying.level() instanceof ServerLevel level)) {
            return;
        }
        Spored spores = dying.hasData(GooAttachments.SPORED) ? dying.removeData(GooAttachments.SPORED) : null;
        if (spores != null && spores.standsAt(level.getGameTime())) {
            burst(level, dying.position(), spores.burst(), spores.radius());
        }
    }

    /**
     * Bursts an ability's spray from a corpse.
     *
     * @param level   the server level
     * @param fell    where the mob fell
     * @param burstId the ability the corpse bursts
     * @param radius  the burst's reach in blocks
     */
    private static void burst(ServerLevel level, Vec3 fell, Identifier burstId, double radius) {
        AbilityDefinition burst = AbilityRegistry.of(level).getAbility(burstId);
        if (burst != null) {
            SprayPrograms.burst(level, fell, radius, burst);
        }
    }
}
