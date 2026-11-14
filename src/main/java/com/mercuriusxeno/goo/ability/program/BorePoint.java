package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Bore's point: where the eye line meets the first breakable block within
 * the reach, short of the first solid block outside the tag. The pick breaks
 * at it and the drawn drill narrows to it, so the drill renders from the
 * size of the break.
 * decision bore-breaks-the-block-at-the-point-and-its-surroundings
 * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
 *
 * @param block the aimed block the point lands on
 * @param at    the point on the aimed block's outside where the eye line enters it
 */
public record BorePoint(BlockPos block, Vec3 at) {
}
