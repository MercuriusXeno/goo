package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Every bundled type's brew effect draws the icon the radial draws for the brew ability the shipped
 * goo_abilities JSON grants it, a texture the jar carries, and no icon for a type granting none.
 * decision every-brew-effect-draws-its-abilitys-icon
 */
class HeldEffectIconTest {

    private static final String ASSETS = "/assets/";
    private static final char PATH_SEPARATOR = '/';

    private static AbilityRegistry shipped;

    @BeforeAll
    static void readShippedAbilities() {
        Map<Identifier, AbilityDefinition> abilities = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .collect(Collectors.toMap(AbilityDefinition::id, Function.identity()));
        shipped = new AbilityRegistry(abilities);
    }

    @ParameterizedTest
    @MethodSource("bundledKeys")
    void brewEffectDrawsTheIconTheRadialDrawsForItsAbility(ResourceKey<GooTypeDefinition> type) throws IOException {
        AbilityDefinition granted = shipped.durativeAbilityFor(type);

        Identifier icon = HeldEffectIcon.textureOf(granted);

        if (granted == null) {
            assertNull(icon, () -> GooTypes.id(type) + " grants no brew ability");
            return;
        }
        Identifier radial = ClientAbility.fromEntry(
                AbilitySyncPayload.gloveEntries(type, List.of(granted)).getFirst()).iconTexture();
        assertEquals(radial, icon, () -> GooTypes.id(type) + "'s brew effect icon");
        String resource = ASSETS + radial.getNamespace() + PATH_SEPARATOR + radial.getPath();
        try (InputStream in = HeldEffectIconTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
        }
    }

    static Stream<ResourceKey<GooTypeDefinition>> bundledKeys() {
        return GooTypes.BUNDLED.stream();
    }
}
