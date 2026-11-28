package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Predicate;

/**
 * Whether the caster already sees a wall a front reached, judged once as
 * the front reaches it: a wall the caster sees takes only the ring, and only
 * a wall of a hollow hidden behind blocks holds its light.
 * decision scry-shows-only-hidden-hollows-and-mobs
 * decision scry-visible-blocks-take-only-the-fading-ring
 */
@FunctionalInterface
interface FaceSight {

    /** Sees no wall, so every wall holds its light. */
    FaceSight NONE = face -> false;
    /** How far past a block's side the line from the eye ends, in the cell the side opens onto. */
    double PAST_THE_SIDE = 0.51;

    /**
     * Whether the caster sees a wall.
     *
     * @param face a wall a front reached
     * @return true where the caster sees it
     */
    boolean sees(EchoField.EchoFace face);

    /**
     * The sight from the caster's eye: a wall is in sight where the line from
     * the eye to the open side facing it most meets no block that hides what
     * lies behind it.
     *
     * @param eye         the caster's eye
     * @param blocksSight whether a block hides what lies behind it
     * @return the sight
     */
    static FaceSight from(Vec3 eye, Predicate<BlockPos> blocksSight) {
        return face -> {
            Direction toward = sideFacing(face, eye);
            if (toward == null) {
                return false;
            }
            Vec3 side = face.middle().add(toward.getUnitVec3().scale(PAST_THE_SIDE));
            BlockPos hiding = BlockGetter.traverseBlocks(eye, side, blocksSight,
                    (hides, pos) -> hides.test(pos) ? pos.immutable() : null, hides -> null);
            return hiding == null;
        };
    }

    /**
     * The open side of a wall that faces the eye most.
     *
     * @param face the wall
     * @param eye  the caster's eye
     * @return the side, or null where every open side faces away from the eye
     */
    private static @Nullable Direction sideFacing(EchoField.EchoFace face, Vec3 eye) {
        Vec3 toEye = eye.subtract(face.middle());
        Direction best = null;
        double most = 0;
        for (Direction side : face.sides()) {
            double facing = toEye.dot(side.getUnitVec3());
            if (facing > most) {
                best = side;
                most = facing;
            }
        }
        return best;
    }
}
