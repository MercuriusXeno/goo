package com.mercuriusxeno.goo.block;

import net.minecraft.resources.Identifier;

/**
 * One stack a machine took in for its goo: the item, how many and the goo volume they carry.
 *
 * @param item   the item's registry id
 * @param count  the number of items in the stack
 * @param volume the goo volume the stack carries, in mB
 */
public record ValuedStack(Identifier item, int count, long volume) {
}
