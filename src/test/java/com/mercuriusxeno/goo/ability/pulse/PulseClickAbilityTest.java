package com.mercuriusxeno.goo.ability.pulse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.FxAnchor;
import com.mercuriusxeno.goo.ability.program.ScanVisualStep;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.VibrateStep;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pulse's Click loads from pulse_click.json as a scan in pulse goo, ordered after
 * Pulse's three abilities, whose landing runs a vibrate step and then a sound at the host, and en_us names it
 * with a description; no deep_click ships
 * (decisions click-moves-to-pulse-and-costs-pulse-goo, click-is-a-hit-scan-drawn-as-a-delayed-wave,
 * click-unlocks-with-the-sculk-sensor-recipe, click-vibration-carries-no-source-entity).
 */
class PulseClickAbilityTest {

    private static final String LANG = "assets/goo/lang/en_us.json";
    private static final String NAME_KEY = "goo.ability.pulse.click";

    private final AbilityDefinition click = AbilityJson.decode("pulse_click");

    @Test
    void isAPulseAbilityAndNoDeepClickShips() {
        List<Identifier> shipped = AbilityJson.files().stream().map(AbilityJson::decode).map(AbilityDefinition::id)
                .toList();
        assertEquals(List.of(GooTypes.PULSE, false),
                List.of(click.gooType(), shipped.contains(Identifier.fromNamespaceAndPath("goo", "deep_click"))));
    }

    @Test
    void ordersAfterPulsesThreeAbilities() {
        for (String other : List.of("pulse_zap", "pulse_tick", "pulse_signal")) {
            assertTrue(click.order() > AbilityJson.decode(other).order(), other);
        }
    }

    @Test
    void deliversAsAScanWithARangeAndAWaveSpeed() {
        assertEquals(DeliveryKind.SCAN, click.delivery().kind());
        assertTrue(click.delivery().range() > 0);
        assertTrue(click.delivery().blocksPerTick() > 0);
    }

    @Test
    void landsAVibrationThenASoundAtTheHost() {
        List<Step> effects = click.behaviors().stream().filter(step -> !(step instanceof ScanVisualStep)).toList();
        assertEquals(2, effects.size());
        assertInstanceOf(VibrateStep.class, effects.get(0));
        SoundStep sound = assertInstanceOf(SoundStep.class, effects.get(1));
        assertEquals(FxAnchor.HOST, sound.at());
    }

    /** Decisions click-is-a-hit-scan-drawn-as-a-delayed-wave, ability-json-names-its-choreography. */
    @Test
    void namesTheSoundWaveVisualInItsChoreography() {
        assertEquals("sound_wave", ScanVisualStep.in(click.behaviors()).map(ScanVisualStep::visual).orElse(""));
    }

    @Test
    void enUsNamesItAndDescribesIt() throws IOException {
        assertEquals(NAME_KEY, click.displayName());
        try (InputStream in = PulseClickAbilityTest.class.getClassLoader().getResourceAsStream(LANG)) {
            assertNotNull(in, LANG + " should ship");
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(lang.has(NAME_KEY), NAME_KEY);
            assertTrue(lang.has(click.description()), click.description());
        }
    }
}
