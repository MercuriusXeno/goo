package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * A box drawn as quads wound to show their inner faces, so a camera inside
 * it, as the glove's own camera always is, still sees each pixel once, for a
 * fragment shader that marches a volume inside it.
 * flames-pixelated-fire-stream
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 */
final class InwardBox {

    /** The quads a box draws. */
    static final int FACES = 6;
    /** The corners each quad holds. */
    static final int CORNERS = 4;
    /** The indices each quad draws with. */
    static final int INDICES_PER_QUAD = 6;
    private static final int X_BIT = 1;
    private static final int Y_BIT = 2;
    private static final int Z_BIT = 4;
    /** Each face's corners, by the bits of their coordinates, wound to face inward. */
    private static final int[][] INWARD_FACES = {
        {2, 6, 4, 0}, {5, 7, 3, 1}, {4, 5, 1, 0}, {3, 7, 6, 2}, {1, 3, 2, 0}, {6, 7, 5, 4},
    };

    private InwardBox() {
    }

    /**
     * Emits a box's six faces, wound inward, in plain white.
     *
     * @param builder the position and color builder the quads go into
     * @param low     the box's low corner
     * @param high    the box's high corner
     */
    static void emit(BufferBuilder builder, Vec3 low, Vec3 high) {
        for (int[] face : INWARD_FACES) {
            for (int corner : face) {
                builder.addVertex((float) ((corner & X_BIT) == 0 ? low.x : high.x),
                        (float) ((corner & Y_BIT) == 0 ? low.y : high.y),
                        (float) ((corner & Z_BIT) == 0 ? low.z : high.z)).setColor(GooRenderUtil.OPAQUE_WHITE);
            }
        }
    }
}
