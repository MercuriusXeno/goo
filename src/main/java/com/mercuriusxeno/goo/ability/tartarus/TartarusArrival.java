package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;

/**
 * Where a Tartarus hold carries its caster.
 *
 * @param destination the level the gate leads to and where its search started
 * @param spot        the feet of the safe spot the search answered
 * @param yaw         the way the caster faces arriving, in degrees
 */
public record TartarusArrival(TartarusDestination destination, BlockPos spot, float yaw) {
}
