package com.mercuriusxeno.goo.ability.yarn;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.EntityScan;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.Set;

/**
 * Herds the passive mobs around a player holding Herd each server tick:
 * each walks to the player and holds a short distance from it. Herd sets
 * no target and lays no buff, so a led mob that is hurt keeps its vanilla
 * reaction, and a led mob is left to LeadEvents. Once Herd ends the
 * mobs receive no further move and wander as before.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class HerdEvents {

    private static final Set<EntityFilter> PEACEFUL = Set.of(EntityFilter.PEACEFUL);

    private HerdEvents() {
    }

    /**
     * Moves each mob in reach of a player holding Herd.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Herd herd = player.getData(GooAttachments.HERD);
        if (!herd.stands()) {
            return;
        }
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(herd.radius()))) {
            moveFor(mob, player, herd);
        }
    }

    /**
     * Gives one mob in reach its move from Herd.
     *
     * @param mob    the mob
     * @param player the player holding Herd
     * @param herd the Herd the player holds
     */
    private static void moveFor(Mob mob, ServerPlayer player, Herd herd) {
        HerdFollow.Move move = HerdFollow.choose(EntityScan.passes(mob, PEACEFUL, player),
                mob.hasData(GooAttachments.LED), mob.distanceToSqr(player), herd);
        switch (move) {
            case APPROACH -> LeadEvents.follow(mob, player);
            case HOLD -> mob.getNavigation().stop();
            case LEAVE_ALONE -> { }
        }
    }
}
