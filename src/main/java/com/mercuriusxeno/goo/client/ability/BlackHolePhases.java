package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.MarkerVariables;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.ability.program.PhasedStep;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import java.util.OptionalDouble;

/**
 * Reads the nether black hole's size off the phase cursor its program
 * keeps on the marker: the sphere eases out to full size through
 * {@code expand}, holds through {@code hold} and eases back to nothing
 * through {@code contract}, while the accretion disk sweeps out on a
 * curve of its own, the body pulsing steadily through all three. The phase names are the ones
 * {@code nether_black_hole.json} declares.
 */
public final class BlackHolePhases {

    /** The phase before expand, while nether's inward rush plays and the hole draws only its ramp. */
    private static final String GATHER = "gather";
    private static final String EXPAND = "expand";
    private static final String HOLD = "hold";
    private static final String CONTRACT = "contract";
    /**
     * The disk's expansion at the expand to hold transition.
     */
    private static final float DISK_EXPAND_PEAK = 0.25f;
    /**
     * Minimum visible radius so the hole never collapses to a single pixel.
     */
    static final float HOLE_MIN_RADIUS = 0.25f;
    /**
     * World-space margin added to the implosion radius so the hole's body
     * covers the blast zone.
     */
    private static final float OCCLUSION_MARGIN = 0.75f;
    /** Ticks per cycle of the hole's pulse. */
    static final float HOLE_PULSE_PERIOD = 20f;
    /** How far the pulse swings the hole's radius either side of its phase size. */
    static final float HOLE_PULSE_AMPLITUDE = 0.04f;
    private static final double TWO_PI = 2 * Math.PI;

    private BlackHolePhases() {
    }

    /**
     * Answers whether the marker is a nether marker running a phased program.
     *
     * @param be the chain marker block entity
     * @return true while the black hole should draw
     */
    public static boolean isRunning(ChainMarkerBlockEntity be) {
        return GooTypes.NETHER.equals(be.getGooType()) && be.getPhased().isRunning();
    }

    /**
     * Returns the visible scale of the sphere.
     *
     * @param phase the phase cursor
     * @return the scale in [0, 1]
     */
    public static float visibleScale(PhasedState phase) {
        return switch (phase.name()) {
            case EXPAND -> easeOutCubic(phase.progress());
            case HOLD -> 1f;
            case CONTRACT -> easeOutCubic(1f - phase.progress());
            default -> 0f;
        };
    }

    /**
     * Returns the disk's expansion, which runs ahead of the sphere through
     * hold so the disk sweeps outward on its own.
     *
     * @param phase the phase cursor
     * @return the expansion in [0, 1]
     */
    public static float diskExpansionScale(PhasedState phase) {
        return switch (phase.name()) {
            case EXPAND -> DISK_EXPAND_PEAK * phase.progress();
            case HOLD -> DISK_EXPAND_PEAK + (1f - DISK_EXPAND_PEAK) * phase.progress();
            case CONTRACT -> 1f - phase.progress();
            default -> 0f;
        };
    }

    /**
     * Fills the render state's nether fields from the marker's phase cursor,
     * the one extraction every hole style shares (decision
     * one-disc-mesh-config-lens), or clears {@code netherActive} when no
     * black hole runs or its gather has not reached the ramp.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     * @return true when a black hole with a visible body should mark the lens
     */
    public static boolean populateRenderState(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        OptionalDouble ramp = isRunning(be) ? holeRamp(be.getPhased(), state.partialTick) : OptionalDouble.empty();
        if (ramp.isEmpty()) {
            state.netherActive = false;
            return false;
        }
        PhasedState phase = be.getPhased();
        state.netherActive = true;
        state.holeRamp = (float) ramp.getAsDouble();
        state.visibleScale = visibleScale(phase);
        state.diskExpansionScale = diskExpansionScale(phase);
        state.implodeRadius = SyncedSteps.first(be, PhasedStep.class)
                .map(step -> step.radius().evaluateFloat(new MarkerVariables(be))).orElse(0f);
        state.animationTime = NetherDiscMesh.animationTime(be);
        return state.visibleScale > 0f;
    }

    /**
     * How far the hole's startup ramp has run in a phase. The gather leaves
     * the stage to nether's inward rush while the marker's orb holds
     * (decision elemental-explosion-per-type), until its last ticks, where
     * the hole starts small and fades in to meet expand's first frame
     * (decision dome-fades-in-before-its-start).
     *
     * @param phase       the phase cursor
     * @param partialTick the partial tick
     * @return the ramp's share in [0, 1], 1 past the gather, or empty while the hole draws nothing
     */
    static OptionalDouble holeRamp(PhasedState phase, float partialTick) {
        if (!GATHER.equals(phase.name())) {
            return OptionalDouble.of(1);
        }
        if (phase.duration() <= 0) {
            return OptionalDouble.empty();
        }
        float remaining = Math.max(0f, phase.duration() - phase.ticks() - partialTick);
        if (remaining >= StartupRamp.RAMP_TICKS) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(1f - remaining / StartupRamp.RAMP_TICKS);
    }

    /**
     * Answers the hole's full radius: the implosion radius plus the margin
     * that makes the body cover the whole blast zone.
     *
     * @param state the populated render state
     * @return the full radius in world blocks
     */
    public static float fullRadius(ChainMarkerRenderState state) {
        return state.implodeRadius + OCCLUSION_MARGIN;
    }

    /**
     * Answers the hole body's current radius (a sphere's radius, a cube's
     * half-extent), never collapsing below a minimum once its ramp has run.
     *
     * @param state the populated render state
     * @return the visible radius in world blocks
     */
    public static float visibleRadius(ChainMarkerRenderState state) {
        return rampedBodyRadius(fullRadius(state), state.visibleScale, state.gameTime, state.holeRamp);
    }

    /**
     * The hole body's radius through its startup ramp: easing in from
     * nothing to the body's radius, which is the minimum at expand's first
     * frame (decision dome-fades-in-before-its-start).
     *
     * @param fullRadius   the hole's full radius in world blocks
     * @param visibleScale the phase scale visibleScale answers
     * @param gameTime     the game time including the partial tick
     * @param ramp         the ramp's share in [0, 1], 1 once it has run
     * @return the visible radius in world blocks
     */
    static float rampedBodyRadius(float fullRadius, float visibleScale, float gameTime, float ramp) {
        return StartupRamp.radius(ramp, bodyRadius(fullRadius, visibleScale, gameTime));
    }

    /**
     * The hole body's radius at a phase scale, carrying the nether marker's
     * constant pulse (decision orchestration-animation-per-ability).
     *
     * @param fullRadius   the hole's full radius in world blocks
     * @param visibleScale the phase scale visibleScale answers
     * @param gameTime     the game time including the partial tick
     * @return the visible radius in world blocks
     */
    static float bodyRadius(float fullRadius, float visibleScale, float gameTime) {
        return Math.max(HOLE_MIN_RADIUS, fullRadius * visibleScale * holePulse(gameTime));
    }

    /**
     * The nether marker's steady pulse: a sine about 1 the hole rides
     * through expand, hold and contract alike.
     *
     * @param gameTime the game time including the partial tick
     * @return the pulse factor in [1 - HOLE_PULSE_AMPLITUDE, 1 + HOLE_PULSE_AMPLITUDE]
     */
    static float holePulse(float gameTime) {
        double phase = TWO_PI * gameTime / HOLE_PULSE_PERIOD;
        return 1f + HOLE_PULSE_AMPLITUDE * (float) Math.sin(phase);
    }

    /**
     * Answers the world-space center of the marker's block.
     *
     * @param be the chain marker block entity
     * @return the block center
     */
    public static Vec3 holeCenter(ChainMarkerBlockEntity be) {
        return Vec3.atCenterOf(be.getBlockPos());
    }

    /**
     * Cubic ease-out: {@code 1 - (1 - t)^3}.
     *
     * @param t the linear progress
     * @return the eased progress
     */
    private static float easeOutCubic(float t) {
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }
}
