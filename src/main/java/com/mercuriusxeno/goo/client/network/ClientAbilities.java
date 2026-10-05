package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The abilities one client connection was synced, by goo type and by id. The
 * connection holds them, so a disconnect drops them with it
 * (decision type-package-and-per-server-holders).
 *
 * @param byType each type's abilities, in fan order
 * @param byId   each ability by its id string
 */
public record ClientAbilities(Map<ResourceKey<GooTypeDefinition>, List<ClientAbility>> byType,
                              Map<String, ClientAbility> byId) {

    /**
     * The abilities a connection holds before its first sync.
     */
    public static final ClientAbilities EMPTY = new ClientAbilities(Map.of(), Map.of());

    /**
     * Freezes the maps it is built from.
     *
     * @param byType each type's abilities, sorted by order
     * @param byId   each ability by its id string
     */
    public ClientAbilities {
        byType = Map.copyOf(byType);
        byId = Map.copyOf(byId);
    }

    /**
     * Reads a sync payload, skipping an entry naming no type.
     *
     * @param payload the sync payload
     * @return the abilities it carries
     */
    public static ClientAbilities fromPayload(AbilitySyncPayload payload) {
        Map<ResourceKey<GooTypeDefinition>, List<ClientAbility>> byType = new HashMap<>();
        Map<String, ClientAbility> byId = new HashMap<>();
        for (AbilitySyncPayload.Entry e : payload.entries()) {
            ResourceKey<GooTypeDefinition> type = GooTypes.byId(e.gooTypeId());
            if (type == null) {
                continue;
            }
            ClientAbility ability = ClientAbility.fromEntry(e);
            byType.computeIfAbsent(type, t -> new ArrayList<>()).add(ability);
            byId.put(e.abilityId(), ability);
        }
        Map<ResourceKey<GooTypeDefinition>, List<ClientAbility>> sorted = new HashMap<>();
        byType.forEach((type, list) -> sorted.put(type,
                list.stream().sorted(AbilityBadge.fanOrder(ClientAbility::badge, ClientAbility::order)).toList()));
        return new ClientAbilities(sorted, byId);
    }

    /**
     * Answers the abilities the current connection holds.
     *
     * @return the abilities, empty while no connection stands
     */
    public static ClientAbilities current() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection instanceof ClientAbilityConnection holder) {
            return holder.clientAbilities();
        }
        return EMPTY;
    }

    /**
     * @param type the goo type
     * @return the type's abilities, in fan order
     */
    public List<ClientAbility> forType(ResourceKey<GooTypeDefinition> type) {
        return byType.getOrDefault(type, List.of());
    }

    /**
     * The type's abilities a player who knows these items may have, the ones
     * the radial offers (decision ability-hidden-until-recipes-known).
     *
     * @param type  the goo type
     * @param known the items the player knows
     * @return the type's known abilities, in fan order
     */
    public List<ClientAbility> knownForType(ResourceKey<GooTypeDefinition> type, KnownItems known) {
        return forType(type).stream().filter(ability -> ability.isKnownTo(known)).toList();
    }

    /**
     * @param abilityId the ability's id string
     * @return the ability, or null when none was synced
     */
    public @Nullable ClientAbility find(String abilityId) {
        return byId.get(abilityId);
    }
}
