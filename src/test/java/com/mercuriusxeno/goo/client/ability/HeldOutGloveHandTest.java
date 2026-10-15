package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.model.GloveBodyModels;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The glove in the held-out first-person hand, its palm blob drawn the way the
 * hand pass draws it: every opaque submission first, then each translucent
 * render type's quads sorted far to near by their centroids, each writing
 * depth, so the glove hides the part of the core inside the hand.
 * decision diagnose-and-correct-the-palm-core-cull
 */
class HeldOutGloveHandTest {

    /** The vanilla first-person arm transform for the right hand at rest, ItemInHandRenderer.applyItemArmTransform. */
    private static final Vector3f ARM = new Vector3f(0.56f, -0.52f, -0.72f);
    /** The default field of view in degrees, which the hand pass projects with. */
    private static final int FOV_DEGREES = 70;
    /**
     * The game ticks one pulse of the core spans, 2 pi over the pulse speed
     * of 0.3 rounded up; the operator's screenshot caught the core near its
     * widest, a quarter cycle in.
     */
    private static final int PULSE_TICKS = 21;
    /** The operator's screenshot size, so the raster samples the screen they saw. */
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1009;
    private static final float NEAR = 0.05f;
    private static final float FAR = 100f;
    private static final float PX = 1f / 16f;

    private enum Part { BODY, CORE, SHELL }

    /** One quad as the hand pass sees it: its view-space corners, what it belongs to and which face it is. */
    private record Quad(Part part, Direction face, Vector3f[] view, boolean blends, boolean culls) {
    }

    /** One custom geometry submission, in the order GloveSpecialRenderer submitted it. */
    private record Submission(RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer, PoseStack.Pose pose) {
    }

    /**
     * Over one pulse of the core, every core face that faces the camera
     * reaches the screen over the area the nearest-opaque resolve gives it,
     * and no core pixel shows where the glove stands in front of it.
     */
    @Test
    void everyCoreFaceDrawsTheAreaTheHandLeavesItOverAPulse() {
        Map<Integer, String> wrongTicks = new TreeMap<>();
        for (int tick = 0; tick < PULSE_TICKS; tick++) {
            List<Quad> quads = heldOutPalmQuads(tick);
            Raster drawn = Raster.handPass(quads);
            Raster truth = Raster.nearestOpaque(quads);
            Map<Direction, Integer> shown = new EnumMap<>(Direction.class);
            Map<Direction, Integer> owed = new EnumMap<>(Direction.class);
            Map<Direction, Integer> buried = new EnumMap<>(Direction.class);
            for (int i = 0; i < drawn.owner.length; i++) {
                Quad got = drawn.owner[i];
                Quad nearest = truth.owner[i];
                if (nearest != null && nearest.part() == Part.CORE) {
                    owed.merge(nearest.face(), 1, Integer::sum);
                }
                if (got != null && got.part() == Part.CORE) {
                    shown.merge(got.face(), 1, Integer::sum);
                    if (nearest == null || nearest.part() != Part.CORE) {
                        buried.merge(got.face(), 1, Integer::sum);
                    }
                }
            }
            assertEquals(3, owed.size(), "the core shows three faces to the camera at tick " + tick + ": " + owed);
            if (!shown.equals(owed) || !buried.isEmpty()) {
                wrongTicks.put(tick, "drawn " + shown + ", owed " + owed + ", drawn where the glove hides it " + buried);
            }
        }
        assertEquals(Map.of(), wrongTicks, "core pixels per face, by game tick of the pulse");
    }

    /**
     * Submits the glove with a blob at rest size through GloveSpecialRenderer
     * in the held-out first-person hand at a game tick, and reads every quad back.
     */
    private static List<Quad> heldOutPalmQuads(long gameTime) {
        PoseStack poseStack = new PoseStack();
        AbilityPoses.HandOffset hand = AbilityPoses.HELD_OUT_HAND;
        poseStack.translate(hand.across(), hand.lift(), -hand.forward());
        poseStack.translate(ARM.x(), ARM.y(), ARM.z());
        firstPersonRightHand().apply(false, poseStack.last());

        List<Submission> submissions = new ArrayList<>();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            PoseStack at = call.getArgument(0);
            submissions.add(new Submission(call.getArgument(1), call.getArgument(2), at.last().copy()));
            return null;
        }).when(collector).submitCustomGeometry(any(PoseStack.class), any(RenderType.class), any());

        Minecraft minecraft = mock(Minecraft.class);
        // deep stubs answer the level's registry lookup for the goo type's color with nothing
        ClientLevel level = mock(ClientLevel.class, RETURNS_DEEP_STUBS);
        when(level.getGameTime()).thenReturn(gameTime);
        minecraft.level = level;
        when(minecraft.getDeltaTracker()).thenReturn(mock(DeltaTracker.class));
        QuadCollection body = mock(QuadCollection.class);
        when(body.getAll()).thenReturn(gloveBodyQuads());
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class);
             MockedStatic<GloveBodyModels> models = mockStatic(GloveBodyModels.class);
             MockedStatic<GooRenderUtil> util = mockStatic(GooRenderUtil.class, CALLS_REAL_METHODS)) {
            mc.when(Minecraft::getInstance).thenReturn(minecraft);
            models.when(() -> GloveBodyModels.getModel(any())).thenReturn(body);
            util.when(() -> GooRenderUtil.lookupFluidSprite(any())).thenReturn(sprite);
            new GloveSpecialRenderer().submit(new GloveSpecialRenderer.GloveData((Item) null, GooTypes.BLAZE, 0f, 1f),
                    poseStack, collector, 0, 0, false, 0);
            return readBack(submissions);
        }
    }

    /** Runs each submission's geometry at its pose and at the model's own origin, the latter naming each quad's face. */
    private static List<Quad> readBack(List<Submission> submissions) {
        Part[] parts = {Part.BODY, Part.CORE, Part.SHELL};
        assertEquals(parts.length, submissions.size(), "the glove submits its body, its core and its shell");
        List<Quad> quads = new ArrayList<>();
        for (int s = 0; s < parts.length; s++) {
            Submission submission = submissions.get(s);
            RecordingVertexConsumer view = new RecordingVertexConsumer();
            submission.renderer().render(submission.pose(), view);
            RecordingVertexConsumer model = new RecordingVertexConsumer();
            submission.renderer().render(new PoseStack().last(), model);
            List<RecordingVertexConsumer.Vertex> v = view.vertices();
            List<RecordingVertexConsumer.Vertex> m = model.vertices();
            boolean blends = submission.type().hasBlending();
            boolean culls = submission.type().pipeline().isCull();
            for (int q = 0; q + 3 < v.size(); q += 4) {
                Vector3f[] corners = new Vector3f[4];
                for (int c = 0; c < 4; c++) {
                    corners[c] = new Vector3f(v.get(q + c).x(), v.get(q + c).y(), v.get(q + c).z());
                }
                RecordingVertexConsumer.Vertex n = m.get(q);
                Direction face = Direction.getApproximateNearest(n.nx(), n.ny(), n.nz());
                quads.add(new Quad(parts[s], face, corners, blends, culls));
            }
        }
        return quads;
    }

    /** The firstperson_righthand display transform goo_glove.json gives the glove. */
    private static ItemTransform firstPersonRightHand() {
        JsonObject display = readJson("/assets/goo/models/item/goo_glove.json").getAsJsonObject("display")
                .getAsJsonObject("firstperson_righthand");
        Vector3f translation = vector(display, "translation").mul(PX);
        return new ItemTransform(vector(display, "rotation"), translation, vector(display, "scale"));
    }

    /** The glove body's boxes from glove_body.json, each face a baked quad wound as RenderContext winds a box. */
    private static List<BakedQuad> gloveBodyQuads() {
        JsonArray elements = readJson("/assets/goo/models/item/glove_body.json").getAsJsonArray("elements");
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(new PoseStack().last(), recorder, 0);
        for (JsonElement element : elements) {
            Vector3f from = vector(element.getAsJsonObject(), "from").mul(PX);
            Vector3f to = vector(element.getAsJsonObject(), "to").mul(PX);
            ctx.emitBox(-1, new CuboidBounds(from.x(), to.x(), from.z(), to.z(), from.y(), to.y()),
                    new GooRenderUtil.UvRect(0f, 0f, 1f, 1f));
        }
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                sprite, ChunkSectionLayer.TRANSLUCENT, null, -1, true, 0, false);
        List<RecordingVertexConsumer.Vertex> v = recorder.vertices();
        List<BakedQuad> quads = new ArrayList<>();
        for (int q = 0; q + 3 < v.size(); q += 4) {
            Direction face = Direction.getApproximateNearest(v.get(q).nx(), v.get(q).ny(), v.get(q).nz());
            quads.add(new BakedQuad(position(v.get(q)), position(v.get(q + 1)), position(v.get(q + 2)),
                    position(v.get(q + 3)), 0L, 0L, 0L, 0L, face, material));
        }
        return quads;
    }

    private static Vector3f position(RecordingVertexConsumer.Vertex vertex) {
        return new Vector3f(vertex.x(), vertex.y(), vertex.z());
    }

    private static Vector3f vector(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static JsonObject readJson(String path) {
        try (InputStream in = HeldOutGloveHandTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, path + " is not on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(path, e);
        }
    }

    /**
     * A software depth buffer over the screen, recording for each pixel the
     * glove or core quad the color there came from; a shell quad that passes
     * blends over that color without replacing it, and writes depth.
     */
    private static final class Raster {
        private final float[] depth = new float[WIDTH * HEIGHT];
        private final Quad[] owner = new Quad[WIDTH * HEIGHT];
        private final Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(FOV_DEGREES), (float) WIDTH / HEIGHT, NEAR, FAR);

        private Raster() {
            Arrays.fill(depth, Float.POSITIVE_INFINITY);
        }

        /** The hand pass: opaque quads in submission order, then translucent quads sorted far to near, MeshData.sortQuads. */
        static Raster handPass(List<Quad> quads) {
            Raster raster = new Raster();
            quads.stream().filter(q -> !q.blends()).forEach(raster::draw);
            quads.stream().filter(Quad::blends)
                    .sorted(Comparator.comparingDouble((Quad q) -> centroid(q).lengthSquared()).reversed())
                    .forEach(raster::draw);
            return raster;
        }

        /** What a correct depth resolve shows: the nearest glove or core surface at each pixel, order aside. */
        static Raster nearestOpaque(List<Quad> quads) {
            Raster raster = new Raster();
            quads.stream().filter(q -> q.part() != Part.SHELL)
                    .map(q -> new Quad(q.part(), q.face(), q.view(), false, false)).forEach(raster::draw);
            return raster;
        }

        /** MeshData.unpackQuadCentroids: the midpoint of a quad's first and third corners. */
        private static Vector3f centroid(Quad q) {
            return new Vector3f(q.view()[0]).add(q.view()[2]).mul(0.5f);
        }

        private void draw(Quad q) {
            float[][] s = new float[4][];
            for (int c = 0; c < 4; c++) {
                Vector4f clip = projection.transform(new Vector4f(q.view()[c], 1f));
                s[c] = new float[] {(clip.x / clip.w + 1f) * 0.5f * WIDTH, (1f - clip.y / clip.w) * 0.5f * HEIGHT, clip.z / clip.w};
            }
            triangle(q, s[0], s[1], s[2]);
            triangle(q, s[2], s[3], s[0]);
        }

        private void triangle(Quad q, float[] a, float[] b, float[] c) {
            float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            // screen y runs down, so a counter-clockwise front face has negative area here
            if (area == 0f || (q.culls() && area > 0f)) {
                return;
            }
            int x0 = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
            int x1 = Math.min(WIDTH - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
            int y0 = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
            int y1 = Math.min(HEIGHT - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    float px = x + 0.5f;
                    float py = y + 0.5f;
                    float wa = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
                    float wb = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
                    float wc = 1f - wa - wb;
                    if (wa < 0f || wb < 0f || wc < 0f) {
                        continue;
                    }
                    float z = wa * a[2] + wb * b[2] + wc * c[2];
                    int i = y * WIDTH + x;
                    if (z <= depth[i]) {
                        depth[i] = z;
                        // the glove's texture is opaque on any render type; only the shell lets color through
                        if (q.part() != Part.SHELL) {
                            owner[i] = q;
                        }
                    }
                }
            }
        }
    }
}
