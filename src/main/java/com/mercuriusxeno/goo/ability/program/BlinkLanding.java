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
 * @param converts    the bare prism the blink spends itself into, becoming an oculus, empty for a trip
 */
public record BlinkLanding(Vec3 feet, double distance, boolean throughWall, Optional<BlockPos> node,
                           Optional<BlockPos> converts) {

    /**
     * A landing converting no prism.
     *
     * @param feet        where the blinker's feet land
     * @param distance    the blocks from where they stood to where they land
     * @param throughWall whether the trip passed through a solid block
     * @param node        the oculus the blink snapped to, empty for a free or pinned blink
     */
    public BlinkLanding(Vec3 feet, double distance, boolean throughWall, Optional<BlockPos> node) {
        this(feet, distance, throughWall, node, Optional.empty());
    }

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

    /**
     * A blink spent into a bare prism: the blinker stays where they stand,
     * and the trip priced is the distance to the prism.
     * blink-at-a-prism-spends-into-an-oculus
     *
     * @param feet     where the blinker's feet stand, and stay
     * @param distance the blocks from the blinker to the prism
     * @param prism    the bare prism the blink converts
     * @return the landing
     */
    public static BlinkLanding converting(Vec3 feet, double distance, BlockPos prism) {
        return new BlinkLanding(feet, distance, false, Optional.empty(), Optional.of(prism));
    }
}
