package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.ability.nether.HiveSwarm;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

/**
 * Zombie's hive combo: the prism's quartz column, tinted the meat brown of the
 * decaying swarm that set into it; the swarm around it is
 * {@link HiveSwarm}'s (decision hive-prism-pillar-eats-the-living).
 */
public final class HivePrismStyle implements PrismComboStyle {

    /** The combo the style draws, Decay's hive. */
    public static final String COMBO = HiveSwarm.COMBO;
    /**
     * The column's tint, zombie goo's meat brown edge.
     * zombie-goo-is-colored-after-zombie-flesh-and-decay-stops-being-maroon
     */
    static final int MEAT_BROWN = 0x5E4428;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look quartz = state.look;
        if (quartz == null) {
            return;
        }
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        CrystalClusterSubmitter.Look hive = new CrystalClusterSubmitter.Look(quartz.uv(), columnColor(quartz.color()));
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS, hive, state.lightCoords);
    }

    /**
     * The hive column's color: the quartz's alpha, in the swarm's meat brown.
     *
     * @param quartzColor the plain prism's ARGB
     * @return the column's ARGB
     */
    static int columnColor(int quartzColor) {
        return ARGB.color(ARGB.alpha(quartzColor), MEAT_BROWN);
    }
}
