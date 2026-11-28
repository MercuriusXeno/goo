package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.HeldEffectIcon;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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

/**
 * The registrar answers one extension per bundled held effect, reading the connection's durative ability
 * icons over the shipped goo_abilities JSON: each draws its granted ability's icon, or none for a type granting
 * none.
 * decision every-brew-effect-draws-its-abilitys-icon
 */
class HeldEffectIconsTest {

    private static final List<ResourceKey<GooTypeDefinition>> GRANTING_NONE = List.of(
            GooTypes.ASTRAL, GooTypes.CRYSTAL, GooTypes.FROST, GooTypes.HEX, GooTypes.INFINITY, GooTypes.METAL,
            GooTypes.ORNATE, GooTypes.PULSE, GooTypes.QUANTUM, GooTypes.UNSTABLE, GooTypes.VOID, GooTypes.WEIRD);

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
        HeldEffectIcon.readConnectionIconsFrom(type -> HeldEffectIcon.textureOf(shipped.durativeAbilityFor(type), Level.OVERWORLD));
        extensions = HeldEffectIcons.extensionsFor(GooTypes.BUNDLED);
    }

    @AfterEach
    void restoreTheConnectionIcons() {
        HeldEffectIcon.readConnectionIconsFrom(type -> null);
    }

    @Test
    void everyBundledHeldEffectHoldsOneExtension() {
        assertEquals(Set.copyOf(GooTypes.BUNDLED), extensions.keySet());
    }


    @ParameterizedTest
    @MethodSource("bundledKeysGrantingADurative")
    void heldEffectDrawsItsGrantedAbilitysIcon(ResourceKey<GooTypeDefinition> type) {
        HeldEffectIcons.AbilityIconExtension extension =
                assertInstanceOf(HeldEffectIcons.AbilityIconExtension.class, extensions.get(type));
        assertNotNull(extension.iconTexture(), () -> GooTypes.id(type) + " draws no held effect icon");
        assertEquals(HeldEffectIcon.textureOf(shipped.durativeAbilityFor(type), Level.OVERWORLD), extension.iconTexture(),
                () -> GooTypes.id(type) + "'s held effect icon");
    }

    @Test
    void typesGrantingNoDurativeAbilityDrawNoIconSoVanillaDrawsTheirTile() {
        for (ResourceKey<GooTypeDefinition> type : GRANTING_NONE) {
            assertNull(shipped.durativeAbilityFor(type), () -> GooTypes.id(type) + " grants a durative ability");
            HeldEffectIcons.AbilityIconExtension extension =
                    assertInstanceOf(HeldEffectIcons.AbilityIconExtension.class, extensions.get(type));
            assertNull(extension.iconTexture(), () -> GooTypes.id(type) + " draws an icon");
        }
    }

    static Stream<ResourceKey<GooTypeDefinition>> bundledKeysGrantingADurative() {
        return GooTypes.BUNDLED.stream().filter(type -> !GRANTING_NONE.contains(type));
    }
}
