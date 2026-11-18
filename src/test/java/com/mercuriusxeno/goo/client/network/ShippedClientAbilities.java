package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import java.util.List;

/**
 * A shipped ability as the client holds it once synced: its JSON decoded, sent through the sync entry and read back.
 */
public final class ShippedClientAbilities {

    private ShippedClientAbilities() {
    }

    /**
     * A shipped ability's synced client copy.
     *
     * @param name the ability's filename without its suffix, such as {@code astral_lunar}
     * @return the client ability
     */
    public static ClientAbility of(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return ClientAbility.fromEntry(AbilitySyncPayload.gloveEntries(definition.gooType(), List.of(definition))
                .getFirst());
    }
}
