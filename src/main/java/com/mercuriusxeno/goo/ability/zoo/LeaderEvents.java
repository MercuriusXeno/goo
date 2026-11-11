package com.mercuriusxeno.goo.ability.zoo;

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
 * Leads the passive mobs around a player holding Leader each server tick:
 * each walks to the player and holds a short distance from it. Leader sets
 * no target and lays no buff, so a led mob that is hurt keeps its vanilla
 * reaction, and a rallied mob is left to RallyEvents. Once Leader ends the
 * mobs receive no further move and wander as before.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LeaderEvents {

    private static final Set<EntityFilter> PEACEFUL = Set.of(EntityFilter.PEACEFUL);

    private LeaderEvents() {
    }

    /**
     * Moves each mob in reach of a player holding Leader.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Leader leader = player.getData(GooAttachments.LEADER);
        if (!leader.stands()) {
            return;
        }
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(leader.radius()))) {
            moveFor(mob, player, leader);
        }
    }

    /**
     * Gives one mob in reach its move from Leader.
     *
     * @param mob    the mob
     * @param player the player holding Leader
     * @param leader the Leader the player holds
     */
    private static void moveFor(Mob mob, ServerPlayer player, Leader leader) {
        LeaderFollow.Move move = LeaderFollow.choose(EntityScan.passes(mob, PEACEFUL, player),
                mob.hasData(GooAttachments.RALLIED), mob.distanceToSqr(player), leader);
        switch (move) {
            case LEAD -> RallyEvents.follow(mob, player);
            case HOLD -> mob.getNavigation().stop();
            case LEAVE_ALONE -> { }
        }
    }
}
