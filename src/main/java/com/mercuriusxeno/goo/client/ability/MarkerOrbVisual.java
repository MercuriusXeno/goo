package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.FieldStrike;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ber.AbilityBlockRenderState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Slime-like glowing orb a ability block draws while its program runs
 * (decision splat-runs-the-program-no-fuse). Two layers: inner core with
 * the goo fluid texture, outer translucent shell with goo-tinted color.
 * Both are emissive (fullbright). GLOW orbs match the crystal
 * voxel shape from placement. Every layer is the outward half of its
 * box alone, from the face plane into the marker's own block, so nothing of
 * the orb reaches into the block it rests on (decision goo-sits-on-the-face).
 */
public final class MarkerOrbVisual {

    /** Inner core half-size in block units (2 pixels). */
    static final float CORE_BASE = 2f / 16f;
    /** Shell extends 1 pixel beyond core in each direction. */
    static final float SHELL_MARGIN = 1f / 16f;

    /** Outer shell alpha (translucent). */
    private static final int SHELL_ALPHA = 0x60;
    /** Outer shell alpha when the player is aiming at the node. */
    private static final int SHELL_ALPHA_TARGETED = 0xC0;
    /** Shell alpha a lurker reaches at full glow. */
    private static final int OPAQUE_ALPHA = 0xFF;
    /** How far a lurker's orb swells at full glow. */
    static final float LURKER_SWELL = 0.35f;

    /** Ticks per cycle of the crystal marker's ebb, four seconds. */
    static final float CRYSTAL_EBB_PERIOD = 80f;
    /** How far the crystal ebb swings the orb either side of resting size. */
    static final float CRYSTAL_EBB_AMPLITUDE = 0.03f;
    private static final double TWO_PI = 2 * Math.PI;

    /** Center offset in block units. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Divisor for converting crystal extent to half-size in block units. */
    private static final float CRYSTAL_HALF_DIVISOR = 2f;

    /** The silhouette an orb takes, which picks its scale and its depth off the face. */
    enum OrbShape {
        /** A cube scaled evenly by the orb modifier. */
        GOO,
        /** A glow crystal's bump, its depth the crystal model's. */
        GLOW_BUMP;

        /**
         * Picks the shape a marker's goo type draws.
         *
         * @param state the ability block render state
         * @return the orb shape
         */
        static OrbShape of(AbilityBlockRenderState state) {
            return state.gooType == GooTypes.GLOW ? GLOW_BUMP : GOO;
        }
    }

    private MarkerOrbVisual() {
    }

    /**
     * Renders the orb while the marker's program runs: a textured core layer
     * wrapped in a goo-tinted shell.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    public static void submit(AbilityBlockRenderState state, PoseStack poseStack,
                              SubmitNodeCollector nodeCollector) {
        if (!state.behaviorActive) {
            return;
        }
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
     * The half-size a non-glow orb's shell reaches, for what sits beside
     * the orb to clear it.
     *
     * @return the shell half-size in block units
     */
    public static float shellHalf() {
        return CORE_BASE + SHELL_MARGIN;
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
        if (shape == OrbShape.GOO) {
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
        CuboidBounds bounds = outwardHalfBounds(half, face, orbDepth(shape, half));
        Direction intoTheBlock = face.getOpposite();
        for (Direction side : Direction.values()) {
            // The side pressed to the block lies on the block's own face, where it would z-fight and is never seen.
            if (side != intoTheBlock) {
                ctx.emitFace(color, bounds, uv, side);
            }
        }
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
            case GOO -> half;
        };
    }

    /**
     * Glow orbs have no shell margin; all others add one.
     *
     * @param state    the ability block render state
     * @param coreHalf the inner core half-size in block units
     * @return the shell half-size in block units
     */
    private static float computeShellHalf(AbilityBlockRenderState state, float coreHalf) {
        return state.gooType == GooTypes.GLOW ? coreHalf : coreHalf + SHELL_MARGIN;
    }

    /**
     * Combines each ability's own rhythm into one scale factor.
     *
     * @param state the ability block render state
     * @return the combined scale modifier
     */
    private static float computeOrbModifier(AbilityBlockRenderState state) {
        float spikeShake = computeSpikeShake(state);
        float ebb = crystalEbb(state.crystalActive, state.gameTime);
        return spikeShake * ebb * lurkerSwell(state.lurkerGlow);
    }

    /**
     * The swell a watching marker's orb takes on its glow, so the orb beats
     * as it brightens (decision lurker-blob-brightens-then-detonates).
     *
     * @param glow the lurker glow, 0 at rest
     * @return the swell factor, exactly 1 at rest
     */
    static float lurkerSwell(float glow) {
        return 1f + LURKER_SWELL * glow;
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
     * Computes the core half-size. For GLOW type, the crystal's lateral
     * extent.
     *
     * @param state the ability block render state
     * @return the core half-size in block units
     */
    private static float computeCoreHalf(AbilityBlockRenderState state) {
        if (state.gooType == GooTypes.GLOW) {
            return computeGlowCoreHalf();
        }
        return CORE_BASE;
    }

    /**
     * Returns the crystal's lateral half-extent so the glow orb matches
     * the crystal voxel shape from the moment it lands.
     *
     * @return the crystal half-size in block units
     */
    static float computeGlowCoreHalf() {
        GlowCrystalBlock.CrystalSize cs = GlowCrystalBlock.CrystalSize.TINY;
        return (float) ((cs.max - cs.min) / CRYSTAL_HALF_DIVISOR);
    }

    /**
     * Packs shell alpha and goo tint into an ARGB color.
     *
     * @param state the ability block render state
     * @return the packed ARGB shell color
     */
    private static int computeShellColor(AbilityBlockRenderState state) {
        int baseShellAlpha = lurkerAlpha(state.targeted ? SHELL_ALPHA_TARGETED : SHELL_ALPHA, state.lurkerGlow);
        int rgb = state.gooType == GooTypes.GLOW
                ? GooRenderUtil.OPAQUE_WHITE : ClientGooTypes.color(state.gooType);
        return ARGB.color(baseShellAlpha, rgb);
    }

    /**
     * Brightens the shell toward opaque on a watching marker's glow
     * (decision lurker-blob-brightens-then-detonates).
     *
     * @param alpha the shell alpha at rest
     * @param glow  the lurker glow, 0 at rest
     * @return the shell alpha
     */
    static int lurkerAlpha(int alpha, float glow) {
        return alpha + Math.round((OPAQUE_ALPHA - alpha) * glow);
    }

    /**
     * The strongest windup shake across the spikes in flight, so the orb
     * shakes each time it is about to stab.
     *
     * @param state the ability block render state
     * @return the shake scale, 1 while no spike winds up
     */
    private static float computeSpikeShake(AbilityBlockRenderState state) {
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
}
