package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What a held Bore breaks this tick: whatever its drill's cross section
 * intersects. The cross section is the ellipse the drill cone cuts in the
 * plane square to the look ({@link BoreDrillShape}), centered on the eye
 * line at the depth a tube of rays sets: the eye line and 8 rays round the
 * ellipse's rim, parallel to the look out to the reach, each stopping at the
 * first block that is not air, the nearest stop setting the depth. The rays
 * pick nothing: aiming at a face's middle picks one block, near an edge two
 * and near a corner four, never every block the drill touches; no block
 * behind them progresses.
 * decision bore-breaks-what-the-drill-cross-section-intersects
 * decision bore-tube-rays-set-the-depth-of-the-cross-section
 * decision bore-breaks-the-block-at-the-point-and-its-surroundings
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
 *
 * @param blocks the breakable blocks the cross section intersects, the one on the eye line first; empty where
 *               no ray meets a block
 * @param tunnel every cell the eye line crosses from the eye out to the cross section, air among them, where
 *               Bore strikes what stands
 */
record BorePick(List<BlockPos> blocks, List<BlockPos> tunnel) {

    /** The rays round the cross section's rim, beside the eye line. */
    static final int RIM_RAYS = 8;
    /** How far past the cross section's plane, along the look, its cell is read. */
    private static final double NUDGE = 1e-4;
    /** A block's box at the origin, which a clip carries to the block it is handed. */
    private static final AABB UNIT_CELL = new AABB(0, 0, 0, 1, 1, 1);
    /** How far in front of the crosshair block's face a rim ray's block must stand to set the depth. */
    private static final double IN_FRONT = 1e-3;

    /**
     * Where the cross section stands: on the eye line at the depth the tube
     * sets, on the face the ray that set it met.
     *
     * @param center  the cross section's center, on the eye line
     * @param face    the unit outward normal of the face the depth's ray met
     * @param stopped whether a ray met a block, so the cross section stands on it; false at the end of reach
     */
    private record Section(Vec3 center, Vec3 face, boolean stopped) {
    }

    /**
     * Where a ray meets a block, and the face it meets.
     *
     * @param at   the point on the block's outside the ray meets
     * @param face the unit outward normal of that face
     */
    private record Hit(Vec3 at, Vec3 face) {
    }

    /**
     * Picks what a bore breaks this tick.
     *
     * @param eye        the eye the line runs from
     * @param shape      the drill cone whose cross section the pick reads
     * @param gloveRight whether the glove is in the right hand, the side the cross section's long axis lies
     * @param aim        the stream's aim: its reach along the look
     * @param air        whether a cell holds air
     * @param breakable  whether a cell holds a block in the tag
     * @return the pick
     */
    static BorePick of(Vec3 eye, BoreDrillShape shape, boolean gloveRight, ChannelAim aim, Predicate<BlockPos> air,
                       Predicate<BlockPos> breakable) {
        Vec3 look = aim.aimPoint().subtract(eye).normalize();
        Section section = sectionOf(eye, aim.aimPoint(), shape, gloveRight, air, breakable);
        List<BlockPos> line = BoreStep.blocksAlong(eye, section.center().add(look.scale(NUDGE)));
        if (!section.stopped()) {
            return new BorePick(List.of(), List.copyOf(line.subList(1, line.size())));
        }
        BlockPos eyeCell = line.getFirst();
        BlockPos outward = BlockPos.containing(section.face());
        // a block whose face toward the crosshair's side is buried, as a tunnel's floor or ceiling is, never breaks
        List<BlockPos> blocks = shape.cellsCrossedAt(section.center(), look, section.face(), gloveRight).stream()
                .filter(cell -> !cell.equals(eyeCell) && breakable.test(cell) && air.test(cell.offset(outward)))
                .toList();
        // the strike reaches the block the eye line enters where Bore breaks it, and stops short of one it cannot
        int tunnelEnd = blocks.contains(line.getLast()) ? line.size() : line.size() - 1;
        return new BorePick(blocks, List.copyOf(line.subList(1, Math.max(1, tunnelEnd))));
    }

    /**
     * Where the cross section stands: where the eye line meets the crosshair's
     * block, unless a rim ray of the tube meets a block standing in front of
     * that block's face, which then sets the depth; or the end of reach where
     * no ray meets a block. A rim ray meeting the same face the crosshair
     * meets, as a slanted floor or wall gives, sets nothing, so the cross
     * section stays on the crosshair.
     *
     * @param eye        the eye the tube runs from
     * @param reachEnd   the end of the reach along the look
     * @param shape      the drill cone whose cross section sizes the tube
     * @param gloveRight whether the glove is in the right hand
     * @param air        whether a cell holds air
     * @param breakable  whether a cell holds a block in the tag
     * @return the cross section's place
     */
    private static Section sectionOf(Vec3 eye, Vec3 reachEnd, BoreDrillShape shape, boolean gloveRight,
                                     Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        Vec3 look = reachEnd.subtract(eye).normalize();
        Optional<Hit> crosshair = hitOf(eye, reachEnd, air, breakable);
        List<Hit> hits = new ArrayList<>();
        crosshair.ifPresent(hits::add);
        for (Vec3 offset : shape.rimOffsets(look, gloveRight, RIM_RAYS, 1)) {
            hitOf(eye.add(offset), reachEnd.add(offset), air, breakable)
                    .filter(hit -> crosshair.isEmpty() || standsInFront(hit, crosshair.get()))
                    .ifPresent(hits::add);
        }
        return hits.stream().min(Comparator.comparingDouble(hit -> hit.at().subtract(eye).dot(look)))
                .map(hit -> new Section(eye.add(look.scale(hit.at().subtract(eye).dot(look))), hit.face(), true))
                .orElse(new Section(reachEnd, look.reverse(), false));
    }

    /**
     * Whether a rim ray's block stands in front of the face the crosshair meets.
     *
     * @param rim       where the rim ray meets its block
     * @param crosshair where the eye line meets the crosshair's block
     * @return true for a block standing out from that face toward the eye
     */
    private static boolean standsInFront(Hit rim, Hit crosshair) {
        // a tunnel's floor, ceiling or side the rim grazes faces across the crosshair's face and sets nothing
        return rim.face().equals(crosshair.face()) && rim.at().subtract(crosshair.at()).dot(crosshair.face()) > IN_FRONT;
    }

    /**
     * Where a ray meets the first block that is not air, past the cell it
     * starts in, and the face it meets.
     *
     * @param start     the ray's start
     * @param end       the ray's end
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return the hit, empty where the ray meets nothing
     */
    private static Optional<Hit> hitOf(Vec3 start, Vec3 end, Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        List<BlockPos> crossed = BoreStep.blocksAlong(start, end);
        int met = firstMet(crossed, air, breakable);
        if (met == crossed.size()) {
            return Optional.empty();
        }
        BlockPos block = crossed.get(met);
        BlockHitResult hit = AABB.clip(List.of(UNIT_CELL), start, end, block);
        return hit == null ? Optional.of(new Hit(Vec3.atCenterOf(block), end.subtract(start).normalize().reverse()))
                : Optional.of(new Hit(hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i())));
    }

    /**
     * Where a bore's cross section lands on a breakable block it picks.
     *
     * @param eye        the eye the tube runs from
     * @param reachEnd   the end of the reach along the look
     * @param shape      the drill cone
     * @param gloveRight whether the glove is in the right hand
     * @param air        whether a cell holds air
     * @param breakable  whether a cell holds a block in the tag
     * @return the cross section's center and the first block it picks, empty where it picks none
     */
    static Optional<BorePoint> pointOf(Vec3 eye, Vec3 reachEnd, BoreDrillShape shape, boolean gloveRight,
                                       Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        Section section = sectionOf(eye, reachEnd, shape, gloveRight, air, breakable);
        BorePick pick = of(eye, shape, gloveRight, new ChannelAim(reachEnd, null, 0), air, breakable);
        return pick.blocks().isEmpty() ? Optional.empty()
                : Optional.of(new BorePoint(pick.blocks().getFirst(), section.center()));
    }

    /**
     * Where a bore's cross section stands: at the depth the tube sets, on a
     * breakable block or a solid block outside the tag alike, so a drill
     * drawn to it never runs into a block it does not break; or the end of
     * the reach where no ray meets a block.
     *
     * @param eye        the eye the tube runs from
     * @param reachEnd   the end of the reach along the look
     * @param shape      the drill cone
     * @param gloveRight whether the glove is in the right hand
     * @param air        whether a cell holds air
     * @param breakable  whether a cell holds a block in the tag
     * @return the cross section's center
     */
    static Vec3 stopOf(Vec3 eye, Vec3 reachEnd, BoreDrillShape shape, boolean gloveRight, Predicate<BlockPos> air,
                       Predicate<BlockPos> breakable) {
        return sectionOf(eye, reachEnd, shape, gloveRight, air, breakable).center();
    }

    /**
     * Where a ray first meets a block past the cell it starts in: a breakable
     * block, or a solid block outside the tag, which stops it alike.
     *
     * @param crossed   the blocks the ray crosses, its start's own first
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return that block's index on the ray, or the ray's length where it meets none
     */
    private static int firstMet(List<BlockPos> crossed, Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        for (int i = 1; i < crossed.size(); i++) {
            if (breakable.test(crossed.get(i)) || !air.test(crossed.get(i))) {
                return i;
            }
        }
        return crossed.size();
    }
}
