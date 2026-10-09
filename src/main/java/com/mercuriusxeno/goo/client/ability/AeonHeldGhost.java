package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
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
 * Aeon's held ghost: the chronosphere's golden veil at its full radius about
 * the aim point, so the player sees what the veil will hold before throwing.
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
        return List.of(new HeldLayer(GooRenderTypes.SPORE_SHELL_TYPE, GooRenderTypes.SPORE_SHELL_THROUGH_BLOCKS_TYPE,
                AeonHeldGhost::emitVeil));
    }

    private static void emitVeil(PoseStack.Pose pose, VertexConsumer consumer, HeldGhost ghost, Direction face,
                                 float opacity, double nowSeconds) {
        int alpha = Math.round(ARGB.alpha(ChronosphereVisual.VEIL_COLOR) * opacity);
        ChronosphereVisual.emitSphere(new FlatQuadContext(pose, consumer), ghost.domeRadius(),
                ARGB.color(alpha, ChronosphereVisual.VEIL_COLOR));
    }
}
