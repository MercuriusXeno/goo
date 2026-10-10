package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * Aeon's held ghost: the chronosphere's own golden veil, drawn through its
 * shader at the radius the drag sets about the pinned center, so the player
 * sees the veil they will open while sizing it.
 * held-visual-ghosts-the-landing-in-two-passes
 * chronosphere-hastes-players-slows-mobs
 */
public final class AeonHeldGhost implements HeldGhostVisual {

    /** The one instance the held dome renderer holds. */
    public static final AeonHeldGhost INSTANCE = new AeonHeldGhost();

    private AeonHeldGhost() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.AEON;
    }

    @Override
    public List<HeldLayer> heldLayers() {
        return List.of(new HeldLayer(GooRenderTypes.CHRONOSPHERE_TYPE,
                GooRenderTypes.CHRONOSPHERE_THROUGH_BLOCKS_TYPE, AeonHeldGhost::emitVeil));
    }

    private static void emitVeil(PoseStack.Pose pose, VertexConsumer consumer, HeldGhost ghost, Direction face,
                                 float opacity, double nowSeconds) {
        ChronosphereVisual.emitVeil(new FlatQuadContext(pose, consumer), ghost.domeRadius(), opacity);
    }
}
