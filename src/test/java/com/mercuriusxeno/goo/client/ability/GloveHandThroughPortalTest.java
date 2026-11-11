package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.model.GloveBodyModels;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The aim line starts at the hand the player holds before its own eyes: a
 * glove drawn on the player's body in a view through a portal stands
 * somewhere else, measured from the view's camera, and leaves the hand the
 * aim line starts from where the first-person glove put it
 * (decision portals-link-by-looking-through-portal-vision).
 */
class GloveHandThroughPortalTest {

    /** How far the glove on the body seen through a portal stands from the view's camera. */
    private static final double THROUGH_A_PORTAL = 40.0;

    @Test
    void aGloveDrawnThroughAPortalLeavesTheAimLinesHandWhereItWas() {
        Minecraft minecraft = mock(Minecraft.class);
        minecraft.level = mock(ClientLevel.class, RETURNS_DEEP_STUBS);
        when(minecraft.getDeltaTracker()).thenReturn(mock(DeltaTracker.class));
        QuadCollection body = mock(QuadCollection.class);
        when(body.getAll()).thenReturn(List.of());
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class);
             MockedStatic<GloveBodyModels> models = mockStatic(GloveBodyModels.class);
             MockedStatic<GooRenderUtil> util = mockStatic(GooRenderUtil.class, CALLS_REAL_METHODS)) {
            mc.when(Minecraft::getInstance).thenReturn(minecraft);
            models.when(() -> GloveBodyModels.getModel(any())).thenReturn(body);
            util.when(() -> GooRenderUtil.lookupFluidSprite(any())).thenReturn(mock(TextureAtlasSprite.class));

            submitGloveAt(new PoseStack());
            Vec3 firstPerson = GloveSpecialRenderer.getLastGooCenterCamRel();
            try (MockedStatic<PortalView> view = mockStatic(PortalView.class)) {
                view.when(PortalView::drawing).thenReturn(true);
                PoseStack throughAPortal = new PoseStack();
                throughAPortal.translate(THROUGH_A_PORTAL, THROUGH_A_PORTAL, THROUGH_A_PORTAL);
                submitGloveAt(throughAPortal);
            }
            assertEquals(firstPerson, GloveSpecialRenderer.getLastGooCenterCamRel(), "A glove drawn in a view "
                    + "through a portal should leave the hand the aim line starts from where the first-person "
                    + "glove put it");
        }
    }

    private static void submitGloveAt(PoseStack poseStack) {
        new GloveSpecialRenderer().submit(new GloveSpecialRenderer.GloveData((Item) null, GooTypes.BLAZE, 0f, 1f),
                poseStack, mock(SubmitNodeCollector.class), 0, 0, false, 0);
    }
}
