package com.mercuriusxeno.goo.ability.spray;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Bursts a spored mob's spores from its corpse: a mob dying before its
 * spores fade sprays the ability that spored it once more, around where it
 * fell, sporing what the burst reaches in turn
 * (decision mycosis-spore-stream-buds-and-poisons).
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
        if (!(dying.level() instanceof ServerLevel level) || !dying.hasData(GooAttachments.SPORED)) {
            return;
        }
        Spored spores = dying.removeData(GooAttachments.SPORED);
        if (spores == null || !spores.standsAt(level.getGameTime())) {
            return;
        }
        AbilityDefinition burst = AbilityRegistry.of(level).getAbility(spores.burst());
        if (burst != null) {
            SprayPrograms.burst(level, dying.position(), spores.radius(), burst);
        }
    }
}
