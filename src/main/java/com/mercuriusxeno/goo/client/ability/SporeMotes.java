package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * One spore mote: two small crossed quads, so it reads from any side, the way
 * shroom's held cloud and Fungal Shift's target column draw their motes.
 * held-visual-ghosts-the-landing-in-two-passes
 */
public final class SporeMotes {

    private SporeMotes() {
    }

    /**
     * Emits a mote centered at a point.
     *
     * @param pose  the pose entry
     * @param c     the vertex consumer
     * @param x     the mote's x
     * @param y     the mote's y
     * @param z     the mote's z
     * @param half  the mote's half width
     * @param color the mote's ARGB color
     */
    public static void emit(PoseStack.Pose pose, VertexConsumer c, float x, float y, float z, float half,
                            int color) {
        c.addVertex(pose, x - half, y - half, z).setColor(color);
        c.addVertex(pose, x + half, y - half, z).setColor(color);
        c.addVertex(pose, x + half, y + half, z).setColor(color);
        c.addVertex(pose, x - half, y + half, z).setColor(color);
        c.addVertex(pose, x, y - half, z - half).setColor(color);
        c.addVertex(pose, x, y - half, z + half).setColor(color);
        c.addVertex(pose, x, y + half, z + half).setColor(color);
        c.addVertex(pose, x, y + half, z - half).setColor(color);
    }
}
