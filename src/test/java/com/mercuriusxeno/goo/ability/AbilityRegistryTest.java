package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry lists each type's abilities in fan order, badge rank then order,
 * over hand-built definitions and over the shipped JSON (decision fan-sorts-badge-then-order).
 */
class AbilityRegistryTest {

    private static AbilityDefinition ability(String name, int order, AbilityBadge badge) {
        return new AbilityDefinition(Identifier.fromNamespaceAndPath("goo", name), GooTypes.ROCK,
                name, "", order, 0, Delivery.ARC, List.of(), List.of(),
                badge, List.of());
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

    private static AbilityDefinition selfAbility(String name, int order, AbilityBadge badge) {
        return new AbilityDefinition(Identifier.fromNamespaceAndPath("goo", name), GooTypes.ROCK,
                name, "", order, 0, Delivery.of(DeliveryKind.SELF), List.of(), List.of(),
                badge, List.of());
    }

    /**
     * A goo landing on a prism runs its type's prism-badged ability lowest by
     * order, and a type wearing none has no prism combo
     * (decision prism-hosts-the-combos).
     */
    @Nested
    class PrismAbility {

        @Test
        void lowestOrderPrismBadgedAbilityIsTheTypesPrismAbility() {
            AbilityRegistry registry = registryOf(Stream.of(
                    ability("rock_world", 0, AbilityBadge.WORLD),
                    ability("rock_prism_late", 9, AbilityBadge.PRISM),
                    ability("rock_prism_early", 3, AbilityBadge.PRISM)));

            AbilityDefinition prism = registry.prismAbilityFor(GooTypes.ROCK);

            assertEquals("rock_prism_early", prism == null ? null : prism.id().getPath());
        }

        @Test
        void typeWithoutAPrismBadgedAbilityHasNoPrismAbility() {
            AbilityRegistry registry = registryOf(Stream.of(ability("rock_world", 0, AbilityBadge.WORLD)));

            assertNull(registry.prismAbilityFor(GooTypes.ROCK));
        }
    }

    /**
     * A drunk brew runs the type's self ability wearing the brew badge, and a
     * type wearing none runs nothing (decision brew-grants-the-self-ability-for-an-hour).
     */
    @Nested
    class BrewAbility {

        @Test
        void brewBadgedSelfAbilityIsTheTypesBrew() {
            AbilityRegistry registry = registryOf(Stream.of(
                    selfAbility("rock_self", 0, AbilityBadge.SELF),
                    ability("rock_arc_brew", 1, AbilityBadge.BREW),
                    selfAbility("rock_brew", 2, AbilityBadge.BREW)));

            AbilityDefinition brew = registry.brewAbilityFor(GooTypes.ROCK);

            assertEquals("rock_brew", brew == null ? null : brew.id().getPath());
        }

        @Test
        void typeWithoutABrewBadgedSelfAbilityHasNoBrew() {
            AbilityRegistry registry = registryOf(Stream.of(
                    selfAbility("rock_self", 0, AbilityBadge.SELF),
                    ability("rock_arc_brew", 1, AbilityBadge.BREW)));

            assertNull(registry.brewAbilityFor(GooTypes.ROCK));
        }

        @Test
        void shippedBlazeAndLeafBrewKindleAndBarkskin() {
            AbilityRegistry registry = registryOf(AbilityJson.files().stream().map(AbilityJson::decode));

            assertEquals(AbilityJson.idOf("blaze_kindle.json"), idOf(registry.brewAbilityFor(GooTypes.BLAZE)));
            assertEquals(AbilityJson.idOf("leaf_barkskin.json"), idOf(registry.brewAbilityFor(GooTypes.LEAF)));
        }

        private static Identifier idOf(AbilityDefinition definition) {
            return definition == null ? null : definition.id();
        }
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
