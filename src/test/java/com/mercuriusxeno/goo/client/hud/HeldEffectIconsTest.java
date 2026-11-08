package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.HeldEffectIcon;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registrar answers one extension per bundled brew effect, reading the connection's brew ability
 * icons over the shipped goo_abilities JSON: shroom's is Sight's eye, drawing Sight's icon in the
 * inventory, and every other draws its granted ability's icon, or none for a type granting none.
 * decision every-brew-effect-draws-its-abilitys-icon
 * decision shroom-hud-eye-stays-and-its-inventory-icon-is-sights
 */
class HeldEffectIconsTest {

    private static final String SIGHT_ICON = "textures/goo/ability/shroom_sight.png";
    private static final List<ResourceKey<GooTypeDefinition>> GRANTING_NONE = List.of(GooTypes.DEEP,
            GooTypes.INFINITY, GooTypes.METAL, GooTypes.OPULENT, GooTypes.VOID, GooTypes.WEIRD);

    private static AbilityRegistry shipped;
    private Map<ResourceKey<GooTypeDefinition>, IClientMobEffectExtensions> extensions;

    @BeforeAll
    static void readShippedAbilities() {
        Map<Identifier, AbilityDefinition> abilities = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .collect(Collectors.toMap(AbilityDefinition::id, Function.identity()));
        shipped = new AbilityRegistry(abilities);
    }

    @BeforeEach
    void readConnectionIconsFromTheShippedAbilities() {
        HeldEffectIcon.readConnectionIconsFrom(type -> HeldEffectIcon.textureOf(shipped.durativeAbilityFor(type)));
        extensions = HeldEffectIcons.extensionsFor(GooTypes.BUNDLED);
    }

    @AfterEach
    void restoreTheConnectionIcons() {
        HeldEffectIcon.readConnectionIconsFrom(type -> null);
    }

    @Test
    void everyBundledBrewEffectHoldsOneExtension() {
        assertEquals(Set.copyOf(GooTypes.BUNDLED), extensions.keySet());
    }

    @Test
    void shroomKeepsSightsEyeAndDrawsSightsIconInTheInventory() {
        SightEyeIcon eye = assertInstanceOf(SightEyeIcon.class, extensions.get(GooTypes.SHROOM));
        Identifier inventoryIcon = eye.inventoryIconTexture();
        assertNotNull(inventoryIcon);
        assertTrue(inventoryIcon.getPath().endsWith(SIGHT_ICON), inventoryIcon::toString);
    }

    @ParameterizedTest
    @MethodSource("bundledKeysButShroom")
    void brewEffectDrawsItsGrantedAbilitysIcon(ResourceKey<GooTypeDefinition> type) {
        HeldEffectIcons.AbilityIconExtension extension =
                assertInstanceOf(HeldEffectIcons.AbilityIconExtension.class, extensions.get(type));
        assertEquals(HeldEffectIcon.textureOf(shipped.durativeAbilityFor(type)), extension.iconTexture(),
                () -> GooTypes.id(type) + "'s brew effect icon");
    }

    @Test
    void typesGrantingNoBrewAbilityDrawNoIconSoVanillaDrawsTheirTile() {
        for (ResourceKey<GooTypeDefinition> type : GRANTING_NONE) {
            assertNull(shipped.durativeAbilityFor(type), () -> GooTypes.id(type) + " grants a brew ability");
            HeldEffectIcons.AbilityIconExtension extension =
                    assertInstanceOf(HeldEffectIcons.AbilityIconExtension.class, extensions.get(type));
            assertNull(extension.iconTexture(), () -> GooTypes.id(type) + " draws an icon");
        }
    }

    static Stream<ResourceKey<GooTypeDefinition>> bundledKeysButShroom() {
        return GooTypes.BUNDLED.stream().filter(type -> !type.equals(GooTypes.SHROOM));
    }
}
