package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client lists a synced type's abilities in the fan order the server
 * registry does, badge rank then order (decision fan-sorts-badge-then-order),
 * and offers the radial only those whose required items the player knows.
 */
class ClientAbilitiesTest {

    private static final Identifier GLASS = Identifier.withDefaultNamespace("glass");
    private static final Identifier SAND = Identifier.withDefaultNamespace("sand");

    private static AbilitySyncPayload.Entry entry(String name, int order, AbilityBadge badge) {
        return gatedEntry(name, order, badge, List.of());
    }

    private static AbilitySyncPayload.Entry gatedEntry(String name, int order, AbilityBadge badge,
                                                       List<Identifier> requires) {
        return new AbilitySyncPayload.Entry("goo:" + name, "goo:rock", name, "", order, List.of(), 0, 0,
                List.of(), 0, Delivery.ARC, badge, requires);
    }

    private static List<String> knownNames(KnownItems known) {
        AbilitySyncPayload payload = new AbilitySyncPayload(List.of(
                entry("open", 0, AbilityBadge.WORLD),
                gatedEntry("gated", 1, AbilityBadge.WORLD, List.of(GLASS, SAND))));
        return ClientAbilities.fromPayload(payload).knownForType(GooTypes.ROCK, known).stream()
                .map(ability -> ability.id().getPath()).toList();
    }

    /** A gated ability stays off the radial until every item it requires is known (decision ability-hidden-until-recipes-known). */
    @Test
    void gatedAbilityHiddenForAPlayerWhoKnowsNothing() {
        assertEquals(List.of("open"), knownNames(KnownItems.NONE));
    }

    @Test
    void gatedAbilityHiddenWhileOneRequiredItemIsUnknown() {
        assertEquals(List.of("open"), knownNames(KnownItems.NONE.with(GLASS)));
    }

    @Test
    void gatedAbilityShownOnceEveryRequiredItemIsKnown() {
        assertEquals(List.of("open", "gated"), knownNames(KnownItems.NONE.with(GLASS).with(SAND)));
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
