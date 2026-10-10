package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Where a blink puts the blinker and what the trip was, which its cost reads.
 * Decisions blink-lands-safely-costed-by-distance and oculus-prism-becomes-a-hovering-eye.
 *
 * @param feet        where the blinker's feet land
 * @param distance    the blocks from where they stood to where they land
 * @param throughWall whether the trip passed through a solid block
 * @param node        the oculus the blink snapped to, empty for a free or pinned blink
 */
public record BlinkLanding(Vec3 feet, double distance, boolean throughWall, Optional<BlockPos> node) {

    /**
     * A landing snapped to no oculus.
     *
     * @param feet        where the blinker's feet land
     * @param distance    the blocks from where they stood to where they land
     * @param throughWall whether the trip passed through a solid block
     */
    public BlinkLanding(Vec3 feet, double distance, boolean throughWall) {
        this(feet, distance, throughWall, Optional.empty());
    }
}
