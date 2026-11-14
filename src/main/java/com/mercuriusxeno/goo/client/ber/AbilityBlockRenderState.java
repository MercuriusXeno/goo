package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;

/**
 * Render state snapshot for the ability block BER. Captures goo type,
 * stack count and program state for the slime-like orb visual and the
 * visuals of the abilities that linger.
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

    /**
     * Where each spike in flight aims, block-local, parallel to
     * {@link #spikeAnims}: its mob's interpolated body center while the
     * client holds the mob, the strike's synced point otherwise.
     */
    public java.util.List<org.joml.Vector3f> spikeAims = java.util.List.of();

    /** How far out Urchin's spines stand this frame, as a share of the landing flare's, zero at rest. */
    public float urchinBristle;

    /** The spike age at which a spike reaches full extension and lands. */
    public int spikeStrikeTick;

    /** How many ticks a spike stays in flight, windup to retracted. */
    public int spikeLength;

    /** How brightly a watching marker glows as its enemy closes, 0 at rest. */
    public float lurkerGlow;

    /** True when a crystal shard cloud behavior is active. */
    public boolean crystalActive;

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

    /** True when the marker is a Vines trap waiting on the ground (decision vines-unpack-root-and-thorn). */
    public boolean vinesTrap;

    /** How far the trap's blob has unpacked into its knot and tendrils, 0 to 1. */
    public float vinesUnpacked;
}
