package com.mercuriusxeno.goo.client.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;
import java.util.Arrays;

/**
 * One frame's vignette fills, gathered in the order they were drawn and
 * handed to the GUI as one element. The GUI places each element it is
 * handed by searching the elements already standing for one it overlaps,
 * so thousands of separate fills cost a search each; one element carrying
 * every fill, drawn in order through the same pipeline, covers the same
 * pixels at the same colors for one search.
 * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
 */
final class GuiFillBatch implements VignetteCanvas {

    /** The ints one fill takes: its four edges and its color. */
    private static final int INTS_A_FILL = 5;
    /** Where in a fill's ints each edge and its color stand: the far corner first, as the GUI orders a fill. */
    private static final int FAR_X = 0;
    private static final int FAR_Y = 1;
    private static final int NEAR_X = 2;
    private static final int NEAR_Y = 3;
    private static final int COLOR = 4;
    private static final int FIRST_FILLS = 1024;
    private static final int GROWTH = 2;

    private final int width;
    private final int height;
    private int[] fills = new int[FIRST_FILLS * INTS_A_FILL];
    private int count;

    /**
     * @param width  the GUI's width
     * @param height the GUI's height
     */
    GuiFillBatch(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public int guiWidth() {
        return width;
    }

    @Override
    public int guiHeight() {
        return height;
    }

    /**
     * Gathers a fill, its corners ordered as the GUI's own fill orders them,
     * so its quad's corners match the quad the GUI would have built.
     */
    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        if (count * INTS_A_FILL == fills.length) {
            fills = Arrays.copyOf(fills, fills.length * GROWTH);
        }
        int at = count * INTS_A_FILL;
        fills[at + FAR_X] = Math.max(minX, maxX);
        fills[at + FAR_Y] = Math.max(minY, maxY);
        fills[at + NEAR_X] = Math.min(minX, maxX);
        fills[at + NEAR_Y] = Math.min(minY, maxY);
        fills[at + COLOR] = color;
        count++;
    }

    /**
     * Hands every fill gathered to the GUI as one element, at the GUI's pose
     * and within its scissor.
     *
     * @param graphics the GUI graphics the frame draws on
     */
    void submitTo(GuiGraphicsExtractor graphics) {
        if (count == 0) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.peekScissorStack();
        ScreenRectangle screen = new ScreenRectangle(0, 0, width, height).transformMaxBounds(pose);
        ScreenRectangle bounds = scissor == null ? screen : scissor.intersection(screen);
        graphics.submitGuiElementRenderState(new FillsRenderState(pose, fills, count, scissor, bounds));
    }

    /**
     * Every fill of one frame's vignette, as one GUI element.
     *
     * @param pose        the GUI pose at submit time
     * @param fills       each fill's edges and color, five ints a fill
     * @param count       the fills gathered
     * @param scissorArea the scissor in force at submit time
     * @param bounds      the screen area the fills may cover
     */
    private record FillsRenderState(Matrix3x2f pose, int[] fills, int count, @Nullable ScreenRectangle scissorArea,
                                    @Nullable ScreenRectangle bounds) implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            for (int fill = 0; fill < count; fill++) {
                int at = fill * INTS_A_FILL;
                int color = fills[at + COLOR];
                vertexConsumer.addVertexWith2DPose(pose, fills[at + FAR_X], fills[at + FAR_Y]).setColor(color);
                vertexConsumer.addVertexWith2DPose(pose, fills[at + FAR_X], fills[at + NEAR_Y]).setColor(color);
                vertexConsumer.addVertexWith2DPose(pose, fills[at + NEAR_X], fills[at + NEAR_Y]).setColor(color);
                vertexConsumer.addVertexWith2DPose(pose, fills[at + NEAR_X], fills[at + FAR_Y]).setColor(color);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }
    }
}
