package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One of the warrens' armed sculk mechanisms, so the place reacts to a player: a sculk sensor on sculk, a wireless
 * emitter touching it so the sensor's output is the emitter's input, a little cluster of volatile crystals standing as
 * the emitter's closest receiver, and sometimes a resonator a few blocks off, inside its own hearing range of the
 * sensor, so a sound out of the sensor's range still reaches it. A vibration sets the sensor off, the emitter beams its
 * level to the crystal, and the crystal shatters and explodes. Every mechanism picks its own emitter side, the way and
 * reach of its crystals, how many stand, and whether and where a resonator stands, so no two read as one stamp.
 * decision sculk-sensors-wire-to-pulse-emitter-crystals-that-set-off-volatile-crystals
 *
 * @param emitterSide  the side of the sensor the emitter stands on
 * @param crystalSide  the way from the emitter the crystals stand
 * @param crystalReach how far from the emitter the first crystal stands
 * @param crystals     how many crystals stand, one to four
 * @param resonator    where the resonator stands from the sensor, if one stands
 */
public record WarrenMechanism(Direction emitterSide, Direction crystalSide, int crystalReach, int crystals,
                              Optional<BlockPos> resonator) {

    /** The nearest and farthest the first crystal stands from the emitter. */
    static final int NEAREST_CRYSTAL = 2;
    static final int FARTHEST_CRYSTAL = 4;
    /** The fewest and most crystals a mechanism stands. */
    static final int FEWEST_CRYSTALS = 2;
    static final int MOST_CRYSTALS = 4;
    /** The nearest and farthest a resonator stands from the sensor, inside its eight-block hearing range. */
    static final int NEAREST_RESONATOR = 4;
    static final int FARTHEST_RESONATOR = 6;

    /**
     * Picks a mechanism's layout: its emitter on any side of the sensor, its crystals any way but back across the
     * sensor, two to four blocks off, two to four of them, and a resonator half the time, four to six blocks off any way
     * the crystals do not stand.
     *
     * @param random the feature's random
     * @return the layout
     */
    public static WarrenMechanism pick(RandomSource random) {
        Direction emitterSide = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Direction crystalSide = emitterSide.getOpposite();
        while (crystalSide == emitterSide.getOpposite()) {
            crystalSide = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        }
        int reach = NEAREST_CRYSTAL + random.nextInt(FARTHEST_CRYSTAL - NEAREST_CRYSTAL + 1);
        int crystals = FEWEST_CRYSTALS + random.nextInt(MOST_CRYSTALS - FEWEST_CRYSTALS + 1);
        Optional<BlockPos> resonator = Optional.empty();
        if (random.nextBoolean()) {
            Direction resonatorSide = crystalSide;
            while (resonatorSide == crystalSide) {
                resonatorSide = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            }
            int distance = NEAREST_RESONATOR + random.nextInt(FARTHEST_RESONATOR - NEAREST_RESONATOR + 1);
            resonator = Optional.of(BlockPos.ZERO.relative(resonatorSide, distance));
        }
        return new WarrenMechanism(emitterSide, crystalSide, reach, crystals, resonator);
    }

    /**
     * Where the emitter stands, from the sensor.
     *
     * @return the emitter's offset
     */
    public BlockPos emitterOffset() {
        return BlockPos.ZERO.relative(emitterSide);
    }

    /**
     * Where each crystal stands, from the sensor: the first out from the emitter, the next beside it to either side,
     * the last beyond it, a tight cluster on the floor.
     *
     * @return each crystal's offset, the emitter's closest receiver first
     */
    public List<BlockPos> crystalOffsets() {
        BlockPos first = emitterOffset().relative(crystalSide, crystalReach);
        List<BlockPos> cluster = List.of(first, first.relative(crystalSide.getClockWise()),
                first.relative(crystalSide.getCounterClockWise()), first.relative(crystalSide));
        return cluster.subList(0, crystals);
    }

    /**
     * One block a mechanism lays, where it stands.
     *
     * @param at      where it stands
     * @param state   the block it stands
     * @param onSculk whether it stands on sculk laid under it
     */
    public record Placed(BlockPos at, BlockState state, boolean onSculk) {
    }

    /**
     * Where each part stands with the sensor at a spot: the sensor there and the emitter beside it where that cell is
     * open, and each crystal and the resonator settled onto the floor near its own offset, a part that finds no floor
     * left out. A mechanism whose emitter cell is shut, or whose crystals all find no floor, has no plan.
     *
     * @param sensor where the sensor stands
     * @param open   whether a cell is open for the emitter
     * @param settle where a floor-standing part near a cell settles, or empty where it finds no floor
     * @return each part where it stands, the sensor, the emitter, the crystals with the closest first, the resonator
     */
    public Optional<List<Placed>> plan(BlockPos sensor, Predicate<BlockPos> open,
                                       Function<BlockPos, Optional<BlockPos>> settle) {
        BlockPos emitter = sensor.offset(emitterOffset());
        if (!open.test(emitter)) {
            return Optional.empty();
        }
        BlockState crystal = GooBlocks.VOLATILE_CRYSTAL.get().defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.UP);
        List<Placed> crystalsStanding = new ArrayList<>();
        for (BlockPos offset : crystalOffsets()) {
            settle.apply(sensor.offset(offset)).ifPresent(at -> crystalsStanding.add(new Placed(at, crystal, false)));
        }
        if (crystalsStanding.isEmpty()) {
            return Optional.empty();
        }
        List<Placed> plan = new ArrayList<>();
        plan.add(new Placed(sensor, Blocks.SCULK_SENSOR.defaultBlockState(), true));
        plan.add(new Placed(emitter, GooBlocks.WIRELESS_EMITTER.get().defaultBlockState(), true));
        plan.addAll(crystalsStanding);
        resonator.flatMap(offset -> settle.apply(sensor.offset(offset)))
                .ifPresent(at -> plan.add(new Placed(at, GooBlocks.RESONATOR.get().defaultBlockState(), true)));
        return Optional.of(plan);
    }

    /**
     * Lays the mechanism with its sensor at a spot on flat open floor, every part at its own offset.
     *
     * @param level  the level
     * @param sensor where the sensor stands
     * @param flags  the block update flags
     */
    public void lay(LevelAccessor level, BlockPos sensor, int flags) {
        plan(sensor, cell -> true, Optional::of).ifPresent(plan -> lay(level, plan, flags));
    }

    /**
     * Lays a planned mechanism, sculk under the sensor, the emitter and the resonator.
     *
     * @param level the level
     * @param plan  each part where it stands
     * @param flags the block update flags
     */
    public static void lay(LevelAccessor level, List<Placed> plan, int flags) {
        for (Placed part : plan) {
            if (part.onSculk()) {
                level.setBlock(part.at().below(), Blocks.SCULK.defaultBlockState(), flags);
            }
            level.setBlock(part.at(), part.state(), flags);
        }
    }
}
