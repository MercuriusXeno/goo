package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;

/**
 * Render state snapshot for the chain marker BER. Captures goo type,
 * stack count, and fuse progress for the slime-like orb visual, plus
 * a flag + sphere fields populated when a nether {@code ProgramBehavior}
 * is active so the BER can submit the black-hole shader sphere.
 */
public class ChainMarkerRenderState extends BlockEntityRenderState {

    /** The goo type determining color and fluid texture. */
    public ResourceKey<GooTypeDefinition> gooType = GooTypes.ROCK;

    /** Current stack count (1-based). */
    public int stackCount = 1;

    /** Stack ceiling the marker's ability sets. */
    public int maxStacks = 1;

    /** Fuse remaining in ticks (for pulsing/implosion animation). */
    public int fuseRemaining;

    /** The id of the ability the marker runs. */
    public String abilityId = "";

    /** How far the burnout dome's fuse-tail ramp has run, empty while the marker draws none. */
    public java.util.OptionalDouble domeRamp = java.util.OptionalDouble.empty();

    /** Partial tick for smooth interpolation. */
    public float partialTick;

    /** True when the player's crosshair is on this block. */
    public boolean targeted;

    /** Game tick when the last stack was added (for pulse animation). */
    public long lastStackTick;

    /** Current game time including partial tick, for pulse calculation. */
    public float gameTime;

    /** The face this marker was placed on (for directional rendering). */
    public Direction placedFace = Direction.UP;

    /** True once the marker's fuse has burned out and its program runs. */
    public boolean behaviorActive;

    /** Ticks since this client first drew the behavior, partial tick included; the orb eases back to size on it. */
    public float behaviorAge;

    /** Metal spikes in flight, read from the marker's field-effect state. */
    public java.util.List<com.mercuriusxeno.goo.ability.program.FieldStrike> spikeAnims = java.util.List.of();

    /** The spike age at which a spike reaches full extension and lands. */
    public int spikeStrikeTick;

    /** How many ticks a spike stays in flight, windup to retracted. */
    public int spikeLength;

    /** True when a crystal shard cloud behavior is active. */
    public boolean crystalActive;

    /** Charge density [0-1] for crystal cloud visual scaling. */
    public float crystalDensity;

    /** Slow cycling phase [0-1] for crystal crack drift animation. */
    public float crystalAnimationTime;

    /** The level's game time at full precision, the clock crystal reflections ease on. */
    public double crystalReflectionClock;

    /** Cloud radius fraction [0-1] for expand/contract animation. */
    public float crystalRadiusFraction;

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
}
