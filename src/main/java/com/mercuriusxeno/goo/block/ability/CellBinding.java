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
 * as on a sturdy top. Weird's slime, Kinetic's slick, Blaze's molten and
 * Astral's Dowsing are bound so far, and another goo type ships its world goo
 * as another binding.
 * slime-and-slick-are-one-volume-cell-tech
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
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
    /** The ability laying Astral's Dowsing goo, whose cost prices a whole block of it. */
    public static final Identifier DOWSING_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "astral_dowsing");

    /** Weird's slime: Goop lays it, and it bounces what lands on it. */
    public static final CellBinding SLIME = new CellBinding(GooTypes.WEIRD, GOOP_ABILITY, CellSurface.BOUNCE, false);
    /** Kinetic's slick: Slick lays it, and it ramps a thing trying to move on it. */
    public static final CellBinding SLICK = new CellBinding(GooTypes.KINETIC, SLICK_ABILITY, CellSurface.RAMP, false);
    /** Blaze's molten goo: Molten lays it, it stands as ordinary ground, and it pools on lava. */
    public static final CellBinding MOLTEN = new CellBinding(GooTypes.BLAZE, MOLTEN_ABILITY, CellSurface.POOL, true);
    /**
     * Astral's Dowsing goo: Dowsing lays it, ordinary ground to stand on, and it lies on sturdy tops alone.
     * astral-world-goo-is-dowsing-an-x-ray-of-ore
     */
    public static final CellBinding DOWSING = new CellBinding(GooTypes.ASTRAL, DOWSING_ABILITY, CellSurface.LENS,
            false);
}
