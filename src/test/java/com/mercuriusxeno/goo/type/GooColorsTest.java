package com.mercuriusxeno.goo.type;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that GooColors answers a cycling type's channels rotated around the hue wheel by the
 * tick's share of its cycle, a swinging type's within its arc, and a still type's channels
 * unchanged at every tick.
 */
class GooColorsTest {

    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final float HUE_TOLERANCE_DEGREES = 1.5f;
    private static final float SHADE_TOLERANCE = 0.01f;
    private static final float PALE_FLOOR = 0.85f;

    @Test
    void infinityWheelSitsAQuarterTurnAwayAtAQuarterCycle() throws IOException {
        GooTypeDefinition infinity = bundled("infinity");
        int cycle = infinity.hueCycle().ticks();
        assertTrue(cycle > 0, "infinity names no hue cycle");
        float[] base = hsb(GooColors.wheel(infinity, 0));
        float[] quarter = hsb(GooColors.wheel(infinity, cycle / 4));
        float turned = (quarter[0] - base[0] + 1f) % 1f * 360f;
        assertEquals(90f, turned, HUE_TOLERANCE_DEGREES, "hue turned at a quarter cycle");
        assertEquals(base[1], quarter[1], SHADE_TOLERANCE, "saturation");
        assertEquals(base[2], quarter[2], SHADE_TOLERANCE, "value");
    }

    @Test
    void blazeWheelStandsStillAtEveryTick() throws IOException {
        GooTypeDefinition blaze = bundled("blaze");
        for (long tick : new long[]{0, 1, 37, 200, 123_456}) {
            assertEquals(blaze.wheel(), GooColors.wheel(blaze, tick), "blaze's wheel at tick " + tick);
        }
    }

    @Test
    void yoreSheenStaysInItsSwingAboveThePaleFloor() throws IOException {
        // yore-gets-opalescent-iridescence-back
        GooTypeDefinition yore = bundled("yore");
        HueCycle cycle = yore.hueCycle();
        assertTrue(cycle.ticks() > 0, "yore names no hue cycle");
        assertTrue(cycle.swingDegrees() < 360f, "yore's swing is the whole wheel");
        float[] base = hsb(yore.wheel());
        for (int tick = 0; tick < cycle.ticks(); tick++) {
            float[] sheen = hsb(GooColors.wheel(yore, tick));
            float offset = ((sheen[0] - base[0] + 1.5f) % 1f - 0.5f) * 360f;
            assertTrue(Math.abs(offset) <= cycle.swingDegrees() / 2f + HUE_TOLERANCE_DEGREES,
                    "yore's hue sits " + offset + " degrees from its base at tick " + tick);
            assertTrue(sheen[2] >= PALE_FLOOR, "yore's value drops to " + sheen[2] + " at tick " + tick);
        }
    }

    static float[] hsb(int rgb) {
        return Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
    }

    static GooTypeDefinition bundled(String type) throws IOException {
        String path = TYPE_JSON_FORMAT.formatted(type);
        try (InputStream in = GooColorsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            }
        }
    }
}
