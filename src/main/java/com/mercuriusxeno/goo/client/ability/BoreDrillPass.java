package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Draws Bore's vortex through a {@link MarchedBoxPass}, as Drain's vortex
 * draws: the box around the vortex, inside which {@code bore_drill.fsh}
 * marches the vortex's churning earth dust, sampled smoothly along each ray.
 * It reads a copy of the world's depth, so the blocks the vortex meets clip
 * it.
 * decision bore-shows-a-big-drill-from-the-glove
 */
final class BoreDrillPass {

    /** The uniform block's bytes: four std140 vec4 rows; must match {@code BoreDrill} in bore_drill.fsh. */
    static final int BYTES = 64;
    /** Blocks the box stands past the vortex, so no fragment of its ragged rim is clipped at a face. */
    private static final double BOX_MARGIN = 0.25;
    private static final MarchedBoxPass PASS = new MarchedBoxPass("Goo bore drill", GooRenderTypes.BORE_DRILL,
            GooRenderTypes.BORE_DRILL_BLOCK, GooRenderTypes.BORE_DRILL_DEPTH_SAMPLER, BYTES);

    /** The block's bytes, kept across frames so a frame allocates no direct memory of its own. */
    private static final ByteBuffer BLOCK = ByteBuffer.allocateDirect(BYTES);

    private BoreDrillPass() {
    }

    /**
     * Draws the vortex.
     *
     * @param bit       the vortex, camera relative
     * @param presence  how much of it shows, 0 to 1, as it builds up and disperses
     * @param modelView the level's model view matrix this frame
     */
    static void draw(ConeSections.Volume bit, double presence, Matrix4fc modelView) {
        if (bit.range() <= 0) {
            return;
        }
        BLOCK.clear();
        pack(BLOCK, bit, presence, MarchedBoxPass.depthZeroToOne());
        PASS.draw(boxAround(bit), BLOCK.flip(), modelView);
    }

    /**
     * The box holding the whole vortex: its near and far ends, standing out
     * by its widest radius and the margin.
     *
     * @param bit the vortex
     * @return the box
     */
    static AABB boxAround(ConeSections.Volume bit) {
        double radius = Math.max(bit.radiusAt().applyAsDouble(0), bit.radiusAt().applyAsDouble(bit.range()));
        Vec3 tip = bit.apex().add(bit.axis().scale(bit.range()));
        return new AABB(bit.apex(), bit.apex()).minmax(new AABB(tip, tip)).inflate(radius + BOX_MARGIN);
    }

    /**
     * Packs the vortex's uniform block, four std140 vec4 rows.
     *
     * @param out       where the bytes go, at its position
     * @param bit       the vortex, camera relative
     * @param presence  how much of it shows, 0 to 1
     * @param zeroToOne whether the device's depth runs 0 to 1
     */
    private static void pack(ByteBuffer out, ConeSections.Volume bit, double presence, boolean zeroToOne) {
        ByteBuffer rows = out.order(ByteOrder.nativeOrder());
        MarchedBoxPass.row(rows, bit.apex(), bit.radiusAt().applyAsDouble(0));
        MarchedBoxPass.row(rows, bit.axis(), bit.range());
        MarchedBoxPass.row(rows, FireField.sideOf(bit.axis()), zeroToOne ? 1 : 0);
        MarchedBoxPass.row(rows, new Vec3(presence, 0, 0), bit.radiusAt().applyAsDouble(bit.range()));
    }
}
