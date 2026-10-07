package com.mercuriusxeno.goo.client.radial;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Submits one petal to the GUI as live geometry: its fill sampling the fluid
 * sprite on the block atlas, so the fill animates as the atlas does, and its
 * solid edge on top.
 * decision petals-render-the-live-fluid
 * decision wedges-take-a-solid-edge
 */
final class PetalPainter {

    private static final int CORNERS_PER_QUAD = 4;
    private static final double HALF = 0.5;
    /** A sprite that fills its whole texture, 0 to 1 both ways. */
    private static final PetalLook.SpriteBox WHOLE_TEXTURE = new PetalLook.SpriteBox(0f, 1f, 0f, 1f);

    private PetalPainter() {
    }

    /**
     * Submits a petal's fill under the hover, rest or disabled tint, then its edge.
     *
     * @param graphics the GUI graphics extractor
     * @param frame    what the frame draws from
     * @param face     the petal's fluid and edge sources
     * @param petal    the petal's place on the ring
     * @param overlay  the ARGB tint telling hovered, resting and disabled apart
     */
    static void paint(GuiGraphicsExtractor graphics, RadialWheelRenderer.Frame frame, PetalLook.FluidFace face,
                      RadialWheel.PetalArc petal, int overlay) {
        PetalMask.Petal shape = petal.shape();
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.peekScissorStack();
        List<PetalRenderState.ScreenVertex> fill = toScreen(frame, PetalMesh.fill(shape), face.sprite());
        graphics.submitGuiElementRenderState(new PetalRenderState(RenderPipelines.GUI_TEXTURED, face.texture(), pose,
                fill, ARGB.multiply(face.tint(), overlay), scissor, bounds(fill, pose, scissor)));
        List<PetalRenderState.ScreenVertex> edge = toScreen(frame,
                PetalMesh.edge(shape, PetalMesh.EDGE_THICKNESS, !petal.isAbility()), face.sprite());
        graphics.submitGuiElementRenderState(new PetalRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), pose,
                edge, face.edgeColor(), scissor, bounds(edge, pose, scissor)));
    }

    /**
     * Submits a square sprite cut at a petal's border, so only the part of
     * it lying under the petal's face shows; nothing when none of it does.
     * decision icons-slide-in-from-behind-the-tip
     *
     * @param graphics the GUI graphics extractor
     * @param frame    what the frame draws from
     * @param petal    the petal whose face the sprite shows under
     * @param texture  the sprite's whole texture
     * @param center   the sprite's center on screen
     * @param size     the sprite's side in pixels
     * @param color    the ARGB tint the sprite draws under
     */
    static void paintSprite(GuiGraphicsExtractor graphics, RadialWheelRenderer.Frame frame, PetalMask.Petal petal,
                            TextureSetup texture, int[] center, int size, int color) {
        double radius = frame.radius();
        List<PetalMesh.Quad> quads = PetalMesh.clipSquare(petal, (center[0] - frame.centerX()) / radius,
                (center[1] - frame.centerY()) / radius, size * HALF / radius);
        if (quads.isEmpty()) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.peekScissorStack();
        List<PetalRenderState.ScreenVertex> vertices = toScreen(frame, quads, WHOLE_TEXTURE);
        graphics.submitGuiElementRenderState(new PetalRenderState(RenderPipelines.GUI_TEXTURED, texture, pose,
                vertices, color, scissor, bounds(vertices, pose, scissor)));
    }

    private static List<PetalRenderState.ScreenVertex> toScreen(RadialWheelRenderer.Frame frame,
                                                                List<PetalMesh.Quad> quads,
                                                                PetalLook.SpriteBox sprite) {
        List<PetalRenderState.ScreenVertex> vertices = new ArrayList<>(quads.size() * CORNERS_PER_QUAD);
        for (PetalMesh.Quad quad : quads) {
            for (PetalMesh.Vertex corner : quad.corners()) {
                vertices.add(new PetalRenderState.ScreenVertex(
                        (float) (frame.centerX() + corner.x() * frame.radius()),
                        (float) (frame.centerY() + corner.y() * frame.radius()),
                        sprite.u(corner.u()), sprite.v(corner.v())));
            }
        }
        return vertices;
    }

    private static @Nullable ScreenRectangle bounds(List<PetalRenderState.ScreenVertex> vertices, Matrix3x2f pose,
                                                   @Nullable ScreenRectangle scissor) {
        if (vertices.isEmpty()) {
            return null;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (PetalRenderState.ScreenVertex vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
        }
        int left = (int) Math.floor(minX);
        int top = (int) Math.floor(minY);
        ScreenRectangle area = new ScreenRectangle(left, top, (int) Math.ceil(maxX) - left,
                (int) Math.ceil(maxY) - top).transformMaxBounds(pose);
        return scissor != null ? scissor.intersection(area) : area;
    }
}
