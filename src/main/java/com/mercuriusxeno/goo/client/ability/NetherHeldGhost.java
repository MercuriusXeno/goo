package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.PhasedStep;
import com.mercuriusxeno.goo.ability.program.PullStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import java.util.List;
import java.util.Optional;

/**
 * The black hole's held ghost: a translucent dark body at the radius it phases
 * with its corona, the landing's own sphere and halo, and rings born out at the
 * radius it pulls from closing on the aim point.
 * black-hole-rings-pulse-inward-to-the-pull-radius
 * held-visual-ghosts-the-landing-in-two-passes
 */
public final class NetherHeldGhost implements HeldGhostVisual {

    /** The one instance the held dome renderer holds. */
    public static final NetherHeldGhost INSTANCE = new NetherHeldGhost();

    private NetherHeldGhost() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.NETHER;
    }

    /**
     * The dome at the program's phased radius and inward rings from its pull
     * radius, each falling back to the area's size where the program holds no such step.
     *
     * @param area      the ability's synced area
     * @param behaviors the ability's synced program
     * @return the ghost
     */
    @Override
    public HeldGhost ghost(AbilityArea area, List<Step> behaviors) {
        float fallback = (float) area.size();
        float dome = literalRadius(SyncedSteps.first(behaviors, PhasedStep.class).map(PhasedStep::radius), fallback);
        float pull = literalRadius(SyncedSteps.first(behaviors, PullStep.class).map(PullStep::radius), fallback);
        return new HeldGhost(dome, HeldGhost.RingDirection.INWARD, pull);
    }

    /**
     * A sized black hole's ghost, its rings closing in from the reach it pulls from.
     * black-hole-rings-pulse-inward-to-the-pull-radius
     *
     * @param radius the radius the drag sets, in blocks
     * @return the ghost
     */
    @Override
    public HeldGhost sizedGhost(float radius) {
        return HeldDomeRenderer.sizedGhost(radius);
    }

    @Override
    public List<HeldLayer> heldLayers() {
        return List.of(
                new HeldLayer(GooRenderTypes.NETHER_BLACKHOLE_HELD_TYPE,
                        GooRenderTypes.NETHER_BLACKHOLE_THROUGH_BLOCKS_TYPE, NetherHeldGhost::emitBody),
                new HeldLayer(GooRenderTypes.NETHER_CORONA_TYPE,
                        GooRenderTypes.NETHER_CORONA_THROUGH_BLOCKS_TYPE, NetherHeldGhost::emitCorona));
    }

    private static float literalRadius(Optional<Expr> radius, float fallback) {
        return radius.filter(expr -> expr.variables().isEmpty()).map(expr -> expr.evaluateFloat(Variables.NONE))
                .filter(value -> value > 0).orElse(fallback);
    }

    private static void emitBody(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face,
                                 float opacity, double nowSeconds) {
        NetherSphereVisual.emitHeldBody(pose, c, ghost.domeRadius(), opacity);
    }

    private static void emitCorona(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face,
                                   float opacity, double nowSeconds) {
        NetherSphereVisual.emitHeldCorona(pose, c, ghost.domeRadius(), opacity);
    }
}
