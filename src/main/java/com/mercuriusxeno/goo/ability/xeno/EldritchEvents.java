package com.mercuriusxeno.goo.ability.xeno;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Holds the out-of-phase and the plain apart: an out-of-phase mob takes no
 * player for its target unless that player is eldritch, and a player's
 * eldritch state ends the tick it fades. Until the quantum-goo thread's
 * phased attachment merges, goo's own out-of-phase flag marks the
 * out-of-phase.
 * eldritch-sight-reveals-the-out-of-phase
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class EldritchEvents {

    private EldritchEvents() {
    }

    /**
     * Turns an out-of-phase mob away from a player who is not eldritch.
     *
     * @param event the change target event
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewAboutToBeSetTarget() instanceof Player player
                && !sees(isOutOfPhase(mob), isEldritch(player))) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /**
     * Ends a player's eldritch state the tick it fades.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasData(GooAttachments.ELDRITCH)
                && !isEldritch(player)) {
            end(player);
        }
    }

    /**
     * Whether a player and an entity see each other: anything in phase is
     * seen by all, and the out-of-phase only by the eldritch.
     *
     * @param outOfPhase whether the entity is out of phase
     * @param eldritch   whether the player is eldritch
     * @return true where they see each other
     */
    public static boolean sees(boolean outOfPhase, boolean eldritch) {
        return !outOfPhase || eldritch;
    }

    /**
     * @param entity an entity
     * @return whether it carries goo's out-of-phase flag
     */
    public static boolean isOutOfPhase(Entity entity) {
        return entity.hasData(GooAttachments.OUT_OF_PHASE) && entity.getData(GooAttachments.OUT_OF_PHASE);
    }

    /**
     * @param player a player
     * @return whether the player's eldritch state stands now
     */
    public static boolean isEldritch(Player player) {
        return player.hasData(GooAttachments.ELDRITCH)
                && player.getData(GooAttachments.ELDRITCH).standsAt(player.level().getGameTime());
    }

    /**
     * Ends a player's eldritch state.
     *
     * @param player the player
     */
    public static void end(ServerPlayer player) {
        player.removeData(GooAttachments.ELDRITCH);
    }
}
