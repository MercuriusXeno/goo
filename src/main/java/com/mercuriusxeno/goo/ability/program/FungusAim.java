package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The fungus block an entity's look names for Fungal Shift: the block its
 * look meets, when that is fungus, or else the fungus block whose center
 * sits nearest the look within a small angle of it, within the reach and in
 * clear sight of the eye, so a mushroom a few pixels wide at range is still
 * aimable (decision fungal-shift-blinks-to-the-aimed-fungus).
 */
public final class FungusAim {

    /** How far off the look, in degrees, a fungus block may sit and still be aimed at. */
    public static final double SNAP_DEGREES = 3;
    /** Spacing of the samples taken across each slice of the snap cone, in blocks. */
    private static final double SAMPLE_SPACING = 0.5;
    /** Slices taken along the look, one per block of reach. */
    private static final double SLICE_STEP = 1;
    /** The cosine's whole span, either way, kept so rounding never steps outside acos's domain. */
    private static final double FULL_COSINE = 1.0;

    private FungusAim() {
    }

    /**
     * The fungus block the entity aims at within the reach.
     *
     * @param level  the level
     * @param entity the aiming entity
     * @param reach  the reach in blocks
     * @return the block, or empty when the look names no fungus
     */
    public static Optional<BlockPos> aimedFungus(Level level, Entity entity, double reach) {
        Vec3 eye = entity.getEyePosition();
        Vec3 look = entity.getLookAngle().normalize();
        BlockHitResult hit = clip(level, entity, eye, eye.add(look.scale(reach)));
        if (hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).is(ShiftStep.FUNGUS)) {
            return Optional.of(hit.getBlockPos().immutable());
        }
        return snapCandidates(eye, look, reach, pos -> level.getBlockState(pos).is(ShiftStep.FUNGUS)).stream()
                .filter(pos -> inClearSight(level, entity, eye, pos))
                .findFirst();
    }

    /**
     * The blocks within the snap cone that pass the test, nearest the look
     * first.
     *
     * @param eye    the eye
     * @param look   the look, unit length
     * @param reach  the reach in blocks
     * @param fungus whether a block is fungus
     * @return the candidates, by angle off the look
     */
    static List<BlockPos> snapCandidates(Vec3 eye, Vec3 look, double reach,
                                         Predicate<BlockPos> fungus) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();
        Vec3 side = StreamCone.side(look);
        Vec3 lift = side.cross(look);
        double slope = Math.tan(Math.toRadians(SNAP_DEGREES));
        for (double along = SLICE_STEP; along <= reach; along += SLICE_STEP) {
            Vec3 middle = eye.add(look.scale(along));
            double radius = along * slope + SAMPLE_SPACING;
            for (double u = -radius; u <= radius; u += SAMPLE_SPACING) {
                for (double v = -radius; v <= radius; v += SAMPLE_SPACING) {
                    BlockPos pos = BlockPos.containing(middle.add(side.scale(u)).add(lift.scale(v)));
                    if (seen.add(pos)) {
                        found.add(pos);
                    }
                }
            }
        }
        found.removeIf(pos -> !withinSnap(eye, look, Vec3.atCenterOf(pos), reach) || !fungus.test(pos));
        found.sort(Comparator.comparingDouble(pos -> degreesOff(eye, look, Vec3.atCenterOf(pos))));
        return found;
    }

    /**
     * Whether a point sits within the reach and the snap angle of the look.
     *
     * @param eye   the eye
     * @param look  the look, unit length
     * @param point the point
     * @param reach the reach in blocks
     * @return true inside the snap cone
     */
    static boolean withinSnap(Vec3 eye, Vec3 look, Vec3 point, double reach) {
        return point.distanceTo(eye) <= reach && degreesOff(eye, look, point) <= SNAP_DEGREES;
    }

    private static double degreesOff(Vec3 eye, Vec3 look, Vec3 point) {
        Vec3 toward = point.subtract(eye).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(toward.dot(look), -FULL_COSINE, FULL_COSINE)));
    }

    private static boolean inClearSight(Level level, Entity entity, Vec3 eye, BlockPos pos) {
        BlockHitResult hit = clip(level, entity, eye, Vec3.atCenterOf(pos));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private static BlockHitResult clip(Level level, Entity entity, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
    }
}
