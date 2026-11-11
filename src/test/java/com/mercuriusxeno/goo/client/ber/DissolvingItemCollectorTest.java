package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The dissolving item's glow: its bands follow the item's goo in volume ratio, its quads
 * re-emit on the dissolve render type once per type layer carrying the dissolve fraction
 * and that layer's color, share and index, and the dissolve pipeline draws with its
 * own shaders (decisions dissolve-shader-on-item, glow-color-from-mingling).
 */
class DissolvingItemCollectorTest {

    private static final Identifier ITEM_ATLAS = Identifier.withDefaultNamespace("textures/atlas/items.png");
    private static final int LIGHT = 0x00F000F0;
    private static final int BLAZE_ORANGE = 0xFF8000;
    private static final int FROST_BLUE = 0x0000FF;
    private static final int METAL_GREY = 0x808080;
    private static final Map<ResourceKey<GooTypeDefinition>, Integer> COLORS =
            Map.of(GooTypes.BLAZE, BLAZE_ORANGE, GooTypes.FROST, FROST_BLUE, GooTypes.METAL, METAL_GREY);
    private static final GooValue THREE_TYPES =
            new GooValue(Map.of(GooTypes.BLAZE, 600, GooTypes.FROST, 300, GooTypes.METAL, 100));
    private static final float EPSILON = 1e-6f;

    private static BakedQuad quadOnItemAtlas() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.atlasLocation()).thenReturn(ITEM_ATLAS);
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                sprite, ChunkSectionLayer.CUTOUT, null, -1, true, 0, false);
        return new BakedQuad(new Vector3f(0, 0, 0), new Vector3f(0, 1, 0), new Vector3f(1, 1, 0),
                new Vector3f(1, 0, 0), 0L, 0L, 0L, 0L, Direction.SOUTH, material);
    }

    /**
     * Submits one quad through the collector and records what it re-emits.
     *
     * @param glow       the dissolve the collector carries
     * @param renderType receives the render type the quad was re-emitted on
     * @return the recorded vertices
     */
    private static List<RecordingVertexConsumer.Vertex> submitThrough(DissolveGlow glow,
                                                                      RenderType[] renderType) {
        SubmitNodeCollector delegate = mock(SubmitNodeCollector.class);
        new DissolvingItemCollector(delegate, glow, null, 0, new Matrix4f()).submitItem(new PoseStack(), ItemDisplayContext.FIXED,
                LIGHT, 0, 0, new int[0], List.of(quadOnItemAtlas()), ItemStackRenderState.FoilType.NONE);

        ArgumentCaptor<RenderType> type = ArgumentCaptor.forClass(RenderType.class);
        ArgumentCaptor<SubmitNodeCollector.CustomGeometryRenderer> geometry =
                ArgumentCaptor.forClass(SubmitNodeCollector.CustomGeometryRenderer.class);
        verify(delegate).submitCustomGeometry(any(PoseStack.class), type.capture(), geometry.capture());
        renderType[0] = type.getValue();
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        geometry.getValue().render(new PoseStack().last(), recorder);
        return recorder.vertices();
    }

    @Nested
    class Bands {

        /** A 600/300/100 item's bands span its goo in volume ratio, largest first. */
        @Test
        void threeTypeBandsFollowVolumeRatio() {
            List<TypeBand> bands = TypeBands.largestFirst(THREE_TYPES.meltPayout());

            assertEquals(List.of(GooTypes.BLAZE, GooTypes.FROST, GooTypes.METAL),
                    bands.stream().map(TypeBand::type).toList());
            assertEquals(0.6f, bands.get(0).hi() - bands.get(0).lo(), EPSILON);
            assertEquals(0.3f, bands.get(1).hi() - bands.get(1).lo(), EPSILON);
            assertEquals(0.1f, bands.get(2).hi() - bands.get(2).lo(), EPSILON);
        }

        /** The glow takes one layer per band, each in its own type's color and the band's share. */
        @Test
        void glowTakesOneLayerPerTypeInItsColor() {
            List<DissolveGlow.Layer> layers = DissolveGlow.of(0.5f, THREE_TYPES, COLORS::get).layers();

            assertEquals(List.of(BLAZE_ORANGE, FROST_BLUE, METAL_GREY),
                    layers.stream().map(DissolveGlow.Layer::glowRgb).toList());
            assertEquals(List.of(0, 1, 2), layers.stream().map(DissolveGlow.Layer::layer).toList());
            assertEquals(1f, layers.get(0).share(), EPSILON);
            assertEquals(300f / 900f, layers.get(1).share(), EPSILON);
            assertEquals(0.1f, layers.get(2).share(), EPSILON);
        }
    }

    @Nested
    class Encoding {

        /** A three-type item re-emits its quad once per layer, in order, each carrying its layer's glow. */
        @Test
        void eachLayerCarriesItsColorShareAndIndex() {
            RenderType[] renderType = new RenderType[1];
            List<RecordingVertexConsumer.Vertex> vertices =
                    submitThrough(DissolveGlow.of(0.25f, THREE_TYPES, COLORS::get), renderType);

            assertEquals(GooRenderTypes.crucibleDissolve(ITEM_ATLAS), renderType[0]);
            assertEquals(12, vertices.size());
            int[] rgb565 = {0b11111_100000_00000, 0b00000_000000_11111, 0b10000_100000_10000};
            int[] shareUnits = {127, 42, 13};
            for (int i = 0; i < vertices.size(); i++) {
                RecordingVertexConsumer.Vertex vertex = vertices.get(i);
                int layer = i / 4;
                assertEquals(DissolveGlow.FRACTION_UNITS / 4, vertex.uv1U());
                assertEquals(rgb565[layer], vertex.uv1V());
                assertEquals(0xF0 | shareUnits[layer] << 8, vertex.uv2U());
                assertEquals(0xF0 | layer << 8, vertex.uv2V());
            }
        }

        /** A single-type item glows in one layer of that type's color at a whole share. */
        @Test
        void singleTypeItemGlowsInOneColor() {
            assertEquals(List.of(new DissolveGlow.Layer(FROST_BLUE, 1f, 0)),
                    DissolveGlow.of(0.25f, new GooValue(Map.of(GooTypes.FROST, 40)), COLORS::get).layers());
        }

        /** The fraction clamps to a whole item and the color packs to RGB565 in the overlay's high half. */
        @Test
        void fractionClampsAndColorPacks() {
            DissolveGlow.Layer white = new DissolveGlow.Layer(0xFFFFFF, 1f, 0);

            assertEquals(DissolveGlow.FRACTION_UNITS | 0xFFFF << 16,
                    DissolveGlow.single(1.5f, 0xFFFFFF).overlayCoords(white));
            assertEquals(0, DissolveGlow.single(-1f, 0).overlayCoords(new DissolveGlow.Layer(0, 1f, 0)));
        }
    }

    @Nested
    class SeedSlot {

        private static final String VERTEX_SHADER = "/assets/goo/shaders/core/crucible_dissolve.vsh";
        private static final String FRAGMENT_SHADER = "/assets/goo/shaders/core/crucible_dissolve.fsh";

        private static String shaderSource(String path) throws IOException {
            try (InputStream stream = DissolvingItemCollectorTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private static String firstGroup(String source, String regex) {
            Matcher matcher = Pattern.compile(regex).matcher(source);
            assertTrue(matcher.find(), regex + " missing");
            return matcher.group(1);
        }

        /** The fragment shader samples the dissolve field on the item's pixel grid offset by the melt's seed. */
        @Test
        void fieldSampleTakesTheSeedOffset() throws IOException {
            String argument = firstGroup(shaderSource(FRAGMENT_SHADER), "float field = mingleCornerField\\(([^;]*)\\);");

            assertTrue(argument.contains("atlasPixels"), argument);
            assertTrue(argument.contains("dissolveSeed"), "the dissolve field takes no seed: mingleField(" + argument + ")");
        }

        /** The vertex shader unpacks the seed from the light bytes' low nibbles at DissolveGlow's packing. */
        @Test
        void vertexShaderUnpacksTheSeedAtTheGlowsPacking() throws IOException {
            String source = shaderSource(VERTEX_SHADER);

            assertEquals(DissolveGlow.SEED_NIBBLE_BITS,
                    Integer.parseInt(firstGroup(source, "const int SEED_NIBBLE_BITS = (\\d+);")));
            assertEquals(DissolveGlow.SEED_NIBBLE_MASK,
                    Integer.parseInt(firstGroup(source, "const int SEED_NIBBLE_MASK = (\\d+);")));
            assertEquals("float((UV2.x & SEED_NIBBLE_MASK) | (UV2.y & SEED_NIBBLE_MASK) << SEED_NIBBLE_BITS)",
                    firstGroup(source, "dissolveSeed = (.*);"));
        }

        /** A layer's light bytes carry the seed's two nibbles low, the light level kept in the nibble above each. */
        @Test
        void aLayersLightCarriesTheSeedBesideItsLight() {
            DissolveGlow.Layer body = new DissolveGlow.Layer(BLAZE_ORANGE, 1f, 0);

            int coords = DissolveGlow.single(0.25f, BLAZE_ORANGE).withSeed(0xA7).lightCoords(LIGHT, body);

            assertEquals(0xF7, coords & 0xFF);
            assertEquals(0xFA, (coords >> 16) & 0xFF);
        }
    }

    /** The dissolve pipeline draws with its own vertex and fragment shaders over the entity snippet's samplers. */
    @Test
    void dissolvePipelineDrawsWithItsOwnShadersOverTheEntitySamplers() {
        RenderPipeline pipeline = GooRenderTypes.CRUCIBLE_DISSOLVE;

        assertEquals(Identifier.fromNamespaceAndPath("goo", "core/crucible_dissolve"), pipeline.getFragmentShader());
        assertEquals(Identifier.fromNamespaceAndPath("goo", "core/crucible_dissolve"), pipeline.getVertexShader());
        assertEquals(List.of("Sampler0", "Sampler2"), pipeline.getSamplers());
    }
}
