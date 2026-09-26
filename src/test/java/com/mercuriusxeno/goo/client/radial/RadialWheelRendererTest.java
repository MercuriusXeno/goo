package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers RadialWheelRenderer.resolveAbilityIcon over every shipped ability, as the
 * ability sync hands each one to the client (decision diagnose-then-fix-radial-icon-id).
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
