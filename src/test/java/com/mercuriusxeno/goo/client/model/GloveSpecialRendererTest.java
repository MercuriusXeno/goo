package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.ability.KnivesCharge;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.ability.SprayingClient;
import com.mercuriusxeno.goo.client.outsider.RoomBodyCopyRenderer;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * The glove body submits on the cutout block-atlas type and the palm blob's
 * core on the solid one (decision diagnose-and-correct-the-palm-core-cull).
 * The hand the aim line starts from: the first-person glove stores it, and a
 * glove drawn on a copy of the player's body in an Outsider room's copies
 * leaves it where the player's own glove put it
 * (decision room-tiles-to-render-distance-held-to-the-picture). The palm
 * blob a glove extracts: none while its holder's blob is off the palm at the
 * spray source or settling back, and the blob at rest size once it arrives
 * (decision palm-blob-lifts-as-the-spray-source).
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
            assertEquals(GooTypes.ZOO, data.selectedType());
            assertEquals(KnivesCharge.palmScaleAt(0f), data.blobScale(), "the palm blob should be at rest size");
        }
    }
}
