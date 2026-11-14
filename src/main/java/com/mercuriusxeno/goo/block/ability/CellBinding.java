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
 * and that surface. Weird's slime and Kinetic's slick are bound so far, and a
 * third goo type ships its world goo as a third binding.
 * slime-and-slick-are-one-volume-cell-tech
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 *
 * @param gooType       the goo type the cell holds and drops when broken
 * @param layingAbility the ability laying the goo, whose cost prices a whole block of it
 * @param surface       what the cell does to things on it
 */
public record CellBinding(ResourceKey<GooTypeDefinition> gooType, Identifier layingAbility, CellSurface surface) {

    /** The ability laying Weird's slime, Goop, whose cost prices a whole block of slime. */
    public static final Identifier GOOP_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "weird_goop");
    /** The ability laying Kinetic's slick, Slick, whose cost prices a block of slick and whose step names the ramp. */
    public static final Identifier SLICK_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "kinetic_slick");

    /** Weird's slime: Goop lays it, and it bounces what lands on it. */
    public static final CellBinding SLIME = new CellBinding(GooTypes.WEIRD, GOOP_ABILITY, CellSurface.BOUNCE);
    /** Kinetic's slick: Slick lays it, and it ramps a thing trying to move on it. */
    public static final CellBinding SLICK = new CellBinding(GooTypes.KINETIC, SLICK_ABILITY, CellSurface.RAMP);
}
