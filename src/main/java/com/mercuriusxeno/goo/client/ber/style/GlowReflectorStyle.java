package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mercuriusxeno.goo.client.ber.ReflectorBeams;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Glow's Reflector on a prism: the glow column ({@link GlowColumn}) folded into four sides as Relay's is, and the
 * reflector's rails ({@link ReflectorBeams}) to each reflector it links to, sorted at the prism's center so each
 * beam draws after the column.
 * decision reflector-rails-carry-the-brightest-light
 * decision relay-and-metronome-read-apart-at-rest
 */
public final class GlowReflectorStyle implements PrismComboStyle {

    /**
     * The id of the ability whose on_prism reaction makes the reflector.
     * channeled-hosts-convert-prisms-over-the-hold
     */
    public static final String COMBO = "goo:glow_laser";

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        GlowColumn.queue(state, poseStack, restingSides());
        ReflectorBeams.queue(poseStack, Vec3.atCenterOf(state.blockPos), state.links, state.linkLight,
                state.animationTime);
    }

    @Override
    public PrismCrystal.ColumnSides restingSides() {
        return PrismCrystal.ColumnSides.FOUR;
    }
}
