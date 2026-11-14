package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.ability.HandPassView;
import com.mercuriusxeno.goo.client.ability.KnivesCharge;
import com.mercuriusxeno.goo.client.ability.KnivesCharging;
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
import org.joml.Matrix4f;
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
            + PalmBlobHop.SETTLE_TICKS;
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
            LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

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
            LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

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
            LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

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
            LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

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
                LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
            }

            Vec3 lifted = coreDrawnAt(new GloveSpecialRenderer.GloveData(null, GooTypes.ZOO, 0f, 1f, true, true));

            Vec3 shown = HandPassView.toHandPass(SprayingClient.sourceOffTheEye(), client.camera().rotation(),
                    SprayingClient.WORLD_FOV, new Matrix4f());
            assertEquals(0, lifted.distanceTo(shown), WITHIN, "the eased blob should draw where the spray source "
                    + "shows, " + shown + ", drawn at " + lifted);
        }
    }

    /**
     * Knives' charge lifts the blob off the palm from its first tick, leaving
     * the palm nothing to draw; the hand's pass faces the floating blob along
     * the look whatever the glove's turn; and once the blob settles after the
     * backhand's sweep the palm draws its blob at rest size again (decisions
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives, charge-swells-the-palm-blob).
     */
    @Nested
    class KnivesChargeFloatsTheBlob {

        /** The hand turned as the wind-up begins and as it reaches its furthest across the chest. */
        private static final Quaternionf AT_THE_START = new Quaternionf().rotationXYZ(1.2f, 0.4f, -0.3f);
        private static final Quaternionf AT_THE_FURTHEST = new Quaternionf().rotationXYZ(0.9f, -0.5f, 0.35f);

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void fromTheChargesFirstTickThePalmHasNoBlobToDraw(boolean local) {
            try (SprayingClient client = local ? SprayingClient.local(GooTypes.CRYSTAL)
                    : SprayingClient.another(GooTypes.CRYSTAL);
                 KnivesCharging knives = KnivesCharging.heldTo(0f);
                 MockedStatic<GloveUseTracker> tracker = mockStatic(GloveUseTracker.class)) {
                tracker.when(GloveUseTracker::isSelectedTypeAvailable).thenReturn(true);
                knives.windUp(client, SPRAYED, SPRAYED);

                assertTrue(new GloveSpecialRenderer().extractArgument(client.glove()).palmLifted(),
                        "from the charge's first tick the palm should draw no blob");
            }
        }

        @Test
        void theHandsPassFacesTheFloatingBlobAlongTheLookWhateverTheGlovesTurn() {
            try (SprayingClient client = SprayingClient.local(GooTypes.CRYSTAL);
                 KnivesCharging knives = KnivesCharging.heldTo(0f);
                 MockedStatic<GloveBodyModels> models = bodiless()) {
                knives.windUp(client, SPRAYED, SPRAYED + PalmBlobHop.LIFT_TICKS);
                Quaternionf facing = LiftedPalmBlob.facingTheLook(SprayingClient.PITCH, SprayingClient.YAW);
                GloveSpecialRenderer.GloveData lifted = new GloveSpecialRenderer.GloveData(null, GooTypes.CRYSTAL,
                        0f, 1f, true, true);

                for (Quaternionf handTurn : List.of(AT_THE_START, AT_THE_FURTHEST)) {
                    Quaternionf drawn = coreTurnDrawnAt(lifted, handTurn);

                    assertNear(new Vector3f(0, 0, 1).rotate(facing), new Vector3f(0, 0, 1).rotate(drawn), "front");
                    assertNear(new Vector3f(0, 1, 0).rotate(facing), new Vector3f(0, 1, 0).rotate(drawn), "top");
                }
            }
        }

        @Test
        void fromTheSettlesEndAfterTheSweepThePalmCarriesItsBlobAtRestSize() {
            long released = SPRAYED + 20;
            long swept = released + KnivesCharging.KNIVES.sweepTicks();
            long settled = swept + LiftedPalmBlob.STREAM_GAP_TICKS + 1 + PalmBlobHop.SETTLE_TICKS;
            try (SprayingClient client = SprayingClient.local(GooTypes.CRYSTAL);
                 KnivesCharging knives = KnivesCharging.heldTo(1f);
                 MockedStatic<GloveUseTracker> tracker = mockStatic(GloveUseTracker.class)) {
                tracker.when(GloveUseTracker::isSelectedTypeAvailable).thenReturn(true);
                knives.windUp(client, SPRAYED, released - 1);
                knives.release(client, released, settled);

                GloveSpecialRenderer.GloveData data = new GloveSpecialRenderer().extractArgument(client.glove());

                assertFalse(data.palmLifted(), "once settled the palm should draw its blob");
                assertEquals(KnivesCharge.palmScaleAt(0f), data.blobScale(), "the palm blob should be at rest size");
            }
        }

        private static void assertNear(Vector3f expected, Vector3f actual, String axis) {
            assertTrue(expected.distance(actual) < 1e-4f, "the blob's " + axis + " should lie along " + expected
                    + ", lay along " + actual);
        }
    }

    /**
     * The palm blob the glove draws, first person, in another player's hand
     * and in the inventory slot alike, lies pressed on the palm: thin along
     * the palm's normal and spread across it, its face toward the palm just
     * clear of the palm's face and its edge against the fingers, never inside
     * the hand, at rest and at Knives' full charge, which swells it evenly
     * (decision palm-blob-rests-pressed-and-stands-into-the-cube).
     */
    @Nested
    class PressedOnThePalm {

        @ParameterizedTest
        @ValueSource(floats = {0f, 1f})
        void theShellLiesOnThePalmsFaceWithItsEdgeAgainstTheFingers(float chargeShare) throws IOException {
            Box shell = shellDrawnAt(KnivesCharge.palmScaleAt(chargeShare));
            float palmFace = elementOfTheGloveBody("body").min().z();

            assertEquals(GloveSpecialRenderer.PALM_FLOOR_PX / PIXELS, shell.min().y(), WITHIN,
                    "the shell's edge should lie against the fingers");
            assertEquals(palmFace - GloveSpecialRenderer.PALM_CLEARANCE_PX / PIXELS, shell.max().z(), WITHIN,
                    "the shell's face toward the palm should rest just clear of the palm's face " + palmFace);
        }

        /**
         * operator ruling 2026-11-13: the blob was drawn into the hand and clipped through it settling back
         */
        @ParameterizedTest
        @ValueSource(floats = {0f, 1f})
        void theShellOverlapsNoPartOfTheGlove(float chargeShare) throws IOException {
            Box shell = shellDrawnAt(KnivesCharge.palmScaleAt(chargeShare));
            for (String part : List.of("body", "cuff", "finger_plate")) {
                Box glove = elementOfTheGloveBody(part);

                assertFalse(shell.overlaps(glove), "the shell " + shell + " should stand clear of the glove's "
                        + part + " " + glove);
            }
        }

        @Test
        void theRestingShellIsSpreadAcrossThePalmAndThinOffIt() {
            Vector3f rest = shellDrawnAt(KnivesCharge.palmScaleAt(0f)).size();

            assertEquals(rest.x(), rest.y(), WITHIN, "the pressed shell should run as far along the palm as across it");
            assertTrue(rest.x() > rest.z(), "the pressed shell " + rest + " should be wider than it is thick off the "
                    + "palm");
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
            Box hand = elementOfTheGloveBody("body");

            assertTrue(shell.min().x() > hand.min().x() + SIDE_CLEARANCE, "the shell's side " + shell.min().x()
                    + " should stand inside the hand's side " + hand.min().x());
            assertTrue(shell.max().x() < hand.max().x() - SIDE_CLEARANCE, "the shell's side " + shell.max().x()
                    + " should stand inside the hand's side " + hand.max().x());
        }

        /** A named element of the glove body's model, in block units. */
        private static Box elementOfTheGloveBody(String name) throws IOException {
            try (InputStream model = GloveSpecialRendererTest.class.getResourceAsStream(
                    "/assets/goo/models/item/glove_body.json")) {
                JsonObject element = StreamSupport.stream(JsonParser.parseReader(new InputStreamReader(
                                Objects.requireNonNull(model), StandardCharsets.UTF_8)).getAsJsonObject()
                                .getAsJsonArray("elements").spliterator(), false)
                        .map(JsonElement::getAsJsonObject)
                        .filter(candidate -> name.equals(candidate.get("name").getAsString()))
                        .findFirst().orElseThrow();
                return new Box(cornerOf(element.getAsJsonArray("from")), cornerOf(element.getAsJsonArray("to")));
            }
        }

        private static Vector3f cornerOf(JsonArray pixels) {
            return new Vector3f(pixels.get(0).getAsFloat(), pixels.get(1).getAsFloat(), pixels.get(2).getAsFloat())
                    .div(PIXELS);
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

        boolean overlaps(Box other) {
            return min.x() < other.max().x() && max.x() > other.min().x() && min.y() < other.max().y()
                    && max.y() > other.min().y() && min.z() < other.max().z() && max.z() > other.min().z();
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
        Vector3f at = corePoseDrawnAt(data, new Quaternionf().rotationXYZ(1.2f, 0.4f, -0.3f))
                .transformPosition(new Vector3f());
        return new Vec3(at);
    }

    /**
     * The turn a glove held in a hand turned some way draws its blob's core
     * in, in the hand pass's camera-relative world axes.
     */
    private static Quaternionf coreTurnDrawnAt(GloveSpecialRenderer.GloveData data, Quaternionf handTurn) {
        return corePoseDrawnAt(data, handTurn).getUnnormalizedRotation(new Quaternionf());
    }

    /** The pose a glove held in a turned, shrunk hand draws its blob's core in. */
    private static Matrix4f corePoseDrawnAt(GloveSpecialRenderer.GloveData data, Quaternionf handTurn) {
        PoseStack hand = new PoseStack();
        hand.translate(HAND_AHEAD * 0.6, -HAND_AHEAD * 0.8, -HAND_AHEAD);
        hand.mulPose(handTurn);
        hand.scale(0.5f, 0.5f, 0.5f);
        List<Matrix4f> cores = new ArrayList<>();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            if (call.getArgument(1) == GloveSpecialRenderer.coreType()) {
                cores.add(new Matrix4f(call.<PoseStack>getArgument(0).last().pose()));
            }
            return null;
        }).when(collector).submitCustomGeometry(any(), any(), any());

        new GloveSpecialRenderer().submit(data, hand, collector, 0, 0, false, 0);

        assertEquals(1, cores.size(), "the glove should draw one core");
        return cores.get(0);
    }
}
