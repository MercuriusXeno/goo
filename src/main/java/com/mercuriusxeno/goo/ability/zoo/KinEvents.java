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
 * Leads the passive mobs around a player holding Kin each server tick:
 * each walks to the player and holds a short distance from it. Kin sets
 * no target and lays no buff, so a led mob that is hurt keeps its vanilla
 * reaction, and a rallied mob is left to RallyEvents. Once Kin ends the
 * mobs receive no further move and wander as before.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class KinEvents {

    private static final Set<EntityFilter> PEACEFUL = Set.of(EntityFilter.PEACEFUL);

    private KinEvents() {
    }

    /**
     * Moves each mob in reach of a player holding Kin.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Kin kin = player.getData(GooAttachments.KIN);
        if (!kin.stands()) {
            return;
        }
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(kin.radius()))) {
            moveFor(mob, player, kin);
        }
    }

    /**
     * Gives one mob in reach its move from Kin.
     *
     * @param mob    the mob
     * @param player the player holding Kin
     * @param kin the Kin the player holds
     */
    private static void moveFor(Mob mob, ServerPlayer player, Kin kin) {
        KinFollow.Move move = KinFollow.choose(EntityScan.passes(mob, PEACEFUL, player),
                mob.hasData(GooAttachments.RALLIED), mob.distanceToSqr(player), kin);
        switch (move) {
            case LEAD -> RallyEvents.follow(mob, player);
            case HOLD -> mob.getNavigation().stop();
            case LEAVE_ALONE -> { }
        }
    }
}
