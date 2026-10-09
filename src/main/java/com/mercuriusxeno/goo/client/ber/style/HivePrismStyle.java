package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

/**
 * Nether's hive combo: the prism's quartz column, tinted the maroon of the
 * decaying swarm that crystallized into it; the swarm itself shows only as
 * gnats bursting around what the hive eats
 * (decision hive-prism-pillar-eats-the-living).
 */
public final class HivePrismStyle implements PrismComboStyle {

    /** The combo the style draws, nether's hive ability. */
    public static final String COMBO = "goo:nether_hive";
    /** The swarm's maroon, the column's tint. */
    private static final int MAROON = 0x7A1A2A;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look quartz = state.look;
        if (quartz == null) {
            return;
        }
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        CrystalClusterSubmitter.Look hive = new CrystalClusterSubmitter.Look(quartz.uv(),
                ARGB.color(ARGB.alpha(quartz.color()), MAROON));
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS, hive, state.lightCoords);
    }
}
