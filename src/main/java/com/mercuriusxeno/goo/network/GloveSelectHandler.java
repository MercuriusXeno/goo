package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for glove selection. Finds the glove in the
 * player's hands and updates its data component so the selection of a
 * type and one of its abilities persists across saves.
 */
public final class GloveSelectHandler {

    /**
     * Log: a selection naming a type and no ability, refused.
     */
    private static final String LOG_NO_ABILITY = "Glove selection of goo type {} names no ability; glove left unchanged";

    private GloveSelectHandler() {}

    /**
     * Applies the selection on the server thread.
     *
     * @param payload the selection payload data
     * @param context the network context
     */
    public static void handle(GloveSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> applySelection(payload, context));
    }

    /** Resolves the glove and applies the type selection.
     *
     * @param payload the selection payload data
     * @param context the network context
     */
    private static void applySelection(GloveSelectPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) { return; }
        ItemStack glove = findGlove(player);
        if (glove == null) { return; }
        resolveAndApply(glove, payload);
    }

    /** Resolves the selection and applies it to the glove: an empty type
     * clears the glove, and a type naming no ability is refused and logged
     * (decision no-throw-without-ability).
     *
     * @param glove   the glove item stack
     * @param payload the selection payload
     */
    static void resolveAndApply(ItemStack glove, GloveSelectPayload payload) {
        if (payload.gooTypeId().isEmpty()) {
            GooGloveItem.setSelection(glove, GloveSelection.EMPTY);
            return;
        }
        ResourceKey<GooTypeDefinition> type = GooTypes.known(payload.gooTypeId());
        if (type == null) { return; }
        if (payload.abilityId().isEmpty()) {
            Goo.LOGGER.warn(LOG_NO_ABILITY, payload.gooTypeId());
            return;
        }
        GooGloveItem.setSelection(glove, new GloveSelection(payload.gooTypeId(), payload.abilityId()));
    }

    /**
     * Finds a glove in main hand or offhand.
     *
     * @param player the interacting player
     * @return the glove item stack, or null if not holding one
     */
    private static ItemStack findGlove(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GooGloveItem) { return main; }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GooGloveItem) { return off; }
        return null;
    }
}
