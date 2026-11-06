package com.mercuriusxeno.goo.ability.gate;

import com.mercuriusxeno.goo.block.gate.EndGateBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Opens and closes End gate pairs: lays the gate's thin layer in the open
 * cells in front of the struck face and its mirror on the End platform's
 * floor, changing no block the gate lies against, and on the pair's clock
 * clears both layers. A burst of the End's particles and the portal's sounds
 * mark each opening and closing.
 * Decision end-clears-blocks-and-opens-a-portal.
 */
public final class EndGateOpening {

    /** The End platform's floor centre, under the spot the End's portal sets players down. */
    static final BlockPos PLATFORM_FLOOR = ServerLevel.END_SPAWN_POINT.below(2);
    /** From the struck block's centre to its face, where the gate's square lies. */
    private static final double SURFACE = 0.5;
    private static final int BURST_PARTICLES = 96;
    private static final double BURST_SPREAD = 1.2;
    private static final double BURST_SPEED = 0.6;

    private EndGateOpening() {
    }

    /**
     * Opens a pair on a struck face. A cast inside the End, against a face
     * with no open cell in front of the struck block, or while the End's
     * mirror spot is taken lays nothing.
     *
     * @param level    the level the blob landed in
     * @param surface  the struck block
     * @param face     the struck face
     * @param lifetime the ticks the pair stands
     * @return true once the pair stands open
     */
    public static boolean open(ServerLevel level, BlockPos surface, Direction face, int lifetime) {
        ServerLevel end = mirrorLevel(level);
        if (end == null) {
            return false;
        }
        EndPlatformFeature.createEndPlatform(end, PLATFORM_FLOOR.above(), true);
        return openPair(level, surface, face, lifetime, end, PLATFORM_FLOOR);
    }

    /**
     * Opens a pair on a struck face and its mirror lying up from a floor in
     * another level, the pair the End gate and Astral's gate both stand. A
     * face with no open cell in front of it, or a floor whose cell above is
     * taken, lays nothing.
     *
     * @param level    the level the blob landed in
     * @param surface  the struck block
     * @param face     the struck face
     * @param lifetime the ticks the pair stands
     * @param mirror   the level the mirror lies in
     * @param floor    the block the mirror lies on
     * @return true once the pair stands open
     */
    public static boolean openPair(ServerLevel level, BlockPos surface, Direction face, int lifetime,
                                   ServerLevel mirror, BlockPos floor) {
        Optional<GatePatch> near = cover(level, surface, face);
        Optional<GatePatch> far = near.isPresent() ? cover(mirror, floor, Direction.UP) : Optional.empty();
        if (near.isEmpty() || far.isEmpty()) {
            near.ifPresent(patch -> restore(level.getServer(), patch));
            return false;
        }
        long closesAt = level.getServer().overworld().getGameTime() + lifetime;
        EndGates.get(level).open(new EndGates.Pair(near.get(), far.get(), closesAt));
        mark(level, near.get(), ParticleTypes.REVERSE_PORTAL, SoundEvents.END_PORTAL_SPAWN);
        mark(mirror, far.get(), ParticleTypes.REVERSE_PORTAL, SoundEvents.END_PORTAL_SPAWN);
        return true;
    }

    /**
     * The End a cast lays its mirror in: none for a cast inside the End, and
     * none while another pair's mirror lies on the platform, which is rebuilt
     * only while no mirror stands on it.
     *
     * @param level the level the blob landed in
     * @return the End, or null where the cast lays nothing
     */
    private static @Nullable ServerLevel mirrorLevel(ServerLevel level) {
        ServerLevel end = level.getServer().getLevel(Level.END);
        boolean free = end != null && level.dimension() != Level.END
                && !end.getBlockState(PLATFORM_FLOOR.above()).is(GooBlocks.END_GATE.get());
        return free ? end : null;
    }

    /**
     * Closes every pair whose clock has run out, putting back the blocks
     * both gates covered.
     *
     * @param server the server
     * @param now    the overworld game time
     */
    public static void closeExpired(MinecraftServer server, long now) {
        for (EndGates.Pair pair : EndGates.get(server.overworld()).takeExpired(now)) {
            restore(server, pair.near());
            restore(server, pair.far());
        }
    }

    /**
     * Lays the gate's layer in the open cells in front of a face, keeping
     * what each cell held; a cell holding anything but air is left as it is.
     *
     * @param level   the level
     * @param surface the struck block the gate centres on
     * @param face    the face the gate looks out of
     * @return the gate laid, empty where the cell in front of the struck block is not open
     */
    private static Optional<GatePatch> cover(ServerLevel level, BlockPos surface, Direction face) {
        List<GateSquare.Cell> cells = GateSquare.cells(surface, face);
        if (!level.getBlockState(cells.getFirst().pos()).isAir()) {
            return Optional.empty();
        }
        List<GatePatch.Covered> covered = new ArrayList<>();
        for (GateSquare.Cell cell : cells) {
            BlockState held = level.getBlockState(cell.pos());
            if (held.isAir()) {
                covered.add(new GatePatch.Covered(cell.pos(), held));
                level.setBlock(cell.pos(), gateCell(face, cell), Block.UPDATE_ALL);
            }
        }
        return Optional.of(new GatePatch(level.dimension(), surface, face, covered));
    }

    /**
     * The gate block for one cell of the layer.
     *
     * @param face the face the gate looks out of
     * @param cell the cell and its place across the square
     * @return the block state
     */
    private static BlockState gateCell(Direction face, GateSquare.Cell cell) {
        return GooBlocks.END_GATE.get().defaultBlockState().setValue(EndGateBlock.FACING, face)
                .setValue(EndGateBlock.ACROSS, cell.across()).setValue(EndGateBlock.ALONG, cell.along());
    }

    /**
     * Clears a gate's layer, putting back what each cell held where the gate
     * still holds it.
     *
     * @param server the server
     * @param patch  the gate
     */
    static void restore(MinecraftServer server, GatePatch patch) {
        ServerLevel level = server.getLevel(patch.dimension());
        if (level == null) {
            return;
        }
        for (GatePatch.Covered cell : patch.covered()) {
            if (level.getBlockState(cell.pos()).is(GooBlocks.END_GATE.get())) {
                level.setBlock(cell.pos(), cell.state(), Block.UPDATE_ALL);
            }
        }
        mark(level, patch, ParticleTypes.PORTAL, SoundEvents.ENDERMAN_TELEPORT);
    }

    /**
     * Marks a gate opening or closing with a burst of particles and a sound
     * at its centre.
     *
     * @param level    the gate's level
     * @param patch    the gate
     * @param particle the burst's particle
     * @param sound    the sound
     */
    private static void mark(ServerLevel level, GatePatch patch, ParticleOptions particle, SoundEvent sound) {
        Vec3 at = Vec3.atCenterOf(patch.center()).add(Vec3.atLowerCornerOf(patch.face().getUnitVec3i())
                .scale(SURFACE));
        level.sendParticles(particle, at.x, at.y, at.z, BURST_PARTICLES, BURST_SPREAD, BURST_SPREAD, BURST_SPREAD,
                BURST_SPEED);
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1f, 1f);
    }
}
