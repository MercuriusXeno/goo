package com.mercuriusxeno.goo.ability.typhoon;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typhoon's world goo ships as typhoon_updraft.json, a residual stream
 * spray, and the column a cell of it lifts through stands a block over its
 * top for each of its layers. Over the push stands a glide zone, where a
 * fall slows and a player's keys steer it through the air.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-wears-the-residual-badge
 * updraft-goo-lifts-one-block-per-layer
 * Updraft carries no prism reaction, so its wheel card wears the residual
 * badge alone and its description names no prism.
 * updraft-sheds-its-prism-effect
 * floatstone-lift-leaves-the-mod
 */
class UpdraftAbilityTest {

    private static final String UPDRAFT = "typhoon_updraft";
    private static final String LANG_PATH = "/assets/goo/lang/en_us.json";
    private static final String DESCRIPTION_KEY = "goo.ability.typhoon.updraft.description";
    private static final String DESCRIPTION_END = "a block for each layer.";

    @Test
    void updraftCarriesNoPrismReactionSoItsWheelCardDrawsTheResidualBadgeAlone() {
        AbilityDefinition updraft = AbilityJson.decode(UPDRAFT);
        ClientAbility synced = ClientAbility.fromEntry(
                AbilitySyncPayload.gloveEntries(updraft.gooType(), List.of(updraft)).getFirst());

        assertTrue(updraft.onPrism().isEmpty(), "typhoon_updraft carries an on_prism reaction");
        assertFalse(synced.reactsToPrisms(), "the synced Updraft reads as reacting to prisms");
    }

    @Test
    void updraftsDescriptionEndsAtItsLayersAndNamesNoPrism() throws IOException {
        String description = englishDescription();
        String lowered = description.toLowerCase(Locale.ROOT);

        assertTrue(description.endsWith(DESCRIPTION_END), description);
        assertFalse(lowered.contains("prism"), description);
        assertFalse(lowered.contains("floatstone"), description);
    }

    private static String englishDescription() throws IOException {
        try (InputStream in = UpdraftAbilityTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(in, LANG_PATH);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject().get(DESCRIPTION_KEY).getAsString();
            }
        }
    }

    @Test
    void updraftWearsTheResidualBadgeAndSpraysAsAStream() {
        AbilityDefinition updraft = AbilityJson.decode(UPDRAFT);

        assertEquals(AbilityBadge.RESIDUAL, updraft.badge());
        assertEquals(DeliveryKind.STREAM, updraft.delivery().kind());
    }

    @Test
    void forwardSteersAPlayerOverTheGooTowardItsFacingByTheSteerShare() {
        UpdraftStep updraft = shipped();
        Vec3 steered = updraft.steered(Vec3.ZERO, new Vec3(0, 0, 1), 0f);

        assertEquals(new Vec3(0, 0, updraft.airSpeed() * updraft.airSteer()), steered);
    }

    @Test
    void noKeysLeaveAPlayersMotionOverTheGoo() {
        Vec3 drifting = new Vec3(0.2, -0.1, 0.05);

        assertEquals(drifting, shipped().steered(drifting, Vec3.ZERO, 90f));
    }

    @Test
    void aFallOverTheGlideZonesTopIsLeftAlone() {
        UpdraftStep updraft = shipped();
        int layers = 4;
        double overTheZone = (double) layers / 15 + UpdraftStep.heightOver(layers) + updraft.glideHeight() + 0.5;

        assertTrue(updraft.carried(new Vec3(0, -0.8, 0), overTheZone, layers).isEmpty());
    }

    @Test
    void aFallInTheGlideZoneSlowsToTheFallCap() {
        UpdraftStep updraft = shipped();
        int layers = 4;
        double inTheZone = (double) layers / 15 + UpdraftStep.heightOver(layers) + updraft.glideHeight() / 2;

        assertEquals(-updraft.fallCap(), updraft.carried(new Vec3(0, -0.8, 0), inTheZone, layers).orElseThrow().y,
                1e-9);
    }

    private static UpdraftStep shipped() {
        return UpdraftStep.in(AbilityJson.decode(UPDRAFT).behaviors()).orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 15})
    void aCellsColumnStandsABlockOverItsTopForEachLayer(int layers) {
        assertEquals(layers, UpdraftStep.heightOver(layers));
    }
}
