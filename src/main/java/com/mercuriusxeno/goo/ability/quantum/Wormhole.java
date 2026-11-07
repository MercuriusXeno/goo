package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Quantum's hole, a wormhole: it closes the space between the struck face and
 * the first opening ahead. A 3x3 cross-section runs from the struck block into
 * the face until a cross-section holds an opening a player fits in, the whole
 * 3x3 or a 2x3 within it, and a mouth opens flush on the struck face and one
 * flush on the wall's far face; whoever steps into either comes out of the
 * other until the hole closes. With no opening within reach the hole cannot
 * be made, and the face shows it collapse.
 * hole-closes-the-space-to-an-opening-ahead
 */
public final class Wormhole {

    /** How far the cross-section reaches out from the scan's line each way. */
    private static final int HALF_SPAN = 1;
    /** Points on the ring a wormhole's mouth or a collapse draws. */
    private static final int RING_POINTS = 24;
    /** The ring's radius around the mouth, in blocks. */
    private static final double RING_RADIUS = 1.2;
    /** How hard a collapsing ring's particles fall in toward its middle. */
    private static final double COLLAPSE_SPEED = 0.12;
    /** How far off the struck face the ring draws, so it shows over the block. */
    private static final double FACE_LIFT = 0.55;
    private static final double HALF = 0.5;
    /** How far out of its face a mouth's slab reaches: a body touches it only by pressing against the face. */
    private static final double FLUSH = 0.1;
    private static final float SOUND_VOLUME = 1.0f;
    private static final float MOUTH_PITCH = 1.3f;
    private static final float COLLAPSE_PITCH = 0.6f;

    private Wormhole() {
    }

    /**
     * The world as the scan reads it: whether a cell is open and whether a
     * body fits in a box.
     *
     * @param open whether a cell holds nothing a body collides with, inside the world
     * @param fits whether a box holds nothing a body collides with, inside the world
     */
    public record Space(Predicate<BlockPos> open, Predicate<AABB> fits) {

        /**
         * The level as a player passing through reads it; a cell above or
         * below the world is never open.
         *
         * @param level  the level
         * @param player the player passing through
         * @return the space
         */
        public static Space of(ServerLevel level, ServerPlayer player) {
            return new Space(cell -> !level.isOutsideBuildHeight(cell) && level.noCollision(player, new AABB(cell)),
                    box -> !level.isOutsideBuildHeight(BlockPos.containing(box.minX, box.minY, box.minZ))
                            && !level.isOutsideBuildHeight(BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                            && level.noCollision(player, box));
        }
    }

    /**
     * The first opening ahead of a struck face a body fits in.
     *
     * @param feet       where a body stands in it
     * @param firstOpen  the scan's centre cell where the opening's cross-section first opens, past the wall
     */
    public record Opening(Vec3 feet, BlockPos firstOpen) {
    }

    /**
     * Where a wormhole from a struck face comes out: the feet of the first
     * opening ahead a body fits in, the whole cross-section preferred to a
     * 2x3 within it at each step, and the step where that opening first
     * opens past the wall.
     *
     * @param space  the world as the scan reads it
     * @param struck the struck block, the scan's first centre
     * @param inward the direction the scan runs, into the struck face
     * @param reach  how many cells past the struck block the scan runs
     * @param width  the body's width
     * @param height the body's height
     * @return the opening, empty where none lies within reach
     */
    public static Optional<Opening> openingAhead(Space space, BlockPos struck, Direction inward, int reach,
            double width, double height) {
        Direction[] across = acrossOf(inward);
        for (int step = 1; step <= reach; step++) {
            List<List<BlockPos>> openings = openingsAt(struck.relative(inward, step), across);
            for (int shape = 0; shape < openings.size(); shape++) {
                List<BlockPos> opening = openings.get(shape);
                if (opening.stream().allMatch(space.open())
                        && space.fits().test(boxAt(feetIn(opening), width, height))) {
                    return Optional.of(new Opening(feetIn(opening), firstOpen(space, struck, inward, step, shape)));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Walks back from the step an opening was found at while its shape's cells stay open.
     *
     * @param space  the world as the scan reads it
     * @param struck the struck block
     * @param inward the direction the scan runs
     * @param step   the step the opening was found at
     * @param shape  the opening's place among a cross-section's openings
     * @return the centre cell where the opening first opens past the wall
     */
    private static BlockPos firstOpen(Space space, BlockPos struck, Direction inward, int step, int shape) {
        Direction[] across = acrossOf(inward);
        int first = step;
        while (first > 1 && openingsAt(struck.relative(inward, first - 1), across).get(shape).stream()
                .allMatch(space.open())) {
            first--;
        }
        return struck.relative(inward, first);
    }

    /**
     * Opens a wormhole from a struck face to the opening ahead: a mouth
     * flush on the struck face and one flush on the wall's far face, each
     * carrying whoever steps into it out of the other until the hole closes.
     * Where no opening lies within reach the face shows the hole collapse.
     *
     * @param level    the level
     * @param caster   the player whose hole it is, whose body the opening must fit
     * @param struck   the struck block
     * @param face     the struck face
     * @param reach    how many cells past the struck block the scan runs
     * @param lifetime the ticks the mouths stand
     * @return true when the hole opened
     */
    public static boolean open(ServerLevel level, ServerPlayer caster, BlockPos struck, Direction face, int reach,
            int lifetime) {
        Space space = Space.of(level, caster);
        Direction inward = face.getOpposite();
        Optional<Opening> found = openingAhead(space, struck, inward, reach, caster.getBbWidth(),
                caster.getBbHeight());
        if (found.isEmpty()) {
            collapse(level, struck, face);
            return false;
        }
        BlockPos firstOpen = found.get().firstOpen();
        BlockPos farWall = firstOpen.relative(face);
        Vec3 up = face.getAxis().isHorizontal() ? Direction.UP.getUnitVec3() : Direction.NORTH.getUnitVec3();
        OpenHoles.Mouth entry = new OpenHoles.Mouth(flushOn(struck, face), arrivalBefore(space, struck, face,
                caster), onFace(struck, face), new Gate(level.dimension(), layerAround(struck, face), face, up));
        OpenHoles.Mouth exit = new OpenHoles.Mouth(flushOn(farWall, inward), found.get().feet(),
                Vec3.atCenterOf(firstOpen), new Gate(level.dimension(), layerAround(farWall, inward), inward, up));
        OpenHoles.open(level.getServer(), new OpenHoles.Hole(level.dimension(), entry, exit, face.getAxis(),
                level.getGameTime() + lifetime));
        mouth(level, entry.middle());
        mouth(level, exit.middle());
        return true;
    }

    /**
     * The 3x3 layer of blocks around a block across a face, one block deep:
     * the blocks a mouth on that face covers.
     *
     * @param block the layer's centre
     * @param face  the face the mouth covers
     * @return the layer
     */
    static AABB layerAround(BlockPos block, Direction face) {
        AABB cell = new AABB(block);
        return switch (face.getAxis()) {
            case X -> cell.inflate(0, HALF_SPAN, HALF_SPAN);
            case Y -> cell.inflate(HALF_SPAN, 0, HALF_SPAN);
            case Z -> cell.inflate(HALF_SPAN, HALF_SPAN, 0);
        };
    }

    /**
     * The thin slab lying flush on a face across the 3x3 around a block, which
     * a body touches only by pressing against the face.
     *
     * @param block the block whose face it lies on, the cross-section's centre
     * @param face  the face
     * @return the slab
     */
    static AABB flushOn(BlockPos block, Direction face) {
        Direction.Axis axis = face.getAxis();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = block.get(axis) + (positive ? 1 : 0);
        double outer = plane + (positive ? FLUSH : -FLUSH);
        AABB span = new AABB(block).inflate(HALF_SPAN);
        return switch (axis) {
            case X -> new AABB(Math.min(plane, outer), span.minY, span.minZ, Math.max(plane, outer), span.maxY,
                    span.maxZ);
            case Y -> new AABB(span.minX, Math.min(plane, outer), span.minZ, span.maxX, Math.max(plane, outer),
                    span.maxZ);
            case Z -> new AABB(span.minX, span.minY, Math.min(plane, outer), span.maxX, span.maxY,
                    Math.max(plane, outer));
        };
    }

    /**
     * Where a traveller comes back out before the struck face: the cell in
     * front of it, or the one below that where the body fits only there.
     *
     * @param space  the world as the scan reads it
     * @param struck the struck block
     * @param face   the struck face
     * @param caster the body that must fit
     * @return the feet
     */
    private static Vec3 arrivalBefore(Space space, BlockPos struck, Direction face, ServerPlayer caster) {
        BlockPos front = struck.relative(face);
        for (BlockPos cell : List.of(front.below(), front)) {
            Vec3 feet = Vec3.atBottomCenterOf(cell);
            if (space.fits().test(boxAt(feet, caster.getBbWidth(), caster.getBbHeight()))) {
                return feet;
            }
        }
        return Vec3.atBottomCenterOf(front);
    }

    /**
     * Whether a wormhole from a struck face has an opening to carry a player
     * out to.
     *
     * @param level  the level
     * @param player the player who would pass through
     * @param struck the struck block
     * @param face   the struck face
     * @param reach  how many cells past the struck block the scan runs
     * @return true where an opening lies within reach
     */
    public static boolean opens(ServerLevel level, ServerPlayer player, BlockPos struck, Direction face, int reach) {
        return openingAhead(Space.of(level, player), struck, face.getOpposite(), reach, player.getBbWidth(),
                player.getBbHeight()).isPresent();
    }

    /**
     * Shows a hole that could not be made: a ring on the struck face falling
     * in on itself, and a dull drop of sound.
     *
     * @param level  the level
     * @param struck the struck block
     * @param face   the struck face
     */
    public static void collapse(ServerLevel level, BlockPos struck, Direction face) {
        Vec3 middle = onFace(struck, face);
        for (Vec3 offset : ring(face.getAxis())) {
            Vec3 at = middle.add(offset);
            Vec3 inward = offset.normalize().reverse();
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x(), at.y(), at.z(), 0,
                    inward.x(), inward.y(), inward.z(), COLLAPSE_SPEED);
        }
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.BLOCKS, SOUND_VOLUME, COLLAPSE_PITCH);
    }

    /**
     * Sounds a mouth opening; the client draws the mouth itself as a view
     * through to its partner.
     *
     * @param level  the level
     * @param middle the mouth's middle
     */
    static void mouth(ServerLevel level, Vec3 middle) {
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, SOUND_VOLUME, MOUTH_PITCH);
    }

    /**
     * The ring's points around a middle, in the plane across an axis.
     *
     * @param axis the axis the ring stands across
     * @return each point's offset from the middle
     */
    private static List<Vec3> ring(Direction.Axis axis) {
        List<Vec3> points = new ArrayList<>();
        for (int point = 0; point < RING_POINTS; point++) {
            double angle = Mth.TWO_PI * point / RING_POINTS;
            double first = Math.cos(angle) * RING_RADIUS;
            double second = Math.sin(angle) * RING_RADIUS;
            points.add(switch (axis) {
                case X -> new Vec3(0, first, second);
                case Y -> new Vec3(first, 0, second);
                case Z -> new Vec3(first, second, 0);
            });
        }
        return points;
    }

    private static Vec3 onFace(BlockPos struck, Direction face) {
        return Vec3.atCenterOf(struck).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(FACE_LIFT));
    }

    /**
     * The openings a cross-section offers, best first: the whole 3x3, then
     * each 2x3 within it, either way up.
     *
     * @param centre the cross-section's centre cell
     * @param across the two positive directions across the scan
     * @return each opening's cells
     */
    static List<List<BlockPos>> openingsAt(BlockPos centre, Direction[] across) {
        List<List<BlockPos>> openings = new ArrayList<>();
        openings.add(rectangle(centre, across, -HALF_SPAN, HALF_SPAN, -HALF_SPAN, HALF_SPAN));
        openings.add(rectangle(centre, across, -HALF_SPAN, 0, -HALF_SPAN, HALF_SPAN));
        openings.add(rectangle(centre, across, 0, HALF_SPAN, -HALF_SPAN, HALF_SPAN));
        openings.add(rectangle(centre, across, -HALF_SPAN, HALF_SPAN, -HALF_SPAN, 0));
        openings.add(rectangle(centre, across, -HALF_SPAN, HALF_SPAN, 0, HALF_SPAN));
        return openings;
    }

    private static List<BlockPos> rectangle(BlockPos centre, Direction[] across, int firstFrom, int firstTo,
            int secondFrom, int secondTo) {
        List<BlockPos> cells = new ArrayList<>();
        for (int first = firstFrom; first <= firstTo; first++) {
            for (int second = secondFrom; second <= secondTo; second++) {
                cells.add(centre.relative(across[0], first).relative(across[1], second));
            }
        }
        return cells;
    }

    /**
     * The bottom centre of an opening: its cells' middle across, standing on its lowest cell.
     *
     * @param opening the opening's cells
     * @return the feet of a body standing in it
     */
    private static Vec3 feetIn(List<BlockPos> opening) {
        double x = opening.stream().mapToDouble(cell -> cell.getX() + HALF).average().orElseThrow();
        double z = opening.stream().mapToDouble(cell -> cell.getZ() + HALF).average().orElseThrow();
        int y = opening.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        return new Vec3(x, y, z);
    }

    private static AABB boxAt(Vec3 feet, double width, double height) {
        double half = width * HALF;
        return new AABB(feet.x() - half, feet.y(), feet.z() - half, feet.x() + half, feet.y() + height,
                feet.z() + half);
    }

    private static Direction[] acrossOf(Direction inward) {
        return Arrays.stream(Direction.values())
                .filter(direction -> direction.getAxis() != inward.getAxis()
                        && direction.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                .toArray(Direction[]::new);
    }
}
