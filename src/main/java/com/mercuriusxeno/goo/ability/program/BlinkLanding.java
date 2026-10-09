package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.Vec3;

/**
 * Where a blink puts the blinker and what the trip was, which its cost reads.
 * Decision blink-lands-safely-costed-by-distance.
 *
 * @param feet        where the blinker's feet land
 * @param distance    the blocks from where they stood to where they land
 * @param throughWall whether the trip passed through a solid block
 */
public record BlinkLanding(Vec3 feet, double distance, boolean throughWall) {
}
