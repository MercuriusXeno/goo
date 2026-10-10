package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.PhasedStep;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Nether goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): an inward rush, an implosion rather than a
 * blast, given its own stage. The black hole's program opens with a 20-tick
 * gather in which the marker holds and the hole draws nothing; over it, a
 * sphere of dark red streaks starts at the hole's implode radius and rushes
 * inward onto the marker, slow and then fast, and the hole opens as the
 * rush arrives, so the two read as cause and effect. Its fragment shader
 * ({@code nether_explosion.fsh}) breaks the shell into streaking specks that
 * brighten from 8B0000 through B32828 to white-hot as they near the center,
 * so matter reads as falling into the hole. Additive, fading in over its
 * first ticks rather than popping in, and fading out over its last ticks as
 * it reaches the center. The vertex color carries progress in red, how near
 * the rush has come in green and its strength in blue, since a core
 * pipeline takes no per-draw uniforms.
 */
public final class NetherExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final NetherExplosionVisual INSTANCE = new NetherExplosionVisual();

    /** Ticks the explosion plays: the black hole's gather phase. */
    static final int DURATION_TICKS = 20;
    /** The implode radius drawn when the ability's phased step cannot be read. */
    static final float FALLBACK_REACH = 3f;
    /** The share of the explosion over which the rush fades in: its first 5 ticks. */
    static final float FADE_IN_END = 0.25f;
    /** The share of the explosion after which the rush fades as it reaches the center. */
    static final float FADE_START = 0.7f;
    private static final int OPAQUE = 0xFF;

    private NetherExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.NETHER;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float start = implodeReach(burnout);
        float radius = rushRadius(progress, start);
        int color = ARGB.color(OPAQUE, NetherDiscMesh.toByte(progress),
                NetherDiscMesh.toByte(1f - radius / start), NetherDiscMesh.toByte(strength(progress)));
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.NETHER_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitSphere(pose, c, radius, color));
    }

    /**
     * The rush's radius: from the implode radius down onto the marker, slow
     * and then fast, as matter falling in.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param start    the implode radius the rush starts from, in blocks
     * @return the shell's radius in blocks
     */
    static float rushRadius(float progress, float start) {
        return start * (1f - progress * progress);
    }

    /**
     * The black hole's implode radius at the burnout's stack count, read off
     * the synced ability's phased step.
     *
     * @param burnout the burnout
     * @return the implode radius in blocks
     */
    private static float implodeReach(ChainBurnouts.Burnout burnout) {
        // black-hole-leaves-a-compression-sphere: the rush starts from the radius the cast was dragged to
        ClientLevel level = Minecraft.getInstance().level;
        double size = level != null && level.getBlockEntity(burnout.pos()) instanceof AbilityBlockEntity be
                ? be.programState().castSize() : 0;
        return SyncedSteps.first(burnout.abilityId(), PhasedStep.class)
                .map(step -> step.radius().evaluateFloat(HostVariables.sized(size)))
                .filter(reach -> reach > 0)
                .orElse(FALLBACK_REACH);
    }

    /**
     * The rush's strength: fading in from nothing over FADE_IN_END, whole
     * until FADE_START, then fading to nothing as it reaches the center.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the rush's strength in [0, 1]
     */
    static float strength(float progress) {
        float fadeIn = Math.min(1f, progress / FADE_IN_END);
        return fadeIn * fadeIn * BurnoutGeometry.fadeAfter(progress, FADE_START);
    }
}
