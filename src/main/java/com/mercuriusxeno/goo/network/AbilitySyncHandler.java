package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.*;

/**
 * Client-side handler for the ability sync payload. Stores the ability
 * list in a client-accessible cache so the ability radial menu can
 * read it without server-side registry access.
 */
public final class AbilitySyncHandler {

    private static final String LOG_SYNCED = "Synced {} abilities from server";

    private static Map<ResourceKey<GooTypeDefinition>, List<ClientAbility>> byType = new HashMap<>();
    private static Map<String, ClientAbility> byId = new HashMap<>();

    private AbilitySyncHandler() {
    }

    /**
     * Handles the sync payload on the client thread.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(AbilitySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> applySync(payload));
    }

    private static void applySync(AbilitySyncPayload payload) {
        Map<ResourceKey<GooTypeDefinition>, List<ClientAbility>> map = new HashMap<>();
        Map<String, ClientAbility> ids = new HashMap<>();
        for (AbilitySyncPayload.Entry e : payload.entries()) {
            ResourceKey<GooTypeDefinition> type = GooTypes.byId(e.gooTypeId());
            if (type == null) {
                continue;
            }
            ClientAbility ability = new ClientAbility(Identifier.tryParse(e.abilityId()),
                    e.displayName(), e.icon(), e.order(), e.tags(), e.fuseTicks(), e.maxStacks());
            map.computeIfAbsent(type, t -> new ArrayList<>()).add(ability);
            ids.put(e.abilityId(), ability);
        }
        for (List<ClientAbility> list : map.values()) {
            list.sort(Comparator.comparingInt(ClientAbility::order));
        }
        byType = map;
        byId = ids;
        if (Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_SYNCED, payload.entries().size());
        }
    }

    /**
     * Returns the abilities available for a goo type on the client.
     *
     * @param type the goo type
     * @return immutable list, empty if none synced
     */
    public static List<ClientAbility> getAbilitiesForType(ResourceKey<GooTypeDefinition> type) {
        List<ClientAbility> list = byType.get(type);
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    /**
     * Returns the synced ability an id names.
     *
     * @param abilityId the ability resource id string
     * @return the ability, or null when none synced under that id
     */
    public static @Nullable ClientAbility findAbility(String abilityId) {
        return byId.get(abilityId);
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
     */
    public record ClientAbility(Identifier id, String displayName, String icon,
                                int order, List<String> tags, int fuseTicks, int maxStacks) {

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
