package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import java.util.List;

/**
 * Leaf's held ghost, Bloom's sphere: a soft glowing green-gold haze about the
 * aimed cell as wide as Bloom reaches, a green fog swirling round it and
 * pollen drifting up through it, drawn through {@code leaf_ghost.fsh}. It is
 * a whole sphere, the same whichever face the throw strikes, drawn in the two
 * passes every held ghost takes: bright where it stands in clear sight, faint
 * through the blocks that hide it. Leaf's landing has no burnout, so the
 * ghost is its own sphere.
 * bloom-places-buds-by-biome-and-surface
 */
public final class LeafHeldGhost implements HeldGhostVisual {

    /** The one instance the held dome registry holds. */
    public static final LeafHeldGhost INSTANCE = new LeafHeldGhost();

    private LeafHeldGhost() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.LEAF;
    }

    @Override
    public List<HeldLayer> heldLayers() {
        return List.of(new HeldLayer(GooRenderTypes.LEAF_GHOST_TYPE, GooRenderTypes.LEAF_GHOST_THROUGH_BLOCKS_TYPE,
                LeafHeldGhost::emitHeld));
    }

    /**
     * Emits the haze sphere about the aimed cell.
     *
     * @param pose       the pose entry
     * @param c          the vertex consumer
     * @param ghost      the ghost
     * @param face       the face the throw strikes
     * @param opacity    the share of the dome's opacity
     * @param nowSeconds seconds on the real-time clock
     */
    private static void emitHeld(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face,
                                 float opacity, double nowSeconds) {
        BurnoutGeometry.emitSphere(pose, c, ghost.domeRadius(), sphereColor(NetherDiscMesh.toByte(opacity)));
    }

    /**
     * The sphere's vertex color: the opacity in alpha.
     *
     * @param alpha the opacity as a byte
     * @return the packed ARGB color
     */
    static int sphereColor(int alpha) {
        return ARGB.color(alpha, 0, 0, 0);
    }
}
