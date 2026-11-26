package com.mercuriusxeno.goo.client.overlay;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import com.mercuriusxeno.goo.client.BufferedPass;
import com.mercuriusxeno.goo.client.ability.HeldSweepVisual;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * One frame of the revealed ore over the fixed synthetic world draws the
 * same ore blocks, each side where it stands at its scale, in the same
 * colors as commit b423a17 did, before the ore sense's draw cost was cut,
 * while the veins fade in; it reads each showing block's model once; and the
 * glow takes the casting goo type's tint while the ore draws white.
 * decision glitter-sphere-icons-gem-ore-groups
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 * decision ore-glow-takes-the-casting-goos-color
 */
class OreXrayTest {

    private static final long REVEALED_AT = 1_000;
    private static final int LIFE = 100;
    private static final double ORE_RADIUS = 64;
    private static final double HALF_FADED_IN = 2;
    private static final Vec3 CAMERA = HeldSweepWorld.CASTER.add(0, 1.62, 0);
    /** The glow color commit b423a17 drew in, Glimmer's pale blue. */
    private static final int CAPTURED_GLOW_RGB = 0xA8E8FF;

    private final Map<Direction, BakedQuad> sides = new EnumMap<>(Direction.class);
    private final List<BlockStateModelPart> cube = List.of(new CubePart(sides));
    private int modelReads;

    @AfterEach
    void clear() {
        OreSightings.CLIENT.clear();
    }

    @Test
    void aFrameOfTheRevealedOreDrawsTheCapturedQuads() {
        for (Direction side : Direction.values()) {
            sides.put(side, mock(BakedQuad.class));
        }
        List<OreVeins.Vein> veins = OreVeins.firstReachedIn(BlockPos.containing(HeldSweepWorld.CASTER), 0,
                ORE_RADIUS, ORE_RADIUS, HeldSweepWorld::oreAt);
        OreSightings.CLIENT.reveal(REVEALED_AT, veins, LIFE, GooTypes.CRYSTAL);
        List<OreSightings.Sighting> showing = OreXray.showingAt(REVEALED_AT + HALF_FADED_IN);
        QuadRecorder recorder = new QuadRecorder();
        Function<BlockPos, List<BlockStateModelPart>> partsAt = pos -> {
            modelReads++;
            return cube;
        };

        // the captured glow was Glimmer's pale blue, so the type answers it here
        OreXray.drawOre(partsAt, new PoseStack(), CAMERA, showing, gooType -> CAPTURED_GLOW_RGB,
                pass -> new BufferedPass(recorder, () -> { }));

        assertEquals("757 9084 aa981f4354bb5f09d6b8e201ac8ebee7325da3ff9a6b38ad6ffb1c63c3366a86", showing.size() + " " + recorder.quads.size() + " " + recorder.digest(),
                "blocks showing, quads drawn, and their sorted SHA-256");
        assertEquals(showing.size(), modelReads, "model reads, one for each block showing");
    }

    /**
     * The glow pass draws in the held sweep's tint for the goo type that
     * revealed the vein, and the ore pass draws white
     * (decision ore-glow-takes-the-casting-goos-color).
     */
    @Nested
    class GlowInTheCastingGoosColor {

        private static final String BUNDLED_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
        private static final int WHITE = 0xFFFFFF;
        private static final long SETTLED = REVEALED_AT + 50;
        private static final OreVeins.Vein VEIN = new OreVeins.Vein(Identifier.withDefaultNamespace("iron_ore"),
                new Vec3(10.5, 0.5, 1), List.of(new BlockPos(10, 0, 0), new BlockPos(10, 0, 1)));
        private static final OreVeins.Vein FAR_VEIN = new OreVeins.Vein(Identifier.withDefaultNamespace("diamond_ore"),
                new Vec3(30.5, 0.5, 0.5), List.of(new BlockPos(30, 0, 0)));

        @Test
        void aVeinMagnetRevealedGlowsInMetalsTintAndItsOreDrawsWhite() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();

            Map<OreXray.OrePass, Set<Integer>> drawn = colorsDrawnFor(GooTypes.METAL, typeColor);

            assertEquals(Set.of(HeldSweepVisual.tint(GooTypes.METAL, typeColor)), drawn.get(OreXray.GLOW_PASS),
                    "glow");
            assertEquals(Set.of(WHITE), drawn.get(OreXray.ORE_PASS), "ore");
        }

        @Test
        void aVeinGlimmerRevealedGlowsInCrystalsTintAndItsOreDrawsWhite() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();

            Map<OreXray.OrePass, Set<Integer>> drawn = colorsDrawnFor(GooTypes.CRYSTAL, typeColor);

            assertEquals(Set.of(HeldSweepVisual.tint(GooTypes.CRYSTAL, typeColor)), drawn.get(OreXray.GLOW_PASS),
                    "glow");
            assertEquals(Set.of(WHITE), drawn.get(OreXray.ORE_PASS), "ore");
        }

        @Test
        void aMagnetVeinAndAGlimmerVeinInOneFrameEachGlowInTheirOwnTint() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();
            OreSightings.CLIENT.reveal(REVEALED_AT, List.of(FAR_VEIN), LIFE, GooTypes.CRYSTAL);

            Map<OreXray.OrePass, Set<Integer>> drawn = colorsDrawnFor(GooTypes.METAL, typeColor);

            assertEquals(Set.of(HeldSweepVisual.tint(GooTypes.METAL, typeColor),
                    HeldSweepVisual.tint(GooTypes.CRYSTAL, typeColor)), drawn.get(OreXray.GLOW_PASS), "glow");
        }

        /** One frame after a vein is revealed by a goo type: each pass's quad colors, 0xRRGGBB. */
        private Map<OreXray.OrePass, Set<Integer>> colorsDrawnFor(ResourceKey<GooTypeDefinition> gooType,
                                                                  ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
            for (Direction side : Direction.values()) {
                sides.put(side, mock(BakedQuad.class));
            }
            OreSightings.CLIENT.reveal(REVEALED_AT, List.of(VEIN), LIFE, gooType);
            Map<OreXray.OrePass, QuadRecorder> recorders = new LinkedHashMap<>();

            OreXray.drawOre(pos -> cube, new PoseStack(), CAMERA, OreXray.showingAt(SETTLED), typeColor,
                    pass -> new BufferedPass(recorders.computeIfAbsent(pass, opened -> new QuadRecorder()), () -> { }));

            Map<OreXray.OrePass, Set<Integer>> drawn = new LinkedHashMap<>();
            recorders.forEach((pass, recorder) -> drawn.put(pass, recorder.colors.stream()
                    .map(color -> color & WHITE).collect(Collectors.toSet())));
            return drawn;
        }

        private ToIntFunction<ResourceKey<GooTypeDefinition>> bundledColors() throws IOException {
            Map<ResourceKey<GooTypeDefinition>, GooTypeDefinition> types = Map.of(
                    GooTypes.METAL, bundled(GooTypes.METAL), GooTypes.CRYSTAL, bundled(GooTypes.CRYSTAL));
            return key -> GooColors.get(types.get(key));
        }

        private GooTypeDefinition bundled(ResourceKey<GooTypeDefinition> key) throws IOException {
            String path = BUNDLED_JSON_FORMAT.formatted(key.identifier().getPath());
            try (InputStream in = OreXrayTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "Bundled JSON missing on classpath: " + path);
                return GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
            }
        }
    }

    /** Records each baked quad a draw puts: where it stands, its scale, its side and its color. */
    private final class QuadRecorder implements VertexConsumer {

        private final List<String> quads = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();

        @Override
        public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
            Matrix4f matrix = new Matrix4f(pose.pose());
            Direction side = sides.entrySet().stream().filter(entry -> entry.getValue() == quad)
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            quads.add(String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%08x", matrix.m30(), matrix.m31(), matrix.m32(),
                    matrix.m00(), side, instance.getColor(0)));
            colors.add(instance.getColor(0));
        }

        String digest() {
            List<String> sorted = new ArrayList<>(quads);
            sorted.sort(null);
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(String.join("\n", sorted).getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException missing) {
                throw new IllegalStateException(missing);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    /** A whole block's model part: one quad on each side, none unculled. */
    private record CubePart(Map<Direction, BakedQuad> sides) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? List.of() : List.of(sides.get(direction));
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public Material.Baked particleMaterial() {
            throw new UnsupportedOperationException("a recorded part draws no particle");
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }
}
