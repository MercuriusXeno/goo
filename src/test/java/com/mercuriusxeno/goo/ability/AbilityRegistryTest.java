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
     * A type's held effect is its self ability wearing the durative badge, and a
     * type wearing none holds none (decision brew-grants-the-self-ability-for-an-hour).
     */
    @Nested
    class DurativeAbility {

        @Test
        void durativeBadgedSelfAbilityIsTheTypesDurative() {
            AbilityRegistry registry = registryOf(Stream.of(
                    selfAbility("rock_self", 0, AbilityBadge.SELF),
                    ability("rock_arc_durative", 1, AbilityBadge.DURATIVE),
                    selfAbility("rock_durative", 2, AbilityBadge.DURATIVE)));

            AbilityDefinition durative = registry.durativeAbilityFor(GooTypes.ROCK);

            assertEquals("rock_durative", durative == null ? null : durative.id().getPath());
        }

        @Test
        void typeWithoutADurativeBadgedSelfAbilityHasNoDurative() {
            AbilityRegistry registry = registryOf(Stream.of(
                    selfAbility("rock_self", 0, AbilityBadge.SELF),
                    ability("rock_arc_durative", 1, AbilityBadge.DURATIVE)));

            assertNull(registry.durativeAbilityFor(GooTypes.ROCK));
        }

        @Test
        void shippedBlazeAndLeafDurativeKindleAndBarkskin() {
            AbilityRegistry registry = registryOf(AbilityJson.files().stream().map(AbilityJson::decode));

            assertEquals(AbilityJson.idOf("blaze_kindle.json"), idOf(registry.durativeAbilityFor(GooTypes.BLAZE)));
            assertEquals(AbilityJson.idOf("leaf_barkskin.json"), idOf(registry.durativeAbilityFor(GooTypes.LEAF)));
        }

        private static Identifier idOf(AbilityDefinition definition) {
            return definition == null ? null : definition.id();
        }
    }

    /**
     * Xeno's fan holds Revert before Unveil, then Shriek ordered after Unveil, and no Mutate.
     * xeno-blob-mutates-the-struck
     * yore-leaves-and-rewind-moves-to-xeno-as-revert
     * shriek-moves-to-xeno
     */
    @Test
    void shippedXenoFanHoldsRevertUnveilAndShriek() {
        AbilityRegistry registry = registryOf(AbilityJson.files().stream().map(AbilityJson::decode));
        List<AbilityDefinition> fan = registry.getAbilitiesForType(GooTypes.XENO);

        assertEquals(Set.of(AbilityJson.idOf("xeno_revert.json"), AbilityJson.idOf("xeno_unveil.json"),
                AbilityJson.idOf("xeno_shriek.json")), fan.stream().map(AbilityDefinition::id).collect(Collectors.toSet()));
        assertTrue(AbilityJson.decode("xeno_revert").order() < AbilityJson.decode("xeno_unveil").order());
        assertTrue(AbilityJson.decode("xeno_shriek").order() > AbilityJson.decode("xeno_unveil").order());
    }

    /** flames-absorbs-broil-and-cooks-blocks: Broil folded into Flames, so blaze's fan holds four abilities. */
    @Test
    void shippedBlazeFanHoldsFlamesIgniteMeltAndKindle() {
        AbilityRegistry registry = registryOf(AbilityJson.files().stream().map(AbilityJson::decode));

        assertEquals(Set.of(AbilityJson.idOf("blaze_flames.json"), AbilityJson.idOf("blaze_ignite.json"),
                        AbilityJson.idOf("blaze_molten.json"), AbilityJson.idOf("blaze_kindle.json")),
                registry.getAbilitiesForType(GooTypes.BLAZE).stream().map(AbilityDefinition::id)
                        .collect(Collectors.toSet()));
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
