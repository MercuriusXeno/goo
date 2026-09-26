package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityCost;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers RadialWheelRenderer.resolveAbilityIcon over every shipped ability, as the
 * ability sync hands each one to the client (decision diagnose-then-fix-radial-icon-id),
 * and the fan's cost and holdings labels.
 */
class RadialWheelRendererTest {

    private static final String ASSETS_ROOT = "assets/";
    private static final String TEXTURE_PATH = "textures/goo/ability/unstable_timed_bomb.png";

    private static ClientAbility clientAbilityOf(AbilityDefinition definition) {
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(),
                definition.chain().fuseTicks(), definition.chain().maxStacks(), definition.behaviors(),
                definition.cost());
    }

    private static List<ClientAbility> shippedAbilities() {
        return AbilityJson.files().stream()
                .map((Path file) -> clientAbilityOf(AbilityJson.decode(file)))
                .toList();
    }

    private static ClientAbility abilityWithIcon(String icon) {
        return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_timed_bomb"),
                "ability.goo.unstable_timed_bomb", icon, 0, List.of(), 0, 0, List.of(), null);
    }

    @Nested
    class ShippedAbilities {

        @Test
        void everyShippedAbilityResolvesWithoutThrowing() {
            assertAll(shippedAbilities().stream().map(ability ->
                    (Executable) () -> assertDoesNotThrow(() -> RadialWheelRenderer.resolveAbilityIcon(ability),
                            ability.id().toString())));
        }

        @Test
        void everyShippedAbilityIconExistsUnderGooAssets() {
            assertAll(shippedAbilities().stream().map(ability -> (Executable) () -> {
                Identifier icon = RadialWheelRenderer.resolveAbilityIcon(ability);
                assertEquals(Goo.MODID, icon.getNamespace(), ability.id().toString());
                String resource = ASSETS_ROOT + icon.getNamespace() + "/" + icon.getPath();
                assertNotNull(RadialWheelRendererTest.class.getClassLoader().getResource(resource),
                        ability.id() + " resolves " + icon + " but no " + resource + " is on the classpath");
            }));
        }
    }

    /** The fan reads each ability's first-throw cost and the type's holdings (decision radial-shows-first-throw-cost-and-holdings). */
    @Nested
    class CostAndHoldings {

        private static final int HOLDINGS = 1500;
        private static final int AFFORDABLE = 1000;
        private static final int UNAFFORDABLE = 2000;

        private static ClientAbility costing(int firstThrow) {
            return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "cost_" + firstThrow),
                    "ability.goo.cost", "", 0, List.of(), 0, 1, List.of(),
                    new AbilityCost.Quadratic(firstThrow, 0f, 1f, 1f));
        }

        @Test
        void wedgeReadsItsFirstThrowCost() {
            ClientAbility cheap = costing(AFFORDABLE);
            ClientAbility dear = costing(UNAFFORDABLE);

            assertEquals(RadialWheelRenderer.formatQuantity(cheap.throwCost(0)),
                    RadialWheelRenderer.fanSlot(cheap, HOLDINGS).costLabel());
            assertEquals("1.0k", RadialWheelRenderer.fanSlot(cheap, HOLDINGS).costLabel());
            assertEquals("2.0k", RadialWheelRenderer.fanSlot(dear, HOLDINGS).costLabel());
        }

        @Test
        void centerReadsTheTypesHoldings() {
            assertEquals("1.5k", RadialWheelRenderer.holdingsLabel(HOLDINGS));
        }

        @Test
        void wedgeCostingMoreThanTheHoldingsReadsDimmed() {
            assertFalse(RadialWheelRenderer.fanSlot(costing(AFFORDABLE), HOLDINGS).dimmed());
            assertTrue(RadialWheelRenderer.fanSlot(costing(UNAFFORDABLE), HOLDINGS).dimmed());
        }

        @Test
        void wedgeCostingExactlyTheHoldingsReadsBright() {
            assertFalse(RadialWheelRenderer.fanSlot(costing(HOLDINGS), HOLDINGS).dimmed());
        }
    }

    @Nested
    class ExplicitIcon {

        @Test
        void namespacedIconKeepsItsOwnNamespace() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon(Goo.MODID + ":" + TEXTURE_PATH)));
        }

        @Test
        void bareIconTakesTheGooNamespace() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon(TEXTURE_PATH)));
        }

        @Test
        void emptyIconFallsBackToTheConventionPath() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon("")));
        }
    }
}
