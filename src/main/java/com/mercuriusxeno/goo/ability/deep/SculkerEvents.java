package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The server's half of Sculker. Each player tick, before the tick moves the
 * player, sneak and jump together take it into the dive and rising out of a
 * top face with jump held takes it out, so the no-clip the tick sets follows
 * the dive the server holds and syncs. For the brew's whole duration no
 * stone suffocates the player, in the dive or out of it, so one whose dive
 * ends inside stone takes no hurt either.
 * decision sculker-swims-solid-blocks-and-surfaces-at-will
 * decision sculker-grants-suffocation-immunity
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SculkerEvents {

    private SculkerEvents() {
    }

    /**
     * Moves a server player into or out of the dive before its tick.
     *
     * @param event the player tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SculkerSwim.updateDive(player);
        }
    }

    /**
     * Cancels in-wall damage on a player whose Sculker stands.
     *
     * @param event the damage about to land
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getSource().is(DamageTypes.IN_WALL)
                && SculkerSwim.dives(player)) {
            event.setCanceled(true);
        }
    }
}
