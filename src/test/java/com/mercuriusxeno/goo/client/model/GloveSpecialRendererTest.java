package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.ability.HandPassView;
import com.mercuriusxeno.goo.client.ability.KnivesCharge;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.ability.PalmBlobHop;
import com.mercuriusxeno.goo.client.ability.SprayingClient;
import com.mercuriusxeno.goo.client.outsider.RoomBodyCopyRenderer;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The glove body submits on the cutout block-atlas type and the palm blob's
 * core on the solid one (decision diagnose-and-correct-the-palm-core-cull).
 * The hand the aim line starts from: the first-person glove stores it, and a
 * glove drawn on a copy of the player's body in an Outsider room's copies
 * leaves it where the player's own glove put it
 * (decision room-tiles-to-render-distance-held-to-the-picture). The palm
 * blob a glove extracts: none while its holder's blob is off the palm at the
 * spray source or settling back, and the blob at rest size once it arrives;
 * the local first-person player's lifted blob draws in the hand's pass, over
 * the glove, beginning as the palm blob itself and easing to where the spray
 * source shows (decision palm-blob-lifts-as-the-spray-source).
 */
class GloveSpecialRendererTest {

    private static final Identifier BLOCK_ATLAS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    /** The game time of the holder's last stream tick. */
    private static final long SPRAYED = 400L;
    /**
     * The game tick the holder's blob arrives back on the palm: the spray
     * reads ended a tick past the stream's gap, and the blob settles over its ease.
     */
    private static final long SETTLED = SPRAYED + LiftedPalmBlob.STREAM_GAP_TICKS + 1
            + PalmBlobHop.EASE_TICKS;
    /** Where a far copy of the player stands from the camera, a room width or more away. */
    private static final double COPY_AWAY = 30;
    /** Where the first-person glove's goo sits before the eye. */
    private static final double HAND_AHEAD = 0.5;
    private static final double WITHIN = 1e-5;
    /** A glove model's pixels to a block. */
    private static final float PIXELS = 16f;
    /** A tenth of a model pixel, in blocks: the least gap that keeps two faces from sharing a plane. */
    private static final float SIDE_CLEARANCE = 0.1f / PIXELS;

    @Test
    void theBodySubmitsOnTheCutoutBlockAtlasType() {
        assertEquals(RenderTypes.entityCutout(BLOCK_ATLAS), GloveSpecialRenderer.bodyType());
    }

    @Test
    void theCoreSubmitsOnTheSolidBlockAtlasType() {
        assertEquals(RenderTypes.entitySolid(BLOCK_ATLAS), GloveSpecialRenderer.coreType());
    }

    @Test
    void aGloveOnABodysCopyLeavesTheHandWhereThePlayersOwnGlovePutIt() {
        GloveSpecialRenderer.captureGooCenter(new PoseStack(), 0F, 0F, 0F);
        PoseStack copy = new PoseStack();
        copy.translate(COPY_AWAY, 0, 0);

        RoomBodyCopyRenderer.whileDrawingCopies(() -> GloveSpecialRenderer.captureGooCenter(copy, 0F, 0F, 0F));

        assertEquals(Vec3.ZERO, GloveSpecialRenderer.getLastGooCenterCamRel(),
                "the aim line's hand should stay the player's own, not the copy's");
    }

    @Test
    void theFirstPersonGloveOutsideAnyViewOrCopyStoresTheHand() {
        GloveSpecialRenderer.captureGooCenter(new PoseStack(), 0F, 0F, 0F);
        PoseStack hand = new PoseStack();
        hand.translate(0, 0, -HAND_AHEAD);

        GloveSpecialRenderer.captureGooCenter(hand, 0F, 0F, 0F);

        assertEquals(new Vec3(0, 0, -HAND_AHEAD), GloveSpecialRenderer.getLastGooCenterCamRel(),
                "the aim line's hand should be where the first-person glove drew");
    }

    @Test
    void aGloveWhoseHoldersSprayRunsGivesThePalmNoBlobToDraw() {
        try (SprayingClient client = SprayingClient.another(GooTypes.ZOO);
             MockedStatic<GloveUseTracker> tracker = mockStatic(GloveUseTracker.class)) {
            tracker.when(GloveUseTracker::isSelectedTypeAvailable).thenReturn(true);
            client.at(SPRAYED);
            LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

            GloveSpecialRenderer.GloveData data = new GloveSpecialRenderer().extractArgument(client.glove());

            assertTrue(data.palmLifted(), "the palm should draw no blob while the lifted blob stands at the source");
        }
    }

    @Test
    void aGloveWhoseHoldersBlobIsStillSettlingGivesThePalmNoBlobToDraw() {
        try (SprayingClient client = SprayingClient.another(GooTypes.ZOO);
             MockedStatic<GloveUseTracker> tracker = mockStatic(GloveUseTracker.class)) {
            tracker.when(GloveUseTracker::isSelectedTypeAvailable).thenReturn(true);
            client.at(SETTLED - 1);
            LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

            GloveSpecialRenderer.GloveData data = new GloveSpecialRenderer().extractArgument(client.glove());

            assertTrue(data.palmLifted(), "the palm should draw no blob before the settling blob arrives");
        }
    }

    @Test
    void aGloveWhoseHoldersBlobHasSettledCarriesThePalmBlobAtRestSize() {
        try (SprayingClient client = SprayingClient.another(GooTypes.ZOO);
             MockedStatic<GloveUseTracker> tracker = mockStatic(GloveUseTracker.class)) {
            tracker.when(GloveUseTracker::isSelectedTypeAvailable).thenReturn(true);
            client.at(SETTLED);
            LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

            GloveSpecialRenderer.GloveData data = new GloveSpecialRenderer().extractArgument(client.glove());

            assertFalse(data.palmLifted(), "the palm should draw its blob again once the settling blob arrives");
            assertFalse(data.heldInView(), "another player's glove is not the one held before the eyes");
            assertEquals(GooTypes.ZOO, data.selectedType());
            assertEquals(KnivesCharge.palmScaleAt(0f), data.blobScale(), "the palm blob should be at rest size");
        }
    }

    @Test
    void theLocalFirstPersonPlayersGloveIsHeldInView() {
        try (SprayingClient client = SprayingClient.local(GooTypes.ZOO);
             MockedStatic<GloveUseTracker> tracker = mockStatic(GloveUseTracker.class)) {
            tracker.when(GloveUseTracker::isSelectedTypeAvailable).thenReturn(true);
            client.at(SPRAYED);

            assertTrue(new GloveSpecialRenderer().extractArgument(client.glove()).heldInView(),
                    "the glove before the local player's eyes should draw its lifted blob in the hand's pass");
        }
    }

    @Test
    void aLiftAtItsFirstTickDrawsTheCoreInTheHandsPassWhereThePalmBlobDraws() {
        try (SprayingClient client = SprayingClient.local(GooTypes.ZOO);
             MockedStatic<GloveBodyModels> models = bodiless()) {
            client.at(SPRAYED);
            LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

            Vec3 lifted = coreDrawnAt(new GloveSpecialRenderer.GloveData(null, GooTypes.ZOO, 0f, 1f, true, true));
            Vec3 resting = coreDrawnAt(new GloveSpecialRenderer.GloveData(null, GooTypes.ZOO, 0f, 1f, false, true));

            assertEquals(0, lifted.distanceTo(resting), WITHIN, "the lift should begin as the palm blob itself, "
                    + "drawn at " + lifted + " where the palm blob draws at " + resting);
        }
    }

    @Test
    void aLiftAtTheEasesEndDrawsTheCoreInTheHandsPassWhereTheSprayingSourceShows() {
        try (SprayingClient client = SprayingClient.local(GooTypes.ZOO);
             MockedStatic<GloveBodyModels> models = bodiless()) {
            long eased = SPRAYED + PalmBlobHop.LIFT_TICKS;
            client.at(eased);
            for (long tick = SPRAYED; tick <= eased; tick++) {
                LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
            }

            Vec3 lifted = coreDrawnAt(new GloveSpecialRenderer.GloveData(null, GooTypes.ZOO, 0f, 1f, true, true));

            Vec3 shown = HandPassView.toHandPass(SprayingClient.sourceOffTheEye(), client.camera().rotation(),
                    SprayingClient.WORLD_FOV);
            assertEquals(0, lifted.distanceTo(shown), WITHIN, "the eased blob should draw where the spray source "
                    + "shows, " + shown + ", drawn at " + lifted);
        }
    }

    /**
     * The palm blob the glove draws, first person, in another player's hand
     * and in the inventory slot alike, sits pressed on the palm: its shell's
     * bottom on the palm floor and its back on the palm's back face, at rest
     * and at Knives' full charge, which swells it evenly
     * (decision palm-blob-rests-pressed-and-stands-into-the-cube).
     */
    @Nested
    class PressedOnThePalm {

        @ParameterizedTest
        @ValueSource(floats = {0f, 1f})
        void theShellRestsOnThePalmFloorWithItsBackOnThePalmsBackFace(float chargeShare) {
            Box shell = shellDrawnAt(KnivesCharge.palmScaleAt(chargeShare));

            assertEquals(GloveSpecialRenderer.PALM_FLOOR_PX / PIXELS, shell.min().y(), WITHIN,
                    "the shell's bottom should rest on the palm floor");
            assertEquals(GloveSpecialRenderer.PALM_BACK_Z_PX / PIXELS, shell.max().z(), WITHIN,
                    "the shell's back should sit on the palm's back face");
        }

        @Test
        void theRestingShellIsPressedWiderAndDeeperThanTall() {
            Vector3f rest = shellDrawnAt(KnivesCharge.palmScaleAt(0f)).size();

            assertEquals(rest.x(), rest.z(), WITHIN, "the pressed shell should be as deep as it is wide");
            assertTrue(rest.x() > rest.y(), "the pressed shell " + rest + " should be wider than tall");
        }

        /**
         * A shell side on the hand's side fights it for the same pixels, so
         * the resting shell stands inside the hand's sides, clear of them.
         * operator ruling 2026-11-13: the pressed blob's sides sat flush with the gauntlet's and z-fought,
         * and the operator chose the sides stand just inside the hand
         */
        @Test
        void theRestingShellsSidesStandInsideTheHandsSides() throws IOException {
            Box shell = shellDrawnAt(KnivesCharge.palmScaleAt(0f));
            JsonArray hand = handOfTheGloveBody();
            float handLeft = hand.get(0).getAsFloat() / PIXELS;
            float handRight = hand.get(1).getAsFloat() / PIXELS;

            assertTrue(shell.min().x() > handLeft + SIDE_CLEARANCE, "the shell's side " + shell.min().x()
                    + " should stand inside the hand's side " + handLeft);
            assertTrue(shell.max().x() < handRight - SIDE_CLEARANCE, "the shell's side " + shell.max().x()
                    + " should stand inside the hand's side " + handRight);
        }

        /** The glove body's hand element's left and right sides along x, in model pixels. */
        private static JsonArray handOfTheGloveBody() throws IOException {
            try (InputStream model = GloveSpecialRendererTest.class.getResourceAsStream(
                    "/assets/goo/models/item/glove_body.json")) {
                JsonObject body = StreamSupport.stream(JsonParser.parseReader(new InputStreamReader(
                                Objects.requireNonNull(model), StandardCharsets.UTF_8)).getAsJsonObject()
                                .getAsJsonArray("elements").spliterator(), false)
                        .map(JsonElement::getAsJsonObject)
                        .filter(element -> "body".equals(element.get("name").getAsString()))
                        .findFirst().orElseThrow();
                JsonArray sides = new JsonArray();
                sides.add(body.getAsJsonArray("from").get(0));
                sides.add(body.getAsJsonArray("to").get(0));
                return sides;
            }
        }

        @Test
        void knivesFullChargeSwellsThePressedShellEvenly() {
            Vector3f rest = shellDrawnAt(KnivesCharge.palmScaleAt(0f)).size();
            Vector3f fullest = shellDrawnAt(KnivesCharge.palmScaleAt(1f)).size();

            float full = KnivesCharge.palmScaleAt(1f);
            assertEquals(full * rest.x(), fullest.x(), WITHIN, "the full charge's width");
            assertEquals(full * rest.z(), fullest.z(), WITHIN, "the full charge's depth");
            assertEquals(full * rest.y(), fullest.y(), WITHIN, "the full charge's height");
        }

        /**
         * The box the glove's palm shell spans in the glove's model, in block
         * units, drawn through the one palm draw every view of the glove takes.
         */
        private static Box shellDrawnAt(float blobScale) {
            try (SprayingClient client = SprayingClient.another(GooTypes.ZOO);
                 MockedStatic<GloveBodyModels> models = bodiless();
                 MockedStatic<GooRenderUtil> util = mockStatic(GooRenderUtil.class, CALLS_REAL_METHODS);
                 MockedStatic<ClientGooTypes> colors = mockStatic(ClientGooTypes.class)) {
                util.when(() -> GooRenderUtil.lookupFluidSprite(any())).thenReturn(mock(TextureAtlasSprite.class));
                RecordingVertexConsumer shell = new RecordingVertexConsumer();
                SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
                doAnswer(call -> {
                    if (GooSubmitter.renderType().equals(call.getArgument(1))) {
                        call.<SubmitNodeCollector.CustomGeometryRenderer>getArgument(2)
                                .render(call.<PoseStack>getArgument(0).last(), shell);
                    }
                    return null;
                }).when(collector).submitCustomGeometry(any(), any(), any());

                new GloveSpecialRenderer().submit(new GloveSpecialRenderer.GloveData(null, GooTypes.ZOO, 0f,
                        blobScale, false, false), new PoseStack(), collector, 0, 0, false, 0);

                return Box.of(shell.vertices());
            }
        }
    }

    /** The corners of the box a set of vertices spans. */
    private record Box(Vector3f min, Vector3f max) {

        static Box of(List<RecordingVertexConsumer.Vertex> vertices) {
            Vector3f min = new Vector3f(Float.MAX_VALUE);
            Vector3f max = new Vector3f(-Float.MAX_VALUE);
            for (RecordingVertexConsumer.Vertex vertex : vertices) {
                Vector3f at = new Vector3f(vertex.x(), vertex.y(), vertex.z());
                min.min(at);
                max.max(at);
            }
            return new Box(min, max);
        }

        Vector3f size() {
            return new Vector3f(max).sub(min);
        }
    }

    private static MockedStatic<GloveBodyModels> bodiless() {
        QuadCollection body = mock(QuadCollection.class);
        when(body.getAll()).thenReturn(List.of());
        MockedStatic<GloveBodyModels> models = mockStatic(GloveBodyModels.class);
        models.when(() -> GloveBodyModels.getModel(any())).thenReturn(body);
        return models;
    }

    /**
     * Where a glove held in a turned, shrunk hand draws its blob's core, in
     * the hand pass's camera-relative world axes.
     */
    private static Vec3 coreDrawnAt(GloveSpecialRenderer.GloveData data) {
        PoseStack hand = new PoseStack();
        hand.translate(HAND_AHEAD * 0.6, -HAND_AHEAD * 0.8, -HAND_AHEAD);
        hand.mulPose(new Quaternionf().rotationXYZ(1.2f, 0.4f, -0.3f));
        hand.scale(0.5f, 0.5f, 0.5f);
        List<Vec3> cores = new ArrayList<>();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            if (call.getArgument(1) == GloveSpecialRenderer.coreType()) {
                Vector3f at = call.<PoseStack>getArgument(0).last().pose().transformPosition(new Vector3f());
                cores.add(new Vec3(at));
            }
            return null;
        }).when(collector).submitCustomGeometry(any(), any(), any());

        new GloveSpecialRenderer().submit(data, hand, collector, 0, 0, false, 0);

        assertEquals(1, cores.size(), "the glove should draw one core");
        return cores.get(0);
    }
}
