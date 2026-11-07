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

    /** The mouth sizes a hole tries, largest first, each as its two spans across the face. */
    private static final int[][] MOUTH_SIZES = {{3, 3}, {3, 2}, {2, 3}, {2, 2}, {3, 1}, {1, 3}, {2, 1}, {1, 2},
        {1, 1}};
    /** A crawling body's height, the least room a hole needs past the wall. */
    private static final double CRAWL_HEIGHT = 0.6;
    /** Points on the ring a wormhole's mouth or a collapse draws. */
    private static final int RING_POINTS = 24;
    /** The ring's radius around the mouth, in blocks. */
    private static final double RING_RADIUS = 1.2;
    /** How hard a collapsing ring's particles fall in toward its middle. */
    private static final double COLLAPSE_SPEED = 0.12;
    /** How far off the struck face the ring draws, so it shows over the block. */
    private static final double FACE_LIFT = 0.55;
    private static final double HALF = 0.5;
    private static final float SOUND_VOLUME = 1.0f;
    private static final float MOUTH_PITCH = 1.3f;
    private static final float COLLAPSE_PITCH = 0.6f;
    /** How hard an opening mouth's motes burst outward across its face. */
    private static final double BURST_SPEED = 0.9;

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
     * A hole that fits: the struck face's cells its mouth covers, and the
     * cells of the wall's far face its other mouth covers, the same shape
     * straight through.
     *
     * @param mouth   the cells on the struck face
     * @param farWall the cells on the wall's far face, the last solid layer before the opening
     */
    public record Opening(List<BlockPos> mouth, List<BlockPos> farWall) {
    }

    /**
     * The largest hole a struck face makes room for, a 3x3 where there is
     * room, else the next smaller down to 1x1, each placed over the struck
     * block: its cells on the struck face solid with open cells before them,
     * and straight through the wall the first layer not wholly solid wholly
     * open, within reach, with room past it for a body to crawl out.
     *
     * @param space  the world as the scan reads it
     * @param struck the struck block
     * @param face   the struck face
     * @param reach  how many cells past the struck block the scan runs
     * @param width  the body's width
     * @return the hole, empty where none fits
     */
    public static Optional<Opening> largestHole(Space space, BlockPos struck, Direction face, int reach,
            double width) {
        Direction inward = face.getOpposite();
        for (List<BlockPos> mouth : mouthsOver(struck, acrossOf(inward))) {
            Optional<Opening> hole = holeThrough(space, mouth, face, reach, width);
            if (hole.isPresent()) {
                return hole;
            }
        }
        return Optional.empty();
    }

    /**
     * The hole one mouth's shape makes, if it fits.
     *
     * @param space the world as the scan reads it
     * @param mouth the mouth's cells on the struck face
     * @param face  the struck face
     * @param reach how many cells past the struck block the scan runs
     * @param width the body's width
     * @return the hole, empty where the shape does not fit
     */
    private static Optional<Opening> holeThrough(Space space, List<BlockPos> mouth, Direction face, int reach,
            double width) {
        boolean faced = mouth.stream().allMatch(cell -> !space.open().test(cell)
                && space.open().test(cell.relative(face)));
        if (!faced) {
            return Optional.empty();
        }
        Direction inward = face.getOpposite();
        for (int step = 1; step <= reach; step++) {
            List<BlockPos> layer = shifted(mouth, inward, step);
            long open = layer.stream().filter(space.open()).count();
            if (open == layer.size()) {
                return space.fits().test(crawlBoxIn(layer, inward, width))
                        ? Optional.of(new Opening(mouth, shifted(mouth, inward, step - 1))) : Optional.empty();
            }
            if (open > 0) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static List<BlockPos> shifted(List<BlockPos> cells, Direction toward, int steps) {
        return cells.stream().map(cell -> cell.relative(toward, steps)).toList();
    }

    /**
     * The box a crawling body fills coming out into an opening's layer: its
     * middle across, at the layer's floor through a wall, hung from the far
     * wall's face coming out of a ceiling, and standing on it coming out of a floor.
     *
     * @param layer  the opening's cells
     * @param inward the way the hole runs
     * @param width  the body's width
     * @return the box
     */
    private static AABB crawlBoxIn(List<BlockPos> layer, Direction inward, double width) {
        double x = layer.stream().mapToDouble(cell -> cell.getX() + HALF).average().orElseThrow();
        double z = layer.stream().mapToDouble(cell -> cell.getZ() + HALF).average().orElseThrow();
        double low = layer.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        double bottom = inward == Direction.DOWN ? low + 1 - CRAWL_HEIGHT : low;
        double half = width * HALF;
        return new AABB(x - half, bottom, z - half, x + half, bottom + CRAWL_HEIGHT, z + half);
    }

    /**
     * Every mouth shape over a struck block, largest first: 3x3, then 3x2 and
     * 2x3, 2x2, 3x1 and 1x3, 2x1 and 1x2, then 1x1, each placed every way
     * that covers the struck block, the most centred first.
     *
     * @param struck the struck block
     * @param across the two positive directions across the face
     * @return each mouth's cells
     */
    static List<List<BlockPos>> mouthsOver(BlockPos struck, Direction[] across) {
        List<List<BlockPos>> mouths = new ArrayList<>();
        for (int[] size : MOUTH_SIZES) {
            List<int[]> placings = new ArrayList<>();
            for (int first = 0; first < size[0]; first++) {
                for (int second = 0; second < size[1]; second++) {
                    placings.add(new int[] {first, second});
                }
            }
            placings.sort(java.util.Comparator.comparingDouble(placing -> Math.abs(placing[0] - (size[0] - 1) * HALF)
                    + Math.abs(placing[1] - (size[1] - 1) * HALF)));
            for (int[] placing : placings) {
                mouths.add(rectangle(struck, across, -placing[0], size[0] - 1 - placing[0], -placing[1],
                        size[1] - 1 - placing[1]));
            }
        }
        return mouths;
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
        Optional<Opening> found = largestHole(space, struck, face, reach, caster.getBbWidth());
        if (found.isEmpty()) {
            collapse(level, struck, face);
            return false;
        }
        Vec3 up = face.getAxis().isHorizontal() ? Direction.UP.getUnitVec3() : Direction.NORTH.getUnitVec3();
        Gate entry = new Gate(level.dimension(), boxOf(found.get().mouth()), face, up);
        Gate exit = new Gate(level.dimension(), boxOf(found.get().farWall()), inward, up);
        OpenHoles.open(level.getServer(), new OpenHoles.Hole(level.dimension(), entry, exit,
                level.getGameTime() + lifetime));
        mouth(level, entry);
        mouth(level, exit);
        return true;
    }

    /**
     * The blocks a mouth covers, as one box one block deep.
     *
     * @param cells the mouth's cells
     * @return the box
     */
    static AABB boxOf(List<BlockPos> cells) {
        return cells.stream().map(AABB::new).reduce(AABB::minmax).orElseThrow();
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
        return largestHole(Space.of(level, player), struck, face, reach, player.getBbWidth()).isPresent();
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
            level.sendParticles(ParticleTypes.SQUID_INK, at.x(), at.y(), at.z(), 0,
                    inward.x(), inward.y(), inward.z(), COLLAPSE_SPEED);
        }
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.CONDUIT_DEACTIVATE,
                SoundSource.BLOCKS, SOUND_VOLUME, COLLAPSE_PITCH);
    }

    /**
     * Opens a mouth with some fanfare: a powering-up hum, and a burst of
     * portal motes thrown outward across the face as the client grows the
     * mouth open, its view through to the partner inside.
     *
     * @param level the level
     * @param mouth the mouth
     */
    static void mouth(ServerLevel level, Gate mouth) {
        Vec3 middle = mouth.center();
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, SOUND_VOLUME, MOUTH_PITCH);
        for (Vec3 offset : ring(mouth.face().getAxis())) {
            Vec3 outward = offset.normalize();
            level.sendParticles(ParticleTypes.PORTAL, middle.x(), middle.y(), middle.z(), 0,
                    outward.x(), outward.y(), outward.z(), BURST_SPEED);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, middle.x() + offset.x() * HALF,
                    middle.y() + offset.y() * HALF, middle.z() + offset.z() * HALF, 0, outward.x(), outward.y(),
                    outward.z(), BURST_SPEED * HALF);
        }
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

    private static Direction[] acrossOf(Direction inward) {
        return Arrays.stream(Direction.values())
                .filter(direction -> direction.getAxis() != inward.getAxis()
                        && direction.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                .toArray(Direction[]::new);
    }
}
