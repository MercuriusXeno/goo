package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.MeteorStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
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
 * Meteo's blast zone, drawn at the aimed target while its charge holds: a
 * shell in Astral's gold at the reach the meteor's explosion cuts, depth
 * tested over blocks and fainter through them, its opacity pulsing slowly on
 * the clock, with the rings the held dome ripples across the aimed face.
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 * decision active-target-indicators-carry-depth-and-animation
 */
public final class MeteoBlastZone implements HeldGhostVisual {

    /** The one instance the held dome's registry holds. */
    public static final MeteoBlastZone INSTANCE = new MeteoBlastZone();

    /** Astral's edge gold, the shell's color. */
    private static final int ZONE_RGB = 0xF0E090;
    /** The shell's alpha at full held opacity and the top of its pulse. */
    private static final float ZONE_ALPHA = 0.55f;
    /** The dimmest the pulse falls to, as a share of the shell's alpha. */
    static final float DIMMEST = 0.35f;
    /** How fast the shell pulses, radians per second: one beat about every two seconds. */
    private static final double PULSE_SPEED = Math.PI;
    private static final double HALF = 0.5;

    private MeteoBlastZone() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.ASTRAL;
    }

    /**
     * The shell and its outward rings at the meteor's blast reach, falling
     * back to the area's size where the program calls no meteor of a fixed power.
     *
     * @param area      the ability's synced area
     * @param behaviors the ability's synced program
     * @return the ghost
     */
    @Override
    public HeldGhost ghost(AbilityArea area, List<Step> behaviors) {
        float reach = SyncedSteps.first(behaviors, MeteorStep.class)
                .filter(step -> step.power().variables().isEmpty())
                .map(step -> (float) step.reachAt(Variables.NONE)).orElse((float) area.size());
        return HeldGhost.outwardTo(reach);
    }

    @Override
    public List<HeldLayer> heldLayers() {
        return List.of(new HeldLayer(GooRenderTypes.SPORE_SHELL_TYPE, GooRenderTypes.SPORE_SHELL_THROUGH_BLOCKS_TYPE,
                MeteoBlastZone::emitShell));
    }

    /**
     * The shell's share of its alpha at a moment: a slow beat between the
     * dimmest and full.
     *
     * @param seconds seconds on the real-time clock
     * @return the share, from the dimmest to one
     */
    static float pulseAt(double seconds) {
        float wave = (float) (Math.sin(seconds * PULSE_SPEED) * HALF + HALF);
        return DIMMEST + (1f - DIMMEST) * wave;
    }

    private static void emitShell(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face,
                                  float opacity, double nowSeconds) {
        int alpha = ColorBytes.toByte(Math.min(1f, ZONE_ALPHA * opacity * pulseAt(nowSeconds)));
        BurnoutGeometry.emitSphere(pose, c, ghost.domeRadius(), ARGB.color(alpha, ZONE_RGB));
    }
}
