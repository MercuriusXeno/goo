package com.mercuriusxeno.goo.ability.spray;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

/**
 * Bursts a spored mob's spores from its corpse: a mob dying before its
 * spores fade sprays the ability that spored it once more, around where it
 * fell, sporing what the burst reaches in turn. Spores goo feeds a death two
 * ways, a mob carrying spores the goo gave it, or one carrying none dying
 * within a Spores cell's death reach; a death the goo fed also turns the
 * ground around it to mycelium and credits the cell's placer for its burst.
 * mycosis-spore-stream-buds-and-poisons
 * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SporeEvents {

    private SporeEvents() {
    }

    /**
     * Bursts the dying mob's standing spores, or, where it carries none, the
     * spores of a Spores cell it dies near.
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
            Entity creditor = spores.creditor().map(id -> GooHitCredit.placerOf(level, id)).orElse(null);
            burst(level, dying.position(), spores.burst(), spores.radius(), creditor, spores.feedsMycelium());
            return;
        }
        // mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium: any mob dying near the goo, not only on it
        if (dying instanceof Mob) {
            SporesDeath.feederNear(level, dying.blockPosition()).ifPresent(feeder -> burst(level, dying.position(),
                    feeder.spores().burst(), feeder.spores().sporeRadius(), feeder.creditor(), true));
        }
    }

    /**
     * Bursts an ability's spray from a corpse, crediting a player, and grows
     * mycelium around it where Spores goo fed the death.
     *
     * @param level         the server level
     * @param fell          where the mob fell
     * @param burstId       the ability the corpse bursts
     * @param radius        the burst's reach in blocks
     * @param creditor      the player the burst credits, or null for none
     * @param feedsMycelium whether Spores goo fed the death
     */
    private static void burst(ServerLevel level, Vec3 fell, Identifier burstId, double radius,
                              @Nullable Entity creditor, boolean feedsMycelium) {
        AbilityDefinition burst = AbilityRegistry.of(level).getAbility(burstId);
        if (burst != null) {
            SprayPrograms.burst(level, fell, radius, burst, creditor);
        }
        if (feedsMycelium) {
            SporesDeath.growMycelium(level, fell, radius);
        }
    }
}
