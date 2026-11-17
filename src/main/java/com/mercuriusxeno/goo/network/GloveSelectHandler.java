package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for glove selection. Finds the player's caster, the
 * glove or raw goo held bare handed, and updates its data component so the
 * selection of a type and one of its abilities persists across saves; raw
 * goo takes a selection of its own type alone
 * (decision raw-goo-items-work-bare-handed-for-the-held-type).
 */
public final class GloveSelectHandler {

    /**
     * Log: a selection naming a type and no ability, refused.
     */
    private static final String LOG_NO_ABILITY = "Glove selection of goo type {} names no ability; glove left unchanged";

    /**
     * Log: a selection naming an ability the player may not use, refused.
     */
    private static final String LOG_UNUSABLE = "Glove selection of ability {} refused for {}; glove left unchanged";

    private GloveSelectHandler() {}

    /**
     * Applies the selection on the server thread.
     *
     * @param payload the selection payload data
     * @param context the network context
     */
    public static void handle(GloveSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                select(player, payload);
            }
        });
    }

    /** Resolves the caster and applies the type selection.
     *
     * @param player  the selecting player
     * @param payload the selection payload data
     */
    public static void select(ServerPlayer player, GloveSelectPayload payload) {
        ItemStack glove = GooCasters.casterOf(player);
        if (glove == null) { return; }
        resolveAndApply(player, glove, payload);
    }

    /** Resolves the selection and applies it to the glove: an empty type
     * clears the glove, and a type naming no ability, or an ability the
     * player may not use, is refused and logged.
     * decision no-throw-without-ability
     * decision ability-hidden-until-recipes-known
     *
     * @param player  the selecting player
     * @param glove   the glove item stack
     * @param payload the selection payload
     */
    static void resolveAndApply(ServerPlayer player, ItemStack glove, GloveSelectPayload payload) {
        if (payload.gooTypeId().isEmpty()) {
            GooGloveItem.setSelection(glove, GloveSelection.EMPTY);
            return;
        }
        ResourceKey<GooTypeDefinition> type = GooTypes.known(payload.gooTypeId());
        if (type == null || !GooCasters.castsType(player, type)) { return; }
        if (payload.abilityId().isEmpty()) {
            Goo.LOGGER.warn(LOG_NO_ABILITY, payload.gooTypeId());
            return;
        }
        if (GooThrowHandler.usableAbility(player, payload.abilityId(), type) == null) {
            Goo.LOGGER.warn(LOG_UNUSABLE, payload.abilityId(), player.getName().getString());
            return;
        }
        GooGloveItem.setSelection(glove, new GloveSelection(payload.gooTypeId(), payload.abilityId()));
    }
}
