package com.mercuriusxeno.goo.ability.pulse;

import com.mercuriusxeno.goo.block.alientech.FaceMountedBlock;
import com.mercuriusxeno.goo.block.alientech.RelayBlock;
import com.mercuriusxeno.goo.block.alientech.RelayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The links between relay blocks: a relay links to every other
 * relay in range along one of the three axes, with only air between their centers and neither attached by the side
 * facing the other; each relay gives the strongest redstone signal reaching any relay it links to.
 * relay-prism-carries-the-signal-through-air
 */
public final class RelayNetwork {

    /**
     * How far a relay links, in blocks. An assumption: abilities.md names no
     * range, so the relay reaches as far as a loaded neighborhood of chunks
     * comfortably holds.
     */
    public static final int RANGE = 16;
    /** Samples per block along a link, fine enough to cross every cell it passes. */
    private static final int SAMPLES_PER_BLOCK = 8;
    /** The most relays one network walk gathers, bounding a relay's tick. */
    private static final int MAX_NETWORK = 64;

    private RelayNetwork() {
    }

    /**
     * The signal a relay gives this tick: the strongest signal reaching any other relay of its network.
     *
     * @param level the server level
     * @param pos   the relay's position
     * @return the power the relay gives, 0 to 15
     */
    public static int carriedTo(ServerLevel level, BlockPos pos) {
        int carried = 0;
        for (BlockPos other : networkOf(level, pos)) {
            if (!other.equals(pos)) {
                carried = Math.max(carried, entering(level, other));
            }
        }
        return carried;
    }

    /**
     * The strongest signal entering a relay from outside its network: every neighbor's signal toward it, save a
     * neighboring relay's and the signal of dust no stronger than the relay's own, which the relay may be powering
     * itself. Counting those let a linked pair hold itself on through the dust each powered once its feed was cut.
     * relay-prism-carries-the-signal-through-air
     *
     * @param level the server level
     * @param relay the relay's position
     * @return the signal entering it, 0 to 15
     */
    public static int entering(ServerLevel level, BlockPos relay) {
        int ownSignal = level.getBlockState(relay).getOptionalValue(BlockStateProperties.POWER).orElse(0);
        int entering = 0;
        for (Direction side : Direction.values()) {
            BlockPos from = relay.relative(side);
            if (!echoes(level.getBlockState(from), ownSignal)) {
                entering = Math.max(entering, level.getSignal(from, side));
            }
        }
        return entering;
    }

    /**
     * Whether a neighbor's signal may be the relay network's own coming back: another relay, or dust no stronger
     * than the signal the relay gives.
     *
     * @param neighbor  the neighbor's state
     * @param ownSignal the signal the relay gives
     * @return true where the neighbor's signal is not counted as entering
     */
    static boolean echoes(BlockState neighbor, int ownSignal) {
        return neighbor.getBlock() instanceof RelayBlock
                || neighbor.is(Blocks.REDSTONE_WIRE) && neighbor.getValue(RedstoneWireBlock.POWER) <= ownSignal;
    }

    /**
     * Every relay a relay reaches link by link, itself among them: a signal
     * entering any relay of the network leaves every other, however many hops
     * apart, rather than only the relays linked to it directly.
     * relay-prism-carries-the-signal-through-air
     *
     * @param level the server level
     * @param start the relay the walk starts from
     * @return the network's relays, the start first
     */
    static Set<BlockPos> networkOf(ServerLevel level, BlockPos start) {
        Set<BlockPos> network = new LinkedHashSet<>();
        Deque<BlockPos> toWalk = new ArrayDeque<>();
        network.add(start);
        toWalk.add(start);
        while (!toWalk.isEmpty() && network.size() < MAX_NETWORK) {
            BlockPos current = toWalk.poll();
            relaysNear(level, current).stream()
                    .filter(other -> !network.contains(other) && links(level, current, other))
                    .filter(network::add)
                    .forEach(toWalk::add);
        }
        return network;
    }

    /**
     * The relays standing within range of a position in loaded chunks.
     *
     * @param level the server level
     * @param pos   the center
     * @return the relays' positions
     */
    private static List<BlockPos> relaysNear(ServerLevel level, BlockPos pos) {
        List<BlockPos> relays = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(pos.getX() - RANGE);
        int maxChunkX = SectionPos.blockToSectionCoord(pos.getX() + RANGE);
        int minChunkZ = SectionPos.blockToSectionCoord(pos.getZ() - RANGE);
        int maxChunkZ = SectionPos.blockToSectionCoord(pos.getZ() + RANGE);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    addRelaysIn(chunk, pos, relays);
                }
            }
        }
        return relays;
    }

    private static void addRelaysIn(LevelChunk chunk, BlockPos pos, List<BlockPos> relays) {
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (isRelay(entity) && inLinkReach(pos, entity.getBlockPos())) {
                relays.add(entity.getBlockPos());
            }
        }
    }

    /**
     * Whether a block entity is a relay: a relay block.
     *
     * @param entity the block entity
     * @return true for a relay
     */
    public static boolean isRelay(BlockEntity entity) {
        return entity instanceof RelayBlockEntity;
    }

    /**
     * Whether two relays stand where a link can join them: on one axis, east
     * to west, up to down or north to south, never diagonally, and within range.
     * A diagonal link joined every relay to every other and lit the whole network.
     * relay-prism-carries-the-signal-through-air
     *
     * @param from one relay
     * @param to   the other
     * @return true when the two share a line along an axis within range
     */
    public static boolean inLinkReach(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dy = Math.abs(from.getY() - to.getY());
        int dz = Math.abs(from.getZ() - to.getZ());
        int axesApart = Integer.signum(dx) + Integer.signum(dy) + Integer.signum(dz);
        return axesApart == 1 && dx + dy + dz <= RANGE;
    }

    /**
     * Whether two relays in reach link: only air between them, and neither attached by the side facing the other.
     * The server's network and the client's drawn links both ask here.
     *
     * @param level the level
     * @param from  one relay
     * @param to    the other, in link reach
     * @return true when a link joins them
     */
    public static boolean links(BlockGetter level, BlockPos from, BlockPos to) {
        Direction ahead = Direction.getApproximateNearest(to.getX() - from.getX(), to.getY() - from.getY(),
                to.getZ() - from.getZ());
        return sideOpen(level.getBlockState(from), ahead) && sideOpen(level.getBlockState(to), ahead.getOpposite())
                && cellsBetween(from, to).stream().allMatch(cell -> level.getBlockState(cell).isAir());
    }

    /**
     * Whether a link may leave a relay by a side: every side but the one a face-mounted relay attaches by.
     *
     * @param state the relay's state
     * @param side  the side
     * @return true where a link may leave
     */
    static boolean sideOpen(BlockState state, Direction side) {
        return !(state.getBlock() instanceof FaceMountedBlock) || FaceMountedBlock.attachedSide(state) != side;
    }

    /**
     * The cells a line between two blocks' centers crosses, the two blocks aside.
     *
     * @param from one end
     * @param to   the other end
     * @return the cells between, in order from the first end
     */
    public static Set<BlockPos> cellsBetween(BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 line = Vec3.atCenterOf(to).subtract(start);
        int samples = Math.max(1, (int) Math.ceil(line.length() * SAMPLES_PER_BLOCK));
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int i = 1; i < samples; i++) {
            BlockPos cell = BlockPos.containing(start.add(line.scale((double) i / samples)));
            if (!cell.equals(from) && !cell.equals(to)) {
                cells.add(cell);
            }
        }
        return cells;
    }
}
