package com.mercuriusxeno.goo.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that nether's, void's, sculk's and astral's goo_fluid_types palettes keep their own hue and a dark floor
 * and climb to a bright highlight through a second, contrasting hue.
 */
class DarkGooPaletteDepthTest {

    private static final List<String> DARK_TYPES = DarkGooResources.DARK_TYPES;
    private static final float FLOOR_LIGHTNESS_CEILING = 0.15f;
    private static final float HIGHLIGHT_LIGHTNESS_FLOOR = 0.7f;
    private static final float TINTED_SATURATION = 0.15f;
    private static final float OWN_HUE_REACH = 30f;
    private static final float SECOND_HUE_GAP = 60f;
    private static final float LOWER_HALF = 0.5f;

    private record Stop(float position, float[] hsl) {
    }

    // dark-goos-climb-to-a-second-hue
    @ParameterizedTest
    @FieldSource("DARK_TYPES")
    void paletteKeepsADarkFloor(String type) throws IOException {
        Stop floor = stops(type).getFirst();
        assertTrue(floor.position() == 0f && floor.hsl()[2] <= FLOOR_LIGHTNESS_CEILING,
                type + "'s position 0 stop has lightness " + floor.hsl()[2]);
    }

    // dark-goos-climb-to-a-second-hue
    @ParameterizedTest
    @FieldSource("DARK_TYPES")
    void lowerHalfKeepsTheTypesOwnHue(String type) throws IOException {
        float wheelHue = DarkGooResources.hsl(DarkGooResources.wheel(type))[0];
        assertTrue(stops(type).stream().anyMatch(stop -> stop.position() <= LOWER_HALF
                        && stop.hsl()[1] > TINTED_SATURATION
                        && DarkGooResources.hueGap(stop.hsl()[0], wheelHue) <= OWN_HUE_REACH),
                type + " holds no tinted stop at or below 0.5 within 30 degrees of its wheel hue " + wheelHue);
    }

    // dark-goos-climb-to-a-second-hue
    @ParameterizedTest
    @FieldSource("DARK_TYPES")
    void paletteClimbsToABrightHighlight(String type) throws IOException {
        float brightest = 0f;
        for (Stop stop : stops(type)) {
            brightest = Math.max(brightest, stop.hsl()[2]);
        }
        assertTrue(brightest >= HIGHLIGHT_LIGHTNESS_FLOOR, type + "'s brightest stop has lightness " + brightest);
    }

    // dark-goos-climb-to-a-second-hue
    @ParameterizedTest
    @FieldSource("DARK_TYPES")
    void paletteClimbsThroughASecondHue(String type) throws IOException {
        float wheelHue = DarkGooResources.hsl(DarkGooResources.wheel(type))[0];
        assertTrue(stops(type).stream().anyMatch(stop -> stop.hsl()[1] > TINTED_SATURATION
                        && DarkGooResources.hueGap(stop.hsl()[0], wheelHue) >= SECOND_HUE_GAP),
                type + " holds no tinted stop 60 degrees or more from its wheel hue " + wheelHue);
    }

    private static List<Stop> stops(String type) throws IOException {
        JsonObject entry = DarkGooResources.fluidEntry(type);
        List<Stop> stops = new ArrayList<>();
        for (JsonElement element : entry.getAsJsonArray("palette")) {
            JsonObject stop = element.getAsJsonObject();
            stops.add(new Stop(stop.get("position").getAsFloat(), DarkGooResources.hsl(stop.get("color").getAsString())));
        }
        return stops;
    }
}
