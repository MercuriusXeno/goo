package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.block.ability.WispBlock;
import com.mercuriusxeno.goo.block.ability.WispClaims;
import com.mercuriusxeno.goo.entity.DriftingWisp;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * A Wisps cell shedding a wisp toward the dark near it: the cell walks the
 * air connected to it out to one block past its layers, nearest first, as Wisps' flood walked it, picks a
 * destination slightly at random among the nearest dark air no other wisp
 * has claimed, lets out a wisp that drifts there, and spends a layer by the
 * laying step's chance, so a layer buys several wisps on average. Where no
 * dark air lies in reach the cell sheds nothing and spends nothing.
 * wisps-goo-sheds-a-wisp-toward-nearby-dark
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 * radiant-wisps-where-light-is-low
 * radiant-scatters-wisps-at-random-among-the-dark
 */
public final class WispShed {

    /** The most air cells one shed walks, so a cell under open sky stops short of its whole reach. */
    static final int WALK_BUDGET = 512;
    /** How many of the nearest dark, free cells a destination is picked among, so no grid shows. */
    static final int NEAREST_PICKED_AMONG = 8;
    private static final float CHIME_VOLUME = 0.4f;
    private static final float CHIME_PITCH = 1.6f;
    private static final float CHIME_PITCH_SPREAD = 0.4f;

    private WispShed() {
    }

    /**
     * Sheds a wisp from a Wisps cell toward dark air in reach, spending a
     * layer of the cell by the step's chance; nothing where the reach holds
     * no dark air.
     *
     * @param level the level
     * @param cell  the Wisps cell
     * @param state the cell's state
     * @param wisps the laying step, naming the chance and the wisp's life
     */
    public static void shed(ServerLevel level, BlockPos cell, BlockState state, WispsStep wisps) {
        int layers = state.getValue(VolumeCellBlock.LAYERS);
        Optional<BlockPos> destination = destinationFrom(level, cell, reachOf(layers));
        if (destination.isEmpty()) {
            return;
        }
        GooServerState.of(level.getServer()).wispClaims().claim(level, destination.get());
        DriftingWisp.shed(level, cell, destination.get(), wisps.wispLife());
        int left = layersAfterShedding(layers, level.getRandom().nextDouble(), wisps.shedSpendChance());
        if (left != layers) {
            level.setBlock(cell, left > 0 ? state.setValue(VolumeCellBlock.LAYERS, left)
                    : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * The dark, unclaimed air a wisp heads for from a spot, walking the air
     * connected to it out to a reach.
     *
     * @param level the level
     * @param from  where the wisp starts
     * @param reach how far off the destination may lie, in blocks
     * @return the destination, empty where the reach holds no dark, free air
     */
    public static Optional<BlockPos> destinationFrom(ServerLevel level, BlockPos from, int reach) {
        WispClaims claims = GooServerState.of(level.getServer()).wispClaims();
        return destination(reachedFrom(level, from, reach), level::getMaxLocalRawBrightness,
                GooConfig.wispsLightThreshold(), spot -> claims.claimed(level, spot), level.getRandom());
    }

    /**
     * Picks a wisp's destination among the air cells a walk reached, nearest
     * first: slightly at random among the nearest that lie under the light
     * threshold and that no other wisp has claimed.
     *
     * @param reached   the air cells reached, nearest first
     * @param lightAt   the light level at a cell
     * @param threshold the light a cell must lie under to be dark
     * @param claimed   whether another wisp is headed for a cell
     * @param random    the pick's roll
     * @return the destination, empty where no reached cell is dark and free
     */
    static Optional<BlockPos> destination(List<BlockPos> reached, ToIntFunction<BlockPos> lightAt, int threshold,
                                          Predicate<BlockPos> claimed, RandomSource random) {
        List<BlockPos> free = reached.stream()
                .filter(cell -> lightAt.applyAsInt(cell) < threshold && !claimed.test(cell))
                .limit(NEAREST_PICKED_AMONG).toList();
        return free.isEmpty() ? Optional.empty() : Optional.of(free.get(random.nextInt(free.size())));
    }

    /**
     * How far off a Wisps cell's shed wisp may head, in blocks: one past
     * its layers, so a full cell seeks farthest, past the reach of its own
     * light, and a thin one keeps its wisps close.
     * operator ruling 2026-11-16: the reach scales with the fill, n+1
     *
     * @param layers the layers the cell holds
     * @return the reach, in blocks
     */
    static int reachOf(int layers) {
        return layers + 1;
    }

    /**
     * The layers a Wisps cell holds after shedding a wisp: one fewer for a
     * draw under the spend chance, as many for any other.
     *
     * @param layers the layers the cell holds
     * @param draw   the roll, from 0 to 1
     * @param chance the chance the wisp spends a layer
     * @return the layers left
     */
    static int layersAfterShedding(int layers, double draw, double chance) {
        return draw < chance ? layers - 1 : layers;
    }

    /**
     * Whether a drifting wisp arriving at a spot settles there: open air
     * still under the light threshold.
     *
     * @param level the level
     * @param spot  the spot
     * @return true where the wisp becomes a wisp block
     */
    public static boolean settles(ServerLevel level, BlockPos spot) {
        return level.getBlockState(spot).isAir()
                && level.getMaxLocalRawBrightness(spot) < GooConfig.wispsLightThreshold();
    }

    /**
     * Settles a wisp as the glowy air block wisps are, fading on its own
     * after its life, with a chime.
     *
     * @param level the level
     * @param spot  the spot
     * @param life  the ticks the wisp lasts
     */
    public static void settle(ServerLevel level, BlockPos spot, int life) {
        level.setBlock(spot, GooBlocks.WISP.get().defaultBlockState(), Block.UPDATE_ALL);
        level.scheduleTick(spot, GooBlocks.WISP.get(), WispBlock.ticksBeforeFading(life));
        RandomSource random = level.getRandom();
        level.playSound(null, spot, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME,
                CHIME_PITCH + random.nextFloat() * CHIME_PITCH_SPREAD);
    }

    /**
     * The air cells connected to a spot out to a reach, nearest first.
     *
     * @param level the level
     * @param from  the spot
     * @param reach how far out, in blocks
     * @return the air cells reached
     */
    private static List<BlockPos> reachedFrom(ServerLevel level, BlockPos from, int reach) {
        List<BlockPos> reached = new ArrayList<>();
        new WispFlood(from, reach, 0, 0, 1.0).walk(WALK_BUDGET, new ReachedAir(level, reached), level.getRandom());
        return reached;
    }

    /** The air a shed's walk passes through and reaches, every air cell taken as a candidate. */
    private record ReachedAir(ServerLevel level, List<BlockPos> reached) implements WispFlood.Cells {

        @Override
        public boolean open(BlockPos cell) {
            return level.isLoaded(cell) && (level.getBlockState(cell).isAir()
                    || level.getBlockState(cell).is(GooBlocks.WISP.get()));
        }

        @Override
        public boolean dark(BlockPos cell) {
            return level.getBlockState(cell).isAir();
        }

        @Override
        public boolean seen(BlockPos cell) {
            return true;
        }

        @Override
        public void place(BlockPos cell) {
            reached.add(cell.immutable());
        }
    }
}
