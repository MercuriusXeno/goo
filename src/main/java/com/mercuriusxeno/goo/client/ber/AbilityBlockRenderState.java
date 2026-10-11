package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;

/**
 * Render state snapshot for the ability block BER. Captures goo type,
 * stack count and program state for the slime-like orb visual, plus
 * a flag + sphere fields populated when a nether {@code ProgramBehavior}
 * is active so the BER can submit the black-hole shader sphere.
 */
public class AbilityBlockRenderState extends BlockEntityRenderState {

    /** The goo type determining color and fluid texture. */
    public ResourceKey<GooTypeDefinition> gooType = GooTypes.ROCK;


    /** The id of the ability the marker runs. */
    public String abilityId = "";


    /** Partial tick for smooth interpolation. */
    public float partialTick;

    /** True when the player's crosshair is on this block. */
    public boolean targeted;

    /** Current game time including partial tick, for pulse calculation. */
    public float gameTime;

    /** The face this marker was placed on (for directional rendering). */
    public Direction placedFace = Direction.UP;

    /** True while the marker's program runs. */
    public boolean behaviorActive;

    /** Metal spikes in flight, read from the marker's field-effect state. */
    public java.util.List<com.mercuriusxeno.goo.ability.program.FieldStrike> spikeAnims = java.util.List.of();

    /** The spike age at which a spike reaches full extension and lands. */
    public int spikeStrikeTick;

    /** How many ticks a spike stays in flight, windup to retracted. */
    public int spikeLength;

    /** How brightly a watching marker glows as its enemy closes, 0 at rest. */
    public float lurkerGlow;

    /** True when a crystal shard cloud behavior is active. */
    public boolean crystalActive;

    /** The chronosphere veil's radius this frame, zero for a marker standing none. */
    public float chronosphereRadius;

    /** Charge density [0-1] for crystal cloud visual scaling. */
    public float crystalDensity;

    /** Slow cycling phase [0-1] for crystal crack drift animation. */
    public float crystalAnimationTime;

    /** The level's game time at full precision, the clock crystal reflections ease on. */
    public double crystalReflectionClock;

    /** Cloud contract fraction [0-1]: 1 until the cloud contracts, falling to 0 as it does. */
    public float crystalRadiusFraction;

    /** Ticks the cloud's field has run since its blob landed, the clock the prism dome grows on. */
    public int crystalFieldTicks;

    /** Full cloud radius in blocks, read from the field effect. */
    public float crystalRadius;

    /** True when a nether black-hole behavior is active on this marker.
     * The BER uses this flag to branch between the orb visual (false) and
     * the shader sphere (true). */
    public boolean netherActive;

    /** How far the nether hole's startup ramp has run, 1 once it has run (decision dome-fades-in-before-its-start). */
    public float holeRamp = 1f;

    /** Visible scale of the sphere in [0, 1]: grows through EXPAND, 1
     * during HOLD, shrinks through CONTRACT. Only meaningful when
     * {@link #netherActive} is true. */
    public float visibleScale;

    /** Accretion disc expansion scale in [0, 1]. Runs on a separate
     * curve from {@link #visibleScale} so the disc sweeps outward past
     * the sphere instead of inflating in lockstep with it. Only
     * meaningful when {@link #netherActive} is true. */
    public float diskExpansionScale;

    /** Effect radius of the nether blast in blocks. Only meaningful
     * when {@link #netherActive} is true. */
    public float implodeRadius;

    /** Cycling animation phase in [0, 1] used by the shader's swirl. */
    public float animationTime;

    /** True when the marker is a Vines trap waiting on the ground (decision vines-unpack-root-and-thorn). */
    public boolean vinesTrap;

    /** How far the trap's blob has unpacked into its knot and tendrils, 0 to 1. */
    public float vinesUnpacked;
}
