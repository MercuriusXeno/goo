package com.mercuriusxeno.goo.client.tartarus;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooSubmitter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The nether portal's texture over the view while a Tartarus rift stands at
 * the camera: a first-person caster's own rift reaching them as it takes
 * them, and leaving them as it backs off on arrival, fading in over the
 * last third of a block.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class TartarusCover {

    /** The cover the local view draws. */
    public static final TartarusCover LOCAL = new TartarusCover();
    /** How near the camera a rift's foot stands before its texture starts to cover the view, in blocks. */
    static final double COVERS_WITHIN = 0.35;
    /** The cover's tiles across the screen's height: the artifact draft's texel size. */
    private static final double TILES_DOWN = 6.25;
    /** Layers of the sprite the cover draws, so its translucent texels come near opaque. */
    private static final int LAYERS = 2;

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "tartarus_cover");

    private double alpha;

    private TartarusCover() {
    }

    /**
     * How much of the view a rift covers, standing a distance from the camera across the ground.
     *
     * @param distance the horizontal distance from the camera to the rift's foot, in blocks
     * @return the cover's opacity, 0 to 1
     */
    static double alphaAt(double distance) {
        return Math.clamp(1 - distance / COVERS_WITHIN, 0, 1);
    }

    /**
     * Reads a rift drawn this frame, the nearest to the camera setting the cover.
     *
     * @param foot   the middle of the rift's foot
     * @param camera the camera's world position
     */
    void seeGateAt(Vec3 foot, Vec3 camera) {
        alpha = Math.max(alpha, alphaAt(foot.subtract(camera).horizontalDistance()));
    }

    /**
     * Registers the cover drawn over everything.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, TartarusCover::drawCover);
    }

    private static void drawCover(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        double alpha = LOCAL.alpha;
        LOCAL.alpha = 0;
        if (alpha <= 0 || Minecraft.getInstance().screen != null) {
            return;
        }
        TextureAtlasSprite sprite = GooSubmitter.blockSprite(TartarusGateRenderer.TEXTURE);
        int tile = Math.max(1, (int) Math.round(graphics.guiHeight() / TILES_DOWN));
        int color = ARGB.white((float) alpha);
        // the draft covers with the texture's texels opaque; two layers of the translucent sprite come near it
        for (int layer = 0; layer < LAYERS; layer++) {
            for (int y = 0; y < graphics.guiHeight(); y += tile) {
                for (int x = 0; x < graphics.guiWidth(); x += tile) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, tile, tile, color);
                }
            }
        }
    }
}
