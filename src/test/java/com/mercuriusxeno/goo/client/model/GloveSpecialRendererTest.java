package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.ability.KnivesCharge;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.ability.SprayingClient;
import com.mercuriusxeno.goo.client.outsider.RoomBodyCopyRenderer;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers the hand the aim line starts from: the first-person glove stores it,
 * and a glove drawn on a copy of the player's body in an Outsider room's
 * copies leaves it where the player's own glove put it
 * (decision room-tiles-to-render-distance-held-to-the-picture). Covers the
 * palm blob a glove extracts: none while its holder's spray lifts it to the
 * spray source, and the blob at rest size once the spray ends
 * (decision palm-blob-lifts-as-the-spray-source).
 */
class GloveSpecialRendererTest {

    /** The game time of the holder's last stream tick. */
    private static final long SPRAYED = 400L;
    /** Ticks after the last stream tick past the stream's gap, by which the spray reads ended. */
    private static final long STREAM_STOPPED = 10L;
    /** Where a far copy of the player stands from the camera, a room width or more away. */
    private static final double COPY_AWAY = 30;
    /** Where the first-person glove's goo sits before the eye. */
    private static final double HAND_AHEAD = 0.5;

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
    void aGloveWhoseHoldersStreamTicksStoppedCarriesThePalmBlobAtRestSize() {
        try (SprayingClient client = SprayingClient.another(GooTypes.ZOO);
             MockedStatic<GloveUseTracker> tracker = mockStatic(GloveUseTracker.class)) {
            tracker.when(GloveUseTracker::isSelectedTypeAvailable).thenReturn(true);
            client.at(SPRAYED + STREAM_STOPPED);
            LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, SPRAYED);

            GloveSpecialRenderer.GloveData data = new GloveSpecialRenderer().extractArgument(client.glove());

            assertFalse(data.palmLifted(), "the palm should draw its blob again once the spray ends");
            assertEquals(GooTypes.ZOO, data.selectedType());
            assertEquals(KnivesCharge.palmScaleAt(0f), data.blobScale(), "the palm blob should be at rest size");
        }
    }
}
