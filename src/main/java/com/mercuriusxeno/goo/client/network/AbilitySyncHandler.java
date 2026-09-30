package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Client-side handler for the ability sync payload. Hands the abilities to
 * the connection, which holds them for its life, and reads them back for the
 * radial menu and the throw without server-side registry access
 * (decision type-package-and-per-server-holders).
 */
public final class AbilitySyncHandler {

    private static final String LOG_SYNCED = "Synced {} abilities from server";

    private AbilitySyncHandler() {
    }

    /**
     * Hands the sync payload to the connection on the client thread.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(AbilitySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ((ClientAbilityConnection) context.listener()).receiveClientAbilities(ClientAbilities.fromPayload(payload));
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_SYNCED, payload.entries().size());
            }
        });
    }

    /**
     * Returns the abilities available for a goo type on the client.
     *
     * @param type the goo type
     * @return immutable list, empty if none synced
     */
    public static List<ClientAbility> getAbilitiesForType(ResourceKey<GooTypeDefinition> type) {
        return ClientAbilities.current().forType(type);
    }

    /**
     * Returns the synced ability an id names.
     *
     * @param abilityId the ability resource id string
     * @return the ability, or null when none synced under that id
     */
    public static @Nullable ClientAbility findAbility(String abilityId) {
        return ClientAbilities.current().find(abilityId);
    }

    /**
     * Returns true if the goo type has any synced abilities.
     *
     * @param type the goo type
     * @return true if at least one ability is available
     */
    public static boolean hasAbilities(ResourceKey<GooTypeDefinition> type) {
        return !getAbilitiesForType(type).isEmpty();
    }

    /**
     * A lightweight client-side ability descriptor.
     *
     * @param id          the ability resource identifier
     * @param displayName the translation key
     * @param icon        the icon texture path override (empty for convention path)
     * @param order       the sort order
     * @param tags        categorical tags for targeting and display
     * @param fuseTicks   the chain block's full fuse
     * @param maxStacks   the chain block's stack ceiling
     * @param behaviors   the ability's step program, whose params the marker's renderers read
     * @param cost        the mB a throw costs, the same at every stack count
     * @param delivery    how the ability leaves the glove
     */
    public record ClientAbility(Identifier id, String displayName, String icon,
                                int order, List<String> tags, int fuseTicks, int maxStacks,
                                List<Step> behaviors, int cost, Delivery delivery) {

        /**
         * Builds the client descriptor from a synced entry.
         *
         * @param entry the synced entry
         * @return the client ability
         */
        public static ClientAbility fromEntry(AbilitySyncPayload.Entry entry) {
            return new ClientAbility(Identifier.tryParse(entry.abilityId()), entry.displayName(), entry.icon(),
                    entry.order(), entry.tags(), entry.fuseTicks(), entry.maxStacks(), entry.behaviors(),
                    entry.cost(), entry.delivery());
        }

        /**
         * Prices a throw the way the server does: the synced flat cost,
         * whatever the target already holds (decision flat-cost-per-throw).
         *
         * @param existingStacks the stacks the target marker already holds
         * @return the cost in mB
         */
        public int throwCost(int existingStacks) {
            return cost;
        }

        /**
         * Returns true if this ability has the given tag.
         *
         * @param tag the tag to check
         * @return true if present
         */
        public boolean hasTag(String tag) {
            return tags.contains(tag);
        }
    }
}
