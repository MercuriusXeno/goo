package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.data.GooValueTable;
import java.util.List;

/**
 * The synced copies a client holds of shipped abilities, built from their
 * shipped files.
 */
final class SyncedAbilities {

    /** The seven abilities whose blob once floated to the center line. */
    static final List<String> ONCE_CENTERED = List.of("frost_orb", "hex_drain", "leaf_reap", "pulse_click",
            "pulse_zap", "crystal_glimmer", "metal_magnet");

    private SyncedAbilities() {
    }

    /**
     * The synced copy of a shipped ability.
     *
     * @param name the ability's file name, without the suffix
     * @return the synced copy
     */
    static ClientAbility of(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(), definition.order(),
                definition.tags(), definition.behaviors(), definition.cost().in(GooValueTable.EMPTY),
                definition.delivery(), definition.badge(), definition.requires());
    }
}
