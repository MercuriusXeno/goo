package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.ber.BeaconBeams;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Bulb's beacon on a prism: the glow column ({@link GlowColumn}) with the light beacon's far-seen column
 * ({@link BeaconBeams}) rising out of it along its facing, sorted at the prism's center so the beam draws after the
 * glow column it rises from.
 * decision bulb-one-model-max-light-beacon-combo
 */
public final class GlowBeaconStyle implements PrismComboStyle {

    /** The id of the ability whose on_prism reaction is the beacon: Bulb. */
    public static final String COMBO = "goo:glow_bulb";

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        GlowColumn.queue(state, poseStack, restingSides());
        BeaconBeams.queue(poseStack, Vec3.atCenterOf(state.blockPos), state.facing, state.animationTime,
                state.beamRadiusScale);
    }

    @Override
    public int beamReach() {
        return BeaconBeams.BEAM_REACH;
    }
}
