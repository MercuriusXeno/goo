package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.client.fade.Afterglow;
import com.mercuriusxeno.goo.client.fade.Afterglows;
import net.minecraft.resources.Identifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The brightness a wheel petal pulses at while the player holds its effect:
 * swinging on game time between the resting and the hovered opacity, twice
 * a period, so a held effect reads on the wheel; an inactive petal rests,
 * and a petal whose effect ended settles to rest over the second after.
 * wheel-pulses-the-active-effect
 * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
 */
final class PetalPulse {

    /** The ticks one pulse cycle spans: two seconds. */
    static final float PERIOD_TICKS = 40f;
    private static final double FULL_TURN = 2 * Math.PI;

    private PetalPulse() {
    }

    /**
     * The opacity a petal draws at.
     *
     * @param pulse    how strongly the petal pulses: one while the player holds its effect, falling to zero
     *                 over the second after it ends, zero for an inactive petal
     * @param gameTime the game time, fraction included
     * @return a pulse between resting and hovered, its swing scaled by the pulse's strength
     */
    static int alpha(float pulse, float gameTime) {
        double swing = Math.abs(Math.sin(FULL_TURN * gameTime / PERIOD_TICKS)) * pulse;
        return RadialWheelRenderer.NORMAL_ALPHA
                + (int) Math.round(swing * (RadialWheelRenderer.HOVER_ALPHA - RadialWheelRenderer.NORMAL_ALPHA));
    }

    /**
     * How strongly each ability's petal pulses this frame: each held ability
     * whole, and each one ended inside the last second fading.
     *
     * @param glows the petals' traces, kept between frames
     * @param held  the abilities whose effect the player holds now
     * @param now   the game time, fraction included
     * @return each pulsing ability's strength, by its id
     */
    static Map<Identifier, Float> pulses(Afterglows<Identifier, Identifier> glows, Collection<Identifier> held,
                                         float now) {
        Map<Identifier, Identifier> standing = new LinkedHashMap<>();
        held.forEach(ability -> standing.put(ability, ability));
        Map<Identifier, Float> pulses = new LinkedHashMap<>();
        for (Afterglow.Glow<Identifier> glow : glows.read(standing, now)) {
            pulses.put(glow.state(), glow.strength());
        }
        return pulses;
    }
}
