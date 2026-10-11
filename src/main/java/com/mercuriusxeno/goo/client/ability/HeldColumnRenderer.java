package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a column ability's held ghost while right click is held: a faint
 * column of the area's width and height standing on the aimed face, outlined
 * in the goo type's highlight by a square at every block of its height and an
 * edge up each corner, fading toward its top, so the player sees where the
 * column will stand before letting go.
 * updraft-blob-stands-a-column-of-wind
 */
public final class HeldColumnRenderer {

    /** Alpha of the column's floor outline; each square above it draws fainter. */
    static final int FLOOR_ALPHA = 150;
    /** The faintest a square near the top draws. */
    static final int TOP_ALPHA = 30;
    private static final double BLOCK_CENTER = 0.5;
    private static final double HALF = 0.5;
    /** A square's four corners and its first again, closing it. */
    private static final int[][] CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};

    private HeldColumnRenderer() {
    }

    /**
     * Whether the held column draws: right click arms a press, and the
     * selected ability is an arc throw at the world whose area is a column.
     *
     * @param delivery the selected ability's delivery
     * @param badge    the selected ability's badge
     * @param area     the selected ability's area
     * @param armed    whether right click holds an armed press
     * @return true when the column draws
     */
    public static boolean showsColumn(Delivery delivery, AbilityBadge badge, AbilityArea area, boolean armed) {
        return armed && isWorldArc(delivery, badge) && isColumn(area);
    }

    private static boolean isWorldArc(Delivery delivery, AbilityBadge badge) {
        return delivery.kind() == DeliveryKind.ARC && badge == AbilityBadge.WORLD;
    }

    private static boolean isColumn(AbilityArea area) {
        return area.shape() == AbilityArea.Shape.COLUMN && area.size() > 0 && area.width() > 0;
    }

    /**
     * The middle of the column's floor for a target: the bottom of the cell
     * the throw lands in.
     *
     * @param target the aim target
     * @return the floor's middle, or null when nothing is aimed at
     */
    static @Nullable Vec3 floorOf(TargetResult target) {
        HeldDomeRenderer.DomeAnchor anchor = HeldDomeRenderer.anchorOf(target);
        return anchor == null ? null : anchor.domeCorner().add(BLOCK_CENTER, 0, BLOCK_CENTER);
    }

    /**
     * A square's alpha at a height up the column: brightest at the floor,
     * fading evenly to the faintest at the top.
     *
     * @param rise   the height above the floor, in blocks
     * @param height the column's height
     * @return the alpha
     */
    static int alphaAt(double rise, double height) {
        double share = height <= 0 ? 1 : Math.clamp(rise / height, 0, 1);
        return (int) Math.round(FLOOR_ALPHA + (TOP_ALPHA - FLOOR_ALPHA) * share);
    }

    /**
     * Draws the column's ghost where the target places it.
     *
     * @param poseStack the pose stack, camera relative
     * @param buffers   the buffer source
     * @param camera    the camera's world position
     * @param target    the aim target
     * @param area      the column area
     * @param rgb       the goo type's highlight color
     */
    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera,
                              TargetResult target, AbilityArea area, int rgb) {
        Vec3 floor = floorOf(target);
        if (floor == null) {
            return;
        }
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext lines = new LineContext(poseStack.last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        double height = area.size();
        double half = area.width();
        for (int rise = 0; rise <= Math.floor(height); rise++) {
            lines.emitPolyline(camera, square(floor.add(0, rise, 0), half), ARGB.color(alphaAt(rise, height), rgb),
                    lineWidth);
        }
        for (int corner = 0; corner < CORNERS.length - 1; corner++) {
            Vec3 foot = floor.add(CORNERS[corner][0] * half, 0, CORNERS[corner][1] * half);
            lines.emitPolyline(camera, new Vec3[] {foot, foot.add(0, height, 0)},
                    ARGB.color(alphaAt(height * HALF, height), rgb), lineWidth);
        }
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    /**
     * A closed square about a middle, level.
     *
     * @param middle the square's middle
     * @param half   its half side
     * @return its corners, the first repeated last
     */
    static Vec3[] square(Vec3 middle, double half) {
        Vec3[] points = new Vec3[CORNERS.length];
        for (int corner = 0; corner < CORNERS.length; corner++) {
            points[corner] = middle.add(CORNERS[corner][0] * half, 0, CORNERS[corner][1] * half);
        }
        return points;
    }
}
