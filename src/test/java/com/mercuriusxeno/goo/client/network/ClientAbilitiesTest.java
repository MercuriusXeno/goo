package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client lists a synced type's abilities in the fan order the server
 * registry does, badge rank then order (decision fan-sorts-badge-then-order).
 */
class ClientAbilitiesTest {

    private static AbilitySyncPayload.Entry entry(String name, int order, AbilityBadge badge) {
        return new AbilitySyncPayload.Entry("goo:" + name, "goo:rock", name, "", order, List.of(), 0,
                List.of(), 0, Delivery.ARC, badge);
    }

    @Test
    void mobAtOrderTenLeadsWorldAtOrdersZeroToTwo() {
        AbilitySyncPayload payload = new AbilitySyncPayload(List.of(
                entry("world_two", 2, AbilityBadge.WORLD),
                entry("mob_ten", 10, AbilityBadge.MOB),
                entry("world_zero", 0, AbilityBadge.WORLD),
                entry("world_one", 1, AbilityBadge.WORLD)));

        assertEquals(List.of("mob_ten", "world_zero", "world_one", "world_two"),
                ClientAbilities.fromPayload(payload).forType(GooTypes.ROCK).stream()
                        .map(ability -> ability.id().getPath()).toList());
    }
}
