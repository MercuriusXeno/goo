package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.FieldStrike;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Slime-like glowing orb shown during the chain marker's FUSE phase.
 * Two layers: inner core with the goo fluid texture, outer translucent
 * shell with goo-tinted color. Both are emissive (fullbright). Size
 * scales with stack count and pulses on each stack add. Implodes inward
 * in the final ticks before detonation. GLOW orbs match the crystal
 * voxel shape from placement; FLAT-shape goo splat against the placed
 * face with axis-asymmetric scaling. Every layer is the outward half of its
 * box alone, from the face plane into the marker's own block, so nothing of
 * the orb reaches into the block it rests on (decision goo-sits-on-the-face).
 */
public final class FuseOrbVisual {

    /** Goo shape constant for flat visual. */
    private static final String SHAPE_FLAT = "flat";

    /** Base inner core half-size in block units (2 pixels) at 1 stack. */
    static final float CORE_BASE = 2f / 16f;
    /** Shell extends 1 pixel beyond core in each direction. */
    static final float SHELL_MARGIN = 1f / 16f;
    /** Core growth per additional stack (1/32 block = 0.5 pixel). */
    static final float CORE_GROWTH = 1f / 32f;

    /** Splat squish factor along placed face axis (half height). */
    static final float SPLAT_HEIGHT = 0.5f;
    /** Splat widen factor perpendicular to placed face (sqrt 2). */
    static final float SPLAT_WIDTH = 1.414f;

    /** Pulse amplitude: 10% size increase on stack add. */
    static final float PULSE_AMPLITUDE = 0.10f;
    /** Pulse duration in ticks. */
    private static final int PULSE_TICKS = 4;

    /** Outer shell alpha (translucent). */
    private static final int SHELL_ALPHA = 0x60;
    /** Outer shell alpha when the player is aiming at the node. */
    private static final int SHELL_ALPHA_TARGETED = 0xC0;

    /** Ticks the eased shrink takes, from resting size to the minimum. */
    static final int SHRINK_TICKS = 12;
    /** Ticks the orb jitters at its minimum before detonation. */
    static final int JITTER_TICKS = 4;
    /** Ticks before detonation where the shrink starts: the shrink, then the jitter. */
    public static final int FUSE_EXPIRY_TICKS = SHRINK_TICKS + JITTER_TICKS;
    /** Minimum scale during implosion (fraction of normal). */
    static final float IMPLOSION_MIN = 0.3f;
    /** How far the jitter swings the scale either side of the minimum. */
    static final float JITTER_AMPLITUDE = IMPLOSION_MIN * 0.2f;
    /** Jitter phase speed in radians per tick, a swing about every tick and a half. */
    private static final float JITTER_RATE = 4.2f;
    /** Ticks per cycle of the crystal marker's ebb, four seconds. */
    static final float CRYSTAL_EBB_PERIOD = 80f;
    /** How far the crystal ebb swings the orb either side of resting size. */
    static final float CRYSTAL_EBB_AMPLITUDE = 0.03f;
    /** Ticks per mining beat, rapid beside the nether pulse. */
    static final float MINING_BEAT_PERIOD = 6f;
    /** How far a mining beat swells the orb past resting size, at its peak. */
    static final float MINING_BEAT_AMPLITUDE = 0.12f;
    private static final double TWO_PI = 2 * Math.PI;
    /** Maps 1 - cos, which spans [0, 2], onto [0, 1]. */
    private static final float COSINE_TO_UNIT = 0.5f;

    /** Center offset in block units. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Divisor for converting crystal extent to half-size in block units. */
    private static final float CRYSTAL_HALF_DIVISOR = 2f;

    /** The silhouette an orb takes, which picks its scale and its depth off the face. */
    enum OrbShape {
        /** A cube scaled evenly by the orb modifier. */
        GOO,
        /** A cube squished along the face axis and widened across it. */
        SPLAT,
        /** A glow crystal's bump, its depth the crystal model's. */
        GLOW_BUMP,
        /** A glow crystal's flat, its depth the crystal model's. */
        GLOW_FLAT;

        /**
         * Picks the shape a marker's goo type and goo shape draw.
         *
         * @param state the chain marker render state
         * @return the orb shape
         */
        static OrbShape of(ChainMarkerRenderState state) {
            boolean flat = SHAPE_FLAT.equals(state.markerShape);
            if (state.gooType == GooTypes.GLOW) {
                return flat ? GLOW_FLAT : GLOW_BUMP;
            }
            return flat ? SPLAT : GOO;
        }
    }

    private FuseOrbVisual() {
    }

    /**
     * Renders the orb: a textured core layer wrapped in a goo-tinted shell.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    public static void submit(ChainMarkerRenderState state, PoseStack poseStack,
                              SubmitNodeCollector nodeCollector) {
        float coreHalf = computeCoreHalf(state);
        float shellHalf = computeShellHalf(state, coreHalf);
        float modifier = computeOrbModifier(state);
        int shellColor = computeShellColor(state);
        GooRenderUtil.UvRect uv = lookupSpriteUv(state.gooType);
        OrbShape shape = OrbShape.of(state);
        Direction face = state.placedFace;

        poseStack.pushPose();
        placeOrb(poseStack, face, shape, modifier);
        GooSubmitter.submitFluid(poseStack, nodeCollector,
                ctx -> emitOrbLayer(ctx, GooRenderUtil.OPAQUE_WHITE, coreHalf, face, shape, uv));
        GooSubmitter.submitFluid(poseStack, nodeCollector,
                ctx -> emitOrbLayer(ctx, shellColor, shellHalf, face, shape, uv));
        poseStack.popPose();
    }

    /**
     * The largest half-size a non-glow orb's shell reaches at the given
     * stack count, its stack-add pulse at peak, for what sits beside the
     * orb to clear it.
     *
     * @param stackCount the marker's stack count
     * @return the shell half-size in block units at peak pulse
     */
    public static float peakShellHalf(int stackCount) {
        return (CORE_BASE + (stackCount - 1) * CORE_GROWTH + SHELL_MARGIN) * (1f + PULSE_AMPLITUDE);
    }

    /**
     * Moves the pose to the placed face plane and scales it about that
     * plane, so no scale moves the orb into the block it rests on. Glow
     * orbs keep the crystal's size and take no scale.
     *
     * @param poseStack the pose stack for rendering
     * @param face      the placed face direction
     * @param shape     the orb shape
     * @param modifier  the combined orb scale modifier
     */
    static void placeOrb(PoseStack poseStack, Direction face, OrbShape shape, float modifier) {
        translateToFace(poseStack, face);
        if (shape == OrbShape.SPLAT) {
            applySplatScale(poseStack, face, modifier);
        } else if (shape == OrbShape.GOO) {
            poseStack.scale(modifier, modifier, modifier);
        }
    }

    /**
     * Emits one orb layer as the outward half of its box: a glow orb stands
     * off the face by the crystal model's depth, every other orb by its
     * half-size.
     *
     * @param ctx   the render context, its pose placed by placeOrb
     * @param color the ARGB tint color
     * @param half  the layer's lateral half-size in block units
     * @param face  the placed face direction
     * @param shape the orb shape
     * @param uv    the UV texture rectangle
     */
    static void emitOrbLayer(RenderContext ctx, int color, float half, Direction face,
                             OrbShape shape, GooRenderUtil.UvRect uv) {
        ctx.emitBox(color, outwardHalfBounds(half, face, orbDepth(shape, half)), uv);
    }

    /**
     * The box one orb layer fills: laterally from -half to +half, and along
     * the face axis from the face plane out to depth in the direction the
     * face steps (decision goo-sits-on-the-face).
     *
     * @param half  the lateral half-size
     * @param face  the placed face direction
     * @param depth the extent off the face plane
     * @return the layer's bounds, relative to the center of the face plane
     */
    static CuboidBounds outwardHalfBounds(float half, Direction face, float depth) {
        float near = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0f : -depth;
        return new CuboidBounds(
                lowOn(Direction.Axis.X, face, half, near), highOn(Direction.Axis.X, face, half, near + depth),
                lowOn(Direction.Axis.Z, face, half, near), highOn(Direction.Axis.Z, face, half, near + depth),
                lowOn(Direction.Axis.Y, face, half, near), highOn(Direction.Axis.Y, face, half, near + depth));
    }

    /**
     * @param axis the axis the bound lies on
     * @param face the placed face direction
     * @param half the lateral half-size
     * @param near the face-axis bound nearest the block the orb rests on
     * @return the box's low bound on the axis
     */
    private static float lowOn(Direction.Axis axis, Direction face, float half, float near) {
        return axis == face.getAxis() ? near : -half;
    }

    /**
     * @param axis the axis the bound lies on
     * @param face the placed face direction
     * @param half the lateral half-size
     * @param far  the face-axis bound farthest from the block the orb rests on
     * @return the box's high bound on the axis
     */
    private static float highOn(Direction.Axis axis, Direction face, float half, float far) {
        return axis == face.getAxis() ? far : half;
    }

    /**
     * How far one orb layer stands off the face plane, before pose scale.
     *
     * @param shape the orb shape
     * @param half  the layer's lateral half-size
     * @return the layer's depth in block units
     */
    private static float orbDepth(OrbShape shape, float half) {
        return switch (shape) {
            case GLOW_BUMP -> (float) GlowCrystalBlock.BUMP_DEPTH;
            case GLOW_FLAT -> (float) GlowCrystalBlock.FLAT_DEPTH;
            case GOO, SPLAT -> half;
        };
    }

    /**
     * Glow orbs have no shell margin; all others add one.
     *
     * @param state    the chain marker render state
     * @param coreHalf the inner core half-size in block units
     * @return the shell half-size in block units
     */
    private static float computeShellHalf(ChainMarkerRenderState state, float coreHalf) {
        return state.gooType == GooTypes.GLOW ? coreHalf : coreHalf + SHELL_MARGIN;
    }

    /**
     * Combines the implosion, the stack pulse and each ability's own rhythm into one scale factor.
     *
     * @param state the chain marker render state
     * @return the combined scale modifier
     */
    private static float computeOrbModifier(ChainMarkerRenderState state) {
        float implosion = state.behaviorActive
                ? computeHandoffScale(state.behaviorAge)
                : computeImplosionScale(state.fuseRemaining, state.partialTick, state.gameTime);
        float pulse = computePulseScale(state);
        float spikeShake = computeSpikeShake(state);
        float ebb = crystalEbb(state.crystalActive, state.gameTime);
        float beat = miningBeat(state.miningActive, state.gameTime, state.lastLayerTick);
        return implosion * pulse * spikeShake * ebb * beat;
    }

    /**
     * The rock, blaze and frost marker's rapid beat while its program breaks
     * blocks, each beat swelling from resting size and back (decision
     * orchestration-animation-per-ability).
     *
     * @param miningActive  true while a progressive-area program runs
     * @param gameTime      the game time including the partial tick
     * @param lastLayerTick the game time the mined layer count last changed
     * @return the beat factor, exactly 1 while no program runs
     */
    static float miningBeat(boolean miningActive, float gameTime, long lastLayerTick) {
        if (!miningActive) {
            return 1f;
        }
        float swell = 1f - (float) Math.cos(TWO_PI * miningBeatPhase(gameTime, lastLayerTick));
        return 1f + MINING_BEAT_AMPLITUDE * swell * COSINE_TO_UNIT;
    }

    /**
     * Where the mining beat stands in its cycle, restarting on each layer strike.
     *
     * @param gameTime      the game time including the partial tick
     * @param lastLayerTick the game time the mined layer count last changed
     * @return the beat's phase in [0, 1), 0 on the strike
     */
    static float miningBeatPhase(float gameTime, long lastLayerTick) {
        float elapsed = Math.max(0f, gameTime - lastLayerTick);
        return (elapsed % MINING_BEAT_PERIOD) / MINING_BEAT_PERIOD;
    }

    /**
     * The crystal marker's slow, faint ebb and flow while its cloud holds
     * the shards aloft (decision orchestration-animation-per-ability).
     *
     * @param crystalActive true while the crystal cloud stands
     * @param gameTime      the game time including the partial tick
     * @return the ebb factor, exactly 1 while no cloud stands
     */
    static float crystalEbb(boolean crystalActive, float gameTime) {
        if (!crystalActive) {
            return 1f;
        }
        double phase = TWO_PI * gameTime / CRYSTAL_EBB_PERIOD;
        return 1f + CRYSTAL_EBB_AMPLITUDE * (float) Math.sin(phase);
    }

    /**
     * Computes the core half-size based on stack count. For GLOW type,
     * smoothly interpolates from the standard goo size down to the
     * crystal's lateral extent over the fuse duration.
     *
     * @param state the chain marker render state
     * @return the core half-size in block units
     */
    private static float computeCoreHalf(ChainMarkerRenderState state) {
        if (state.gooType == GooTypes.GLOW) {
            return computeGlowCoreHalf(state.stackCount);
        }
        return CORE_BASE + (state.stackCount - 1) * CORE_GROWTH;
    }

    /**
     * Returns the crystal's lateral half-extent so the glow orb matches
     * the crystal voxel shape from the moment it lands.
     *
     * @param stackCount the marker's stack count
     * @return the crystal half-size in block units
     */
    static float computeGlowCoreHalf(int stackCount) {
        GlowCrystalBlock.CrystalSize cs =
                GlowCrystalBlock.CrystalSize.fromStacks(stackCount);
        return (float) ((cs.max - cs.min) / CRYSTAL_HALF_DIVISOR);
    }

    /**
     * Brief pulse multiplier that spikes on stack add. Compares game
     * time against the recorded stack tick for partial-tick smoothing.
     *
     * @param state the chain marker render state
     * @return pulse scale factor (1.0 normally, up to 1+PULSE_AMPLITUDE)
     */
    private static float computePulseScale(ChainMarkerRenderState state) {
        if (state.lastStackTick <= 0) {
            return 1f;
        }
        float elapsed = state.gameTime - state.lastStackTick;
        if (elapsed < 0 || elapsed >= PULSE_TICKS) {
            return 1f;
        }
        float t = elapsed / PULSE_TICKS;
        return 1f + PULSE_AMPLITUDE * (float) Math.sin(t * Math.PI);
    }

    /**
     * Packs shell alpha and goo tint into an ARGB color.
     *
     * @param state the chain marker render state
     * @return the packed ARGB shell color
     */
    private static int computeShellColor(ChainMarkerRenderState state) {
        int baseShellAlpha = state.targeted ? SHELL_ALPHA_TARGETED : SHELL_ALPHA;
        int rgb = state.gooType == GooTypes.GLOW
                ? GooRenderUtil.OPAQUE_WHITE : ClientGooTypes.color(state.gooType);
        return ARGB.color(baseShellAlpha, rgb);
    }

    /**
     * The strongest windup shake across the spikes in flight, so the orb
     * shakes each time it is about to stab.
     *
     * @param state the chain marker render state
     * @return the shake scale, 1 while no spike winds up
     */
    private static float computeSpikeShake(ChainMarkerRenderState state) {
        float strongest = 1f;
        for (FieldStrike spike : state.spikeAnims) {
            float s = MetalSpikeVisual.gooShake(spike.age(), state.partialTick, state.spikeStrikeTick);
            if (Math.abs(s - 1f) > Math.abs(strongest - 1f)) {
                strongest = s;
            }
        }
        return strongest;
    }

    /**
     * Looks up the fluid sprite and wraps its UV bounds.
     *
     * @param type the goo type to look up
     * @return the UV rectangle for the fluid sprite
     */
    private static GooRenderUtil.UvRect lookupSpriteUv(ResourceKey<GooTypeDefinition> type) {
        return GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(type));
    }

    /**
     * Translates to the face boundary where the goo splats into the wall.
     *
     * @param poseStack the pose stack for rendering
     * @param face      the placed face direction
     */
    private static void translateToFace(PoseStack poseStack, Direction face) {
        float ox = face.getStepX() * BLOCK_CENTER;
        float oy = face.getStepY() * BLOCK_CENTER;
        float oz = face.getStepZ() * BLOCK_CENTER;
        poseStack.translate(BLOCK_CENTER - ox, BLOCK_CENTER - oy, BLOCK_CENTER - oz);
    }

    /**
     * Splat deformation: squish along the placed face axis, widen
     * perpendicular. Also applies the combined modifier.
     *
     * @param poseStack the pose stack to scale
     * @param face      the placed face direction
     * @param modifier  the combined orb scale modifier
     */
    private static void applySplatScale(PoseStack poseStack, Direction face, float modifier) {
        float wide = SPLAT_WIDTH * modifier;
        float thin = SPLAT_HEIGHT * modifier;
        float sx = face.getAxis() == Direction.Axis.X ? thin : wide;
        float sy = face.getAxis() == Direction.Axis.Y ? thin : wide;
        float sz = face.getAxis() == Direction.Axis.Z ? thin : wide;
        poseStack.scale(sx, sy, sz);
    }

    /**
     * Implosion scale (decision shrink-eases-then-jitters): 1.0 until the
     * last FUSE_EXPIRY_TICKS, then an ease-in-out fall to IMPLOSION_MIN over
     * SHRINK_TICKS, then a jitter about the minimum until detonation. A
     * fuse waiting on a trigger holds at the minimum, still.
     *
     * @param fuseRemaining the fuse ticks remaining
     * @param partialTick   the partial tick for interpolation
     * @param gameTime      the game time including the partial tick, the jitter's clock
     * @return the computed implosion scale
     */
    static float computeImplosionScale(int fuseRemaining, float partialTick, float gameTime) {
        if (fuseRemaining < 0) {
            return IMPLOSION_MIN;
        }
        float smoothFuse = Math.max(0f, fuseRemaining - partialTick);
        if (smoothFuse >= FUSE_EXPIRY_TICKS) {
            return 1f;
        }
        if (smoothFuse >= JITTER_TICKS) {
            float t = (FUSE_EXPIRY_TICKS - smoothFuse) / SHRINK_TICKS;
            return 1f - easeInOut(t) * (1f - IMPLOSION_MIN);
        }
        return IMPLOSION_MIN + JITTER_AMPLITUDE * (float) Math.sin(gameTime * JITTER_RATE);
    }

    /**
     * The scale after the behavior becomes active: the shrink curve run
     * backward, from the jitter's minimum to resting size over SHRINK_TICKS,
     * so the orb never snaps back to size.
     *
     * @param behaviorAge ticks since the client first drew the behavior, partial tick included
     * @return the handoff scale
     */
    static float computeHandoffScale(float behaviorAge) {
        float t = Math.min(1f, Math.max(0f, behaviorAge / SHRINK_TICKS));
        return IMPLOSION_MIN + easeInOut(t) * (1f - IMPLOSION_MIN);
    }

    /**
     * Cosine ease-in-out: slow, then fast, then slow.
     *
     * @param t progress in [0, 1]
     * @return eased progress in [0, 1]
     */
    private static float easeInOut(float t) {
        return (1f - (float) Math.cos(t * Math.PI)) * COSINE_TO_UNIT;
    }
}
