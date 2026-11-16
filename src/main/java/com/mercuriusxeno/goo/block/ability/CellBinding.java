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
 * that surface, and whether the goo lies on a lava source's surface as well
 * as on a sturdy top. Weird's slime, Kinetic's slick, Blaze's molten,
 * Crystal's shatter, Deep's host and Glow's wisps are bound so far, and
 * another goo type ships its world goo as another binding.
 * slime-and-slick-are-one-volume-cell-tech
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 * glow-world-goo-is-named-wisps-and-takes-the-holds-place
 *
 * @param gooType       the goo type the cell holds and drops when broken
 * @param layingAbility the ability laying the goo, whose cost prices a whole block of it
 * @param surface       what the cell does to things on it
 * @param liesOnLava    whether the cell lies on a lava source's surface as well as on a sturdy top
 */
public record CellBinding(ResourceKey<GooTypeDefinition> gooType, Identifier layingAbility, CellSurface surface,
                          boolean liesOnLava) {

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

    /** The ability laying Glow's goo, Wisps, whose cost prices a whole block of it. */
    public static final Identifier WISPS_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "glow_wisps");

    /** Weird's slime: Goop lays it, and it bounces what lands on it. */
    public static final CellBinding SLIME = new CellBinding(GooTypes.WEIRD, GOOP_ABILITY, CellSurface.BOUNCE, false);
    /** Kinetic's slick: Slick lays it, and it ramps a thing trying to move on it. */
    public static final CellBinding SLICK = new CellBinding(GooTypes.KINETIC, SLICK_ABILITY, CellSurface.RAMP, false);
    /** Blaze's molten goo: Molten lays it, it stands as ordinary ground, and it pools on lava. */
    public static final CellBinding MOLTEN = new CellBinding(GooTypes.BLAZE, MOLTEN_ABILITY, CellSurface.POOL, true);
    /** Crystal's shatter goo: Shatter lays it, and glass shards hang over it. */
    public static final CellBinding SHATTER = new CellBinding(GooTypes.CRYSTAL, SHATTER_ABILITY, CellSurface.SHARDS,
            false);
    /** Deep's sculky slime: Host lays it, and it stands as ordinary ground. */
    public static final CellBinding HOST = new CellBinding(GooTypes.DEEP, HOST_ABILITY, CellSurface.POOL, false);
    /** Glow's goo: Wisps lays it, it shines at its layers, and it smites the undead touching it. */
    public static final CellBinding WISPS = new CellBinding(GooTypes.GLOW, WISPS_ABILITY, CellSurface.SMITE, false);
}
