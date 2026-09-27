package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.Identifier;

/**
 * One stack a machine took in for its goo: the item, how many and the goo one of them carries.
 *
 * @param item  the item's registry id
 * @param count the number of items in the stack
 * @param unit  the goo one item of the stack carries
 */
public record ValuedStack(Identifier item, int count, GooContents unit) {

    /**
     * Returns the goo volume the whole stack carries.
     *
     * @return the volume in mB
     */
    public long volume() {
        return unit.totalVolume() * count;
    }
}
