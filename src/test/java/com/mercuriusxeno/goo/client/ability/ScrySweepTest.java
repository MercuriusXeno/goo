package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ScrySweep's reveal traces a block's voxel shape: a revealed side is drawn
 * on each of the shape's boxes, at the box's own bounds rather than the
 * cube's; and Scry's sphere, faces and glisten take the casting goo type's
 * color, read from the bundled type JSON.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision scry-ships-on-astral
 */
class ScrySweepTest {

    private static final AABB BOTTOM_SLAB = new AABB(0, 0, 0, 1, 0.5, 1);
    private static final AABB FENCE_POST = new AABB(0.375, 0, 0.375, 0.625, 1.5, 0.625);
    private static final float TOLERANCE = 0.01f;

    @Test
    void aSlabsTopShowsAtItsHalfHeight() {
        List<RecordingVertexConsumer.Vertex> quad = emit(BOTTOM_SLAB, Direction.UP);

        assertEquals(4, quad.size());
        for (RecordingVertexConsumer.Vertex v : quad) {
            assertEquals(0.5f, v.y(), TOLERANCE);
        }
    }

    @Test
    void aFencePostsSideSpansThePostNotTheCube() {
        List<RecordingVertexConsumer.Vertex> quad = emit(FENCE_POST, Direction.EAST);

        for (RecordingVertexConsumer.Vertex v : quad) {
            assertEquals(0.625f, v.x(), TOLERANCE);
            assertEquals(true, v.z() >= 0.375f - TOLERANCE && v.z() <= 0.625f + TOLERANCE, "z within the post");
        }
        assertEquals(1.5f, quad.stream().map(RecordingVertexConsumer.Vertex::y).max(Float::compare).orElseThrow(),
                TOLERANCE);
    }

    private static List<RecordingVertexConsumer.Vertex> emit(AABB box, Direction side) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        ScrySweep.emitBoxFace(new PoseStack().last(), consumer, box, side, 0xFFFFE628);
        return consumer.vertices();
    }

    /** Scry's look reads in the astral type's color, resolved from the bundled astral and glow JSON. */
    @Nested
    class TintFromTheCastingType {

        private static final String BUNDLED_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";

        @Test
        void anAstralPingDrawsInTheAstralTypesColorNotGlowYellow() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();

            int tint = ScrySweep.tint(GooTypes.ASTRAL, typeColor);

            assertEquals(GooColors.get(bundled(GooTypes.ASTRAL)), tint);
            assertNotEquals(GooColors.get(bundled(GooTypes.GLOW)), tint);
        }

        @Test
        void scrysGlistenDrawsInTheAstralTypesColorNotGlowYellow() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();

            int glisten = AilmentKind.ASTRAL.rgb(typeColor);

            assertEquals(GooColors.get(bundled(GooTypes.ASTRAL)), glisten);
            assertNotEquals(AilmentKind.GLOW.rgb(typeColor), glisten);
        }

        private ToIntFunction<ResourceKey<GooTypeDefinition>> bundledColors() throws IOException {
            Map<ResourceKey<GooTypeDefinition>, GooTypeDefinition> types = Map.of(
                    GooTypes.ASTRAL, bundled(GooTypes.ASTRAL), GooTypes.GLOW, bundled(GooTypes.GLOW));
            return key -> GooColors.get(types.get(key));
        }

        private GooTypeDefinition bundled(ResourceKey<GooTypeDefinition> key) throws IOException {
            String path = BUNDLED_JSON_FORMAT.formatted(key.identifier().getPath());
            try (InputStream in = ScrySweepTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "Bundled JSON missing on classpath: " + path);
                return GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
            }
        }
    }
}
