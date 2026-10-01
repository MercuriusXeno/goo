package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry lists each type's abilities in fan order, badge rank then order,
 * over hand-built definitions and over the shipped JSON (decision fan-sorts-badge-then-order).
 */
class AbilityRegistryTest {

    private static AbilityDefinition ability(String name, int order, AbilityBadge badge) {
        return new AbilityDefinition(Identifier.fromNamespaceAndPath("goo", name), GooTypes.ROCK,
                name, "", order, 0, AbilityDefinition.ChainConfig.DEFAULT, Delivery.ARC, List.of(), List.of(),
                badge);
    }

    private static AbilityRegistry registryOf(Stream<AbilityDefinition> definitions) {
        return new AbilityRegistry(definitions.collect(Collectors.toMap(AbilityDefinition::id, Function.identity())));
    }

    @Test
    void mobAtOrderTenLeadsWorldAtOrdersZeroToTwo() {
        AbilityRegistry registry = registryOf(Stream.of(
                ability("world_two", 2, AbilityBadge.WORLD),
                ability("mob_ten", 10, AbilityBadge.MOB),
                ability("world_zero", 0, AbilityBadge.WORLD),
                ability("world_one", 1, AbilityBadge.WORLD)));

        assertEquals(List.of("mob_ten", "world_zero", "world_one", "world_two"),
                registry.getAbilitiesForType(GooTypes.ROCK).stream().map(def -> def.id().getPath()).toList());
    }

    @Test
    void shippedTypesListInBadgeRankThenOrder() {
        AbilityRegistry registry = registryOf(AbilityJson.files().stream().map(AbilityJson::decode));
        Set<ResourceKey<GooTypeDefinition>> types = AbilityJson.files().stream()
                .map(AbilityJson::decode).map(AbilityDefinition::gooType).collect(Collectors.toSet());
        Map<ResourceKey<GooTypeDefinition>, List<AbilityDefinition>> fanByType = types.stream()
                .collect(Collectors.toMap(Function.identity(), registry::getAbilitiesForType));

        fanByType.forEach((type, fan) -> {
            for (int i = 1; i < fan.size(); i++) {
                AbilityDefinition before = fan.get(i - 1);
                AbilityDefinition after = fan.get(i);
                boolean ranked = before.badge().fanRank() < after.badge().fanRank()
                        || before.badge() == after.badge() && before.order() <= after.order();
                assertTrue(ranked, type.identifier() + ": " + before.id() + " before " + after.id());
            }
        });
        assertTrue(fanByType.values().stream().anyMatch(fan -> fan.stream().anyMatch(def -> def.badge() == AbilityBadge.MOB)
                        && fan.stream().anyMatch(def -> def.badge() == AbilityBadge.WORLD)),
                "some shipped type holds both a mob and a world ability");
    }
}
