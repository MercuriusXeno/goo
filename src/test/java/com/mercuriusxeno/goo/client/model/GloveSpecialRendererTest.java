package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.ability.HandPassView;
import com.mercuriusxeno.goo.client.ability.KnivesCharge;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.ability.SprayingClient;
import com.mercuriusxeno.goo.client.outsider.RoomBodyCopyRenderer;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
            + (long) Math.ceil(LiftedPalmBlob.EASE_TICKS);
    /** Where a far copy of the player stands from the camera, a room width or more away. */
    private static final double COPY_AWAY = 30;
    /** Where the first-person glove's goo sits before the eye. */
    private static final double HAND_AHEAD = 0.5;
    private static final double WITHIN = 1e-5;

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
            long eased = SPRAYED + LiftedPalmBlob.EASE_TICKS;
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
