package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What one goo type's world goo binds the volume cell to: the goo type the
 * cell holds and drops when broken, the ability laying it, whose cost prices
 * a whole block of it and whose program names what the cell's surface does,
 * that surface, and the fluid sources the goo's drips stop at and its cell
 * lies on as on a sturdy top. Weird's slime, Kinetic's slick, Blaze's molten,
 * Crystal's shatter, Deep's host, Frost's rime, Glow's wisps, Hex's leech,
 * Typhoon's updraft, Jelly's nectar, Pulse's signal, Metal's spikes,
 * Nether's infest, Leaf's vines, Unstable's nitro, Quantum's phase, Shroom's
 * spores and Rock's mortar are bound so far, and another goo type ships its
 * world goo as another binding.
 * slime-and-slick-are-one-volume-cell-tech
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 * rime-drips-stop-at-a-water-or-lava-surface
 * glow-world-goo-is-named-wisps-and-takes-the-holds-place
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 * extend-is-dropped-for-signal-an-in-world-pulse-goo
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * spores-is-shrooms-residual-a-passive-mycosis
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 * phase-goo-puts-what-stands-in-it-out-of-phase
 * mortar-is-rocks-residual-a-petrification-and-hardening-goo
 *
 * @param gooType      the goo type the cell holds and drops when broken
 * @param layingAbility the ability laying the goo, whose cost prices a whole block of it
 * @param surface       what the cell does to things on it
 * @param fluidFooting  the fluid sources the drips stop at and the cell lies on as on a sturdy top
 */
public record CellBinding(ResourceKey<GooTypeDefinition> gooType, Identifier layingAbility, CellSurface surface,
                          FluidFooting fluidFooting) {

    /** The ability laying Weird's slime, Goop, whose cost prices a whole block of slime. */
    public static final Identifier GOOP_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "weird_goop");
    /** The ability laying Kinetic's slick, Slick, whose cost prices a block of slick and whose step names the ramp. */
    public static final Identifier SLICK_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "kinetic_slick");
    /** The ability laying Blaze's molten goo, Molten, whose cost prices a whole block of it. */
    public static final Identifier MOLTEN_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "blaze_molten");
    /** The ability laying Crystal's shatter goo, Shatter, whose cost prices a whole block of it. */
    public static final Identifier SHATTER_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_shatter");

    /** The ability laying Deep's sculky slime, Host, whose cost prices a whole block of it. */
    public static final Identifier HOST_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "deep_host");
    /** The ability laying Frost's endothermic goo, Rime, whose cost prices a whole block of it. */
    public static final Identifier RIME_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "frost_rime");
    /** The ability laying Hex's goo, Leech, whose cost prices a whole block of it and whose step names its slow. */
    public static final Identifier LEECH_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "hex_leech");

    /** The ability laying Typhoon's goo, Updraft, whose cost prices a whole block of it and whose step names its column. */
    public static final Identifier UPDRAFT_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "typhoon_updraft");

    /** The ability laying Jelly's goo, Nectar, whose cost prices a whole block of it and whose step names its guide. */
    public static final Identifier NECTAR_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "jelly_nectar");
    /** The ability laying Nether's goo, Infest, whose cost prices a whole block of it. */
    public static final Identifier INFEST_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "nether_infest");
    /** The ability laying Quantum's goo, Phase, whose cost prices a whole block of it and whose step names its linger. */
    public static final Identifier PHASE_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "quantum_phase");

    /** The ability laying Pulse's goo, Signal, whose cost prices a whole block of it. */
    public static final Identifier SIGNAL_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "pulse_signal");

    /** The ability laying Metal's goo, Spikes, whose cost prices a whole block of it. */
    public static final Identifier SPIKES_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "metal_spikes");

    /** The ability laying Leaf's goo, Vines, whose cost prices a whole block of it. */
    public static final Identifier VINES_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "leaf_vines");

    /** The ability laying Shroom's goo, Spores, whose cost prices a whole block of it. */
    public static final Identifier SPORES_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "shroom_spores");
    /** The ability laying Unstable's goo, Nitro, whose cost prices a whole block of it. */
    public static final Identifier NITRO_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_nitro");

    /** The ability laying Rock's goo, Mortar, whose cost prices a whole block of it. */
    public static final Identifier MORTAR_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "rock_mortar");

    /** The ability laying Glow's goo, Wisps, whose cost prices a whole block of it. */
    public static final Identifier WISPS_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "glow_wisps");

    /** Weird's slime: Goop lays it, and it bounces what lands on it. */
    public static final CellBinding SLIME = new CellBinding(GooTypes.WEIRD, GOOP_ABILITY, CellSurface.BOUNCE,
            FluidFooting.NONE);
    /** Kinetic's slick: Slick lays it, and it ramps a thing trying to move on it. */
    public static final CellBinding SLICK = new CellBinding(GooTypes.KINETIC, SLICK_ABILITY, CellSurface.RAMP,
            FluidFooting.NONE);
    /** Blaze's molten goo: Molten lays it, it stands as ordinary ground, and it pools on lava. */
    public static final CellBinding MOLTEN = new CellBinding(GooTypes.BLAZE, MOLTEN_ABILITY, CellSurface.POOL,
            FluidFooting.LAVA);
    /** Crystal's shatter goo: Shatter lays it, and glass shards hang over it. */
    public static final CellBinding SHATTER = new CellBinding(GooTypes.CRYSTAL, SHATTER_ABILITY, CellSurface.SHARDS,
            FluidFooting.NONE);
    /** Deep's sculky slime: Host lays it, and it stands as ordinary ground. */
    public static final CellBinding HOST = new CellBinding(GooTypes.DEEP, HOST_ABILITY, CellSurface.POOL,
            FluidFooting.NONE);
    /**
     * Frost's endothermic goo: Rime lays it, it is as slippery as ice, and it
     * lies on water and lava, freezing what it lands on.
     * rime-goo-is-slippery-to-walk-on
     * rime-drips-stop-at-a-water-or-lava-surface
     */
    public static final CellBinding RIME = new CellBinding(GooTypes.FROST, RIME_ABILITY, CellSurface.GLAZE,
            FluidFooting.WATER_AND_LAVA);
    /** Glow's goo: Wisps lays it, it shines at its layers, and it smites the undead touching it. */
    public static final CellBinding WISPS = new CellBinding(GooTypes.GLOW, WISPS_ABILITY, CellSurface.SMITE,
            FluidFooting.NONE);
    /** Hex's goo: Leech lays it, and it slows what it catches. */
    public static final CellBinding LEECH = new CellBinding(GooTypes.HEX, LEECH_ABILITY, CellSurface.SLOW,
            FluidFooting.NONE);
    /**
     * Typhoon's goo: Updraft lays it, it stands as ordinary ground, and its
     * column lifts what stands above it.
     * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
     */
    public static final CellBinding UPDRAFT = new CellBinding(GooTypes.TYPHOON, UPDRAFT_ABILITY, CellSurface.POOL,
            FluidFooting.NONE);
    /**
     * Jelly's goo: Nectar lays it, and it stands as ordinary ground.
     * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
     */
    public static final CellBinding NECTAR = new CellBinding(GooTypes.JELLY, NECTAR_ABILITY, CellSurface.POOL,
            FluidFooting.NONE);
    /**
     * Pulse's goo: Signal lays it, it stands as ordinary ground, and it sends
     * a redstone signal equal to its layers.
     * extend-is-dropped-for-signal-an-in-world-pulse-goo
     * signal-goo-sends-a-redstone-signal-equal-to-its-layers
     */
    public static final CellBinding SIGNAL = new CellBinding(GooTypes.PULSE, SIGNAL_ABILITY, CellSurface.POOL,
            FluidFooting.NONE);
    /**
     * Metal's goo: Spikes lays it, it stands as ordinary ground, and it
     * impales what walks on it.
     * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
     */
    public static final CellBinding SPIKES = new CellBinding(GooTypes.METAL, SPIKES_ABILITY, CellSurface.IMPALE,
            FluidFooting.NONE);
    /**
     * Nether's goo: Infest lays it, and it stands as ordinary ground.
     * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
     */
    public static final CellBinding INFEST = new CellBinding(GooTypes.NETHER, INFEST_ABILITY, CellSurface.POOL,
            FluidFooting.NONE);
    /**
     * Leaf's goo: Vines lays it, it stands as ordinary ground, and it
     * entangles what crosses it.
     * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
     */
    public static final CellBinding VINES = new CellBinding(GooTypes.LEAF, VINES_ABILITY, CellSurface.ENTANGLE,
            FluidFooting.NONE);
    /**
     * Shroom's goo: Spores lays it, and it stands as ordinary ground.
     * spores-is-shrooms-residual-a-passive-mycosis
     */
    public static final CellBinding SPORES = new CellBinding(GooTypes.SHROOM, SPORES_ABILITY, CellSurface.POOL,
            FluidFooting.NONE);
    /**
     * Unstable's goo: Nitro lays it, and it stands as ordinary ground.
     * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
     */
    public static final CellBinding NITRO = new CellBinding(GooTypes.UNSTABLE, NITRO_ABILITY, CellSurface.POOL,
            FluidFooting.NONE);
    /**
     * Quantum's goo: Phase lays it, it stands as ordinary ground, and it puts
     * what stands in it out of phase.
     * phase-goo-puts-what-stands-in-it-out-of-phase
     */
    public static final CellBinding PHASE = new CellBinding(GooTypes.QUANTUM, PHASE_ABILITY, CellSurface.PHASE,
            FluidFooting.NONE);
    /**
     * Rock's goo: Mortar lays it, and it stands as ordinary ground.
     * mortar-is-rocks-residual-a-petrification-and-hardening-goo
     */
    public static final CellBinding MORTAR = new CellBinding(GooTypes.ROCK, MORTAR_ABILITY, CellSurface.POOL,
            FluidFooting.NONE);
}
