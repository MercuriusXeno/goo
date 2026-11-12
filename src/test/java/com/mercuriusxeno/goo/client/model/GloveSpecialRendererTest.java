package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.outsider.RoomBodyCopyRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the hand the aim line starts from: a glove drawn on a copy of the
 * player's body in an Outsider room's copies leaves the hand where the
 * player's own glove put it (decision room-tiles-to-render-distance-held-to-the-picture).
 */
class GloveSpecialRendererTest {

    /** Where a far copy of the player stands from the camera, a room width or more away. */
    private static final double COPY_AWAY = 30;

    @Test
    void aGloveOnABodysCopyLeavesTheHandWhereThePlayersOwnGlovePutIt() {
        GloveSpecialRenderer.captureGooCenter(new PoseStack(), 0F, 0F, 0F);
        PoseStack copy = new PoseStack();
        copy.translate(COPY_AWAY, 0, 0);

        RoomBodyCopyRenderer.whileDrawingCopies(() -> GloveSpecialRenderer.captureGooCenter(copy, 0F, 0F, 0F));

        assertEquals(Vec3.ZERO, GloveSpecialRenderer.getLastGooCenterCamRel(),
                "the aim line's hand should stay the player's own, not the copy's");
    }
}
