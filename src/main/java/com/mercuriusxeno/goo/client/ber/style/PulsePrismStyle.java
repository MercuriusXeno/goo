package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

/**
 * A pulse prism's look: the plain prism's pointed quartz column drawn
 * redstone red. It glows a brighter, self-lit red while it is energized: a
 * metronome while a signal reaches it, a relay while it carries one. A
 * metronome also strobes near white on each beat, fading back over a
 * fraction of a second, so its timing shows.
 * metronome-prism-pulses-at-the-learned-rate
 * relay-prism-carries-the-signal-through-air
 * prism-is-one-pointed-quartz-column
 */
public final class PulsePrismStyle implements PrismComboStyle {

    /** The id of the ability whose program is the metronome combo. */
    public static final String METRONOME_COMBO = "goo:pulse_metronome";
    /** The id of the ability whose program is the relay combo. */
    public static final String RELAY_COMBO = "goo:pulse_relay";
    /** The metronome's look: energized while a signal reaches it, and strobing on each beat. */
    public static final PulsePrismStyle METRONOME = new PulsePrismStyle(true);
    /** The relay's look: energized while it carries a signal. */
    public static final PulsePrismStyle RELAY = new PulsePrismStyle(false);

    /** A dull redstone red the milky crystal is multiplied by at rest. */
    static final int RESTING_RED = 0xFF9A2A20;
    /** The brighter red an energized prism glows. */
    static final int ENERGIZED_RED = 0xFFFF4A30;
    /** The near white a beat strobes to. */
    static final int STROBE_WHITE = 0xFFFFD8CC;
    /** Seconds a beat's strobe takes to fade back. */
    static final double STROBE_SECONDS = 0.3;

    private final boolean strobes;

    private PulsePrismStyle(boolean strobes) {
        this.strobes = strobes;
    }

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look plain = state.look;
        if (plain == null) {
            return;
        }
        boolean energized = energized(state);
        double strobe = strobes ? strobeShare(state.sinceBeat) : 0;
        int tint = tintFor(energized, strobe);
        int light = energized || strobe > 0 ? GooSubmitter.fullbrightLight() : state.lightCoords;
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS,
                new CrystalClusterSubmitter.Look(plain.uv(), ARGB.multiply(plain.color(), tint)), light);
    }

    private boolean energized(PrismRenderState state) {
        return strobes ? state.signalHeard : state.power > 0;
    }

    /**
     * How strongly a beat's strobe shows: full on the beat, fading to none
     * over {@link #STROBE_SECONDS}.
     *
     * @param sinceBeat seconds since the last beat
     * @return the strobe's share, 0 to 1
     */
    static double strobeShare(double sinceBeat) {
        return sinceBeat < 0 || sinceBeat >= STROBE_SECONDS ? 0 : 1 - sinceBeat / STROBE_SECONDS;
    }

    /**
     * The tint the column is multiplied by: resting or energized red, lit
     * toward white by a beat's strobe.
     *
     * @param energized whether the prism is energized
     * @param strobe    the strobe's share, 0 to 1
     * @return the ARGB tint
     */
    static int tintFor(boolean energized, double strobe) {
        int base = energized ? ENERGIZED_RED : RESTING_RED;
        return ARGB.srgbLerp((float) strobe, base, STROBE_WHITE);
    }
}
