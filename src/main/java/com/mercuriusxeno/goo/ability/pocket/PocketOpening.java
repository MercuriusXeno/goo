package com.mercuriusxeno.goo.ability.pocket;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/**
 * Opens a player's Pocket as a container.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public final class PocketOpening {

    private static final String TITLE = "goo.pocket.title";

    private PocketOpening() {
    }

    /**
     * Opens the player's pocket.
     *
     * @param player   the pocket's owner
     * @param slotCost the void each slot the stored items fill reserves
     */
    public static void open(ServerPlayer player, int slotCost) {
        PocketVault vault = new PocketVault(player, PocketShelves.get(player.level()), slotCost);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, opener) ->
                new PocketMenu(containerId, inventory, vault), Component.translatable(TITLE)));
    }
}
