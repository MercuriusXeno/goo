package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.throwing.VortexCone;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Draws Drain's miasma vortex through a {@link MarchedBoxPass}: the box
 * around the cone, inside which {@code miasma_vortex.fsh} marches the cone,
 * stopping at a copy of the world's depth so the miasma lies in front of
 * whatever stands in it.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 */
final class MiasmaVortexPass {

    /** The uniform block's bytes: six std140 vec4 rows; must match {@code MiasmaVortex} in miasma_vortex.fsh. */
    static final int BYTES = 96;
    /** Blocks the box stands past the cone, so no fragment of it is clipped at a face. */
    private static final double BOX_MARGIN = 0.25;
    private static final float CHANNEL = 255f;
    private static final MarchedBoxPass PASS = new MarchedBoxPass("Goo miasma vortex", GooRenderTypes.MIASMA_VORTEX,
            GooRenderTypes.MIASMA_VORTEX_BLOCK, GooRenderTypes.MIASMA_DEPTH_SAMPLER, BYTES);

    /** The block's bytes, kept across frames so a frame allocates no direct memory of its own. */
    private static final ByteBuffer BLOCK = ByteBuffer.allocateDirect(BYTES);

    private MiasmaVortexPass() {
    }

    /**
     * The colors the vortex wears.
     *
     * @param body   the cloud's packed RGB, the goo's wheel color
     * @param streak the streaks' packed RGB, the goo's bright color
     */
    record Colors(int body, int streak) {
    }

    /**
     * Draws the vortex.
     *
     * @param vortex    the vortex, camera relative
     * @param colors    the colors it wears
     * @param shown     how much of it shows
     * @param modelView the level's model view matrix this frame
     */
    static void draw(VortexCone vortex, Colors colors, VortexPresence.Shown shown, Matrix4fc modelView) {
        BLOCK.clear();
        pack(BLOCK, vortex, colors, shown, MarchedBoxPass.depthZeroToOne());
        PASS.draw(vortex.box(BOX_MARGIN), BLOCK.flip(), modelView);
    }

    /**
     * Packs the vortex's uniform block, six std140 vec4 rows.
     *
     * @param out       where the bytes go, at its position
     * @param vortex    the vortex, camera relative
     * @param colors    the colors it wears
     * @param shown     how much of it shows
     * @param zeroToOne whether the device's depth runs 0 to 1
     */
    private static void pack(ByteBuffer out, VortexCone vortex, Colors colors, VortexPresence.Shown shown,
                             boolean zeroToOne) {
        ByteBuffer rows = out.order(ByteOrder.nativeOrder());
        MarchedBoxPass.row(rows, vortex.apex(), vortex.tan());
        MarchedBoxPass.row(rows, vortex.axis(), vortex.length());
        MarchedBoxPass.row(rows, FireField.sideOf(vortex.axis()), zeroToOne ? 1 : 0);
        MarchedBoxPass.row(rows, rgbOf(colors.body()), 0);
        MarchedBoxPass.row(rows, rgbOf(colors.streak()), 0);
        MarchedBoxPass.row(rows, new Vec3(shown.presence(), shown.formed(), 0), 0);
    }

    private static Vec3 rgbOf(int rgb) {
        return new Vec3(ARGB.red(rgb) / CHANNEL, ARGB.green(rgb) / CHANNEL, ARGB.blue(rgb) / CHANNEL);
    }
}
