package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.WindStep;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vacuum's sounds: one held loop, the wind-line wind pitched below Cold's as
 * a steady suction, no void wub under it, and a slurp event built from
 * vanilla's slime and honey samples
 * (decision vacuum-sounds-a-suction-wind-and-rising-slurps).
 */
class VacuumSoundsTest {

    private static final String SOUNDS_JSON = "/assets/goo/sounds.json";
    private static final List<String> WUBS = List.of(GooSoundIds.OUTSIDER_WUB.getPath(),
            GooSoundIds.HOLE_OPEN.getPath(), GooSoundIds.HOLE_CLOSE.getPath());
    private static final List<String> SLURP_SAMPLE_FOLDERS = List.of("minecraft:mob/slime/", "minecraft:item/bottle/drink_honey",
            "minecraft:block/honeyblock/");

    private final AbilityDefinition vacuum = AbilityJson.decode("void_vacuum");

    @Test
    void vacuumsHeldLoopIsTheWindLineWindAlone() {
        assertTrue(windOf(vacuum) != null, "Vacuum names no wind step");
        assertTrue(vacuum.delivery().sound().isEmpty(), "Vacuum's delivery plays a second loop "
                + vacuum.delivery().sound());
    }

    @Test
    void vacuumsWindIsPitchedBelowColdsWind() {
        float vacuumPitch = windOf(vacuum).pitch();
        float coldPitch = windOf(AbilityJson.decode("frost_cold")).pitch();

        assertTrue(vacuumPitch < coldPitch, "Vacuum's wind " + vacuumPitch + " should sound below Cold's " + coldPitch);
    }

    @Test
    void vacuumNamesNoWub() {
        assertFalse(stepsOf(vacuum).anyMatch(SoundStep.class::isInstance), "Vacuum names a sound step");
        String json = AbilityJson.read("void_vacuum");
        for (String wub : WUBS) {
            assertFalse(json.contains(wub), "void_vacuum.json names " + wub);
        }
    }

    @Test
    void theSlurpIsBuiltFromVanillasSlimeAndHoneySamples() throws IOException {
        JsonObject slurp = soundsJson().getAsJsonObject(GooSoundIds.VACUUM_SLURP.getPath());
        assertNotNull(slurp, "sounds.json names no " + GooSoundIds.VACUUM_SLURP.getPath());
        boolean slime = false;
        boolean honey = false;
        for (JsonElement sound : slurp.getAsJsonArray("sounds")) {
            String name = sound.getAsJsonObject().get("name").getAsString();
            assertTrue(SLURP_SAMPLE_FOLDERS.stream().anyMatch(name::startsWith), name + " is no slime or honey sample");
            slime |= name.contains("slime");
            honey |= name.contains("honey");
        }
        assertTrue(slime && honey, "the slurp should mix slime and honey samples");
    }

    private static WindStep windOf(AbilityDefinition ability) {
        return stepsOf(ability).filter(WindStep.class::isInstance).map(WindStep.class::cast).findFirst().orElse(null);
    }

    private static Stream<Step> stepsOf(AbilityDefinition ability) {
        return ability.behaviors().stream().flatMap(VacuumSoundsTest::withDescendants);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(VacuumSoundsTest::withDescendants));
    }

    private static JsonObject soundsJson() throws IOException {
        try (InputStream in = VacuumSoundsTest.class.getResourceAsStream(SOUNDS_JSON)) {
            assertNotNull(in, SOUNDS_JSON + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
