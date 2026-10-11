package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.LineContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a Spire's preview: while the drag sizes the footprint, the outline of
 * the ground cells it covers; once the footprint is fixed, the outline of
 * the wall or platform the rise would stand on it.
 * decision spire-rips-walls-and-platforms
 */
final class SpireOutlineRenderer {

    private static final int OUTLINE_ALPHA = 220;

    private SpireOutlineRenderer() {
    }

    /**
     * Draws the footprint's outline in the type's highlight color.
     *
     * @param ps        the pose stack
     * @param buf       the buffer source
     * @param camPos    the camera position
     * @param footprint the live footprint
     * @param rising    whether the footprint is fixed and the rise is being set
     * @param rgb       the goo type's highlight color
     */
    static void render(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 camPos, SpireFootprint footprint,
                       boolean rising, int rgb) {
        BlockPos min = footprint.min();
        BlockPos max = footprint.max();
        float bottom = rising ? min.getY() + 1f : min.getY();
        float top = rising ? min.getY() + 1f + footprint.rise() : min.getY() + 1f;
        CuboidBounds bounds = new CuboidBounds(
                (float) (min.getX() - camPos.x), (float) (max.getX() + 1 - camPos.x),
                (float) (min.getZ() - camPos.z), (float) (max.getZ() + 1 - camPos.z),
                (float) (bottom - camPos.y), (float) (top - camPos.y));
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(ps.last(), buf.getBuffer(RenderTypes.lines()));
        ctx.emitWireframe(bounds, ARGB.color(OUTLINE_ALPHA, rgb), lineWidth);
        buf.endLastBatch();
    }
}
