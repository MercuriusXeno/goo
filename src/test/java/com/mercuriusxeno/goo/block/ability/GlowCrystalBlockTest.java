package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * GlowCrystalBlock: the bulb sits on the face it landed on, its hitbox
 * hugging the support's face rather than the far side of its cell, and its
 * blockstate turns the model the way the prism's does, so the model hugs
 * that face too (operator UAT 2026-10-10: on a wall the bulb sat on the
 * far side of its cell).
 * decision bulb-one-model-max-light-beacon-combo
 */
class GlowCrystalBlockTest {

    private static final double EPSILON = 1e-9;
    private static final double MIDDLE = 0.5;
    private static final String BLOCKSTATES = "/assets/goo/blockstates/";

    @ParameterizedTest
    @EnumSource(Direction.class)
    void theHitboxHugsTheSupportsFace(Direction facing) {
        AABB bounds = GlowCrystalBlock.shapeOn(facing).bounds();
        // the support stands opposite the facing; the hitbox touches the cell's side toward it
        Direction toSupport = facing.getOpposite();
        double supportSide = toSupport.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        double touching = toSupport.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? bounds.max(facing.getAxis()) : bounds.min(facing.getAxis());
        assertEquals(supportSide, touching, EPSILON, facing + " should touch the support's side");
        assertEquals(GlowCrystalBlock.DEPTH, bounds.max(facing.getAxis()) - bounds.min(facing.getAxis()), EPSILON);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void theHitboxCentersAcrossTheFace(Direction facing) {
        AABB bounds = GlowCrystalBlock.shapeOn(facing).bounds();
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != facing.getAxis()) {
                assertEquals(MIDDLE, (bounds.min(axis) + bounds.max(axis)) / 2, EPSILON);
            }
        }
    }

    @Test
    void theBlockstateTurnsTheModelAsThePrismsDoes() throws IOException {
        JsonObject bulb = variants("glow_crystal.json");
        JsonObject prism = variants("prism.json");
        for (Direction facing : Direction.values()) {
            String key = "facing=" + facing.getSerializedName();
            assertEquals(rotation(prism, key, "x"), rotation(bulb, key, "x"), key + " x");
            assertEquals(rotation(prism, key, "y"), rotation(bulb, key, "y"), key + " y");
        }
    }

    private static int rotation(JsonObject variants, String key, String axis) {
        JsonObject variant = variants.getAsJsonObject(key);
        return variant.has(axis) ? variant.get(axis).getAsInt() : 0;
    }

    private static JsonObject variants(String file) throws IOException {
        try (InputStream in = GlowCrystalBlockTest.class.getResourceAsStream(BLOCKSTATES + file)) {
            assertNotNull(in, file + " should be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("variants");
        }
    }
}
