package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of a self delivery: nothing leaves the hand; the ability's
 * cost at stack zero drains from the inventory and its programs run on the
 * invoking player in the same tick (decision self-delivery-runs-on-player).
 */
public final class GooSelfHandler {

    private static final String LOG_NO_GOO = "Self ability {} rejected: insufficient goo";
    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on the player host: {}";

    private GooSelfHandler() {
    }

    /**
     * Invokes a self ability on the player, when the player holds its cost.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     */
    static void invoke(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        int cost = ability.cost();
        if (!GooSourceScanner.hasEnough(player, gooType, cost)) {
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_NO_GOO, ability.id());
            }
            return;
        }
        GooSourceScanner.deplete(player, gooType, cost);
        GooEffectScheduler.playThrowSound(player, ability.delivery());
        try {
            ProgramBehavior.forHost(ability.behaviors(), HostKind.PLAYER).tick(new PlayerHost(player.level(), player));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
        }
    }
}
