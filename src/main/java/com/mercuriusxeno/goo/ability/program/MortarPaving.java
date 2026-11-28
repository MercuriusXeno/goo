package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.MortarCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Level's disc dragged under Mortar: the disc lifts the Mortar on its far
 * side rather than breaking it, each gap on its near side takes a stone block
 * for a layer, and the layers left lie back over the disc where the cursor
 * rests now, so the goo travels with the cursor and stands where it was let go.
 * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
 * layer-of-rock-goo-is-a-stone-block
 */
public final class MortarPaving {

    /** How many layers out from the face the disc carries Mortar, as deep as Level's swath reaches. */
    private static final int CARRY_DEPTH = 3;

    private MortarPaving() {
    }

    /**
     * The stone a carry lays in the gaps under the disc.
     *
     * @param paved      the gaps taking a stone block, in the order they were handed
     * @param layersLeft the layers the carry holds once they are paved
     */
    public record Deposit(List<BlockPos> paved, int layersLeft) {
    }

    /**
     * Paves the gaps under the disc in turn, one carried layer a stone block,
     * until the gaps or the carry run out.
     * layer-of-rock-goo-is-a-stone-block
     *
     * @param gaps    the gaps under the disc, the first paved first
     * @param carried the layers the disc carries in
     * @return the gaps paved and the layers left
     */
    public static Deposit deposit(List<BlockPos> gaps, int carried) {
        int paving = Math.min(gaps.size(), Math.max(0, carried));
        return new Deposit(List.copyOf(gaps.subList(0, paving)), carried - paving);
    }

    /**
     * Lays a carry's layers into cells in turn, each filled whole before the next.
     *
     * @param slots  the cells the carry may lie in, the first filled first
     * @param layers the layers to lay
     * @return each cell laid with the layers it holds, in slot order; layers past the slots' room lie nowhere
     */
    public static Map<BlockPos, Integer> lay(List<BlockPos> slots, int layers) {
        Map<BlockPos, Integer> laid = new LinkedHashMap<>();
        int left = layers;
        for (BlockPos slot : slots) {
            if (left <= 0) {
                break;
            }
            int cell = Math.min(VolumeCellBlock.MAX_LAYERS, left);
            laid.put(slot, cell);
            left -= cell;
        }
        return laid;
    }

    /**
     * Carries the Mortar a hold has lifted and the Mortar over the disc where
     * the cursor rests: paves the gaps under the disc, then lays the layers
     * left over the disc, and remembers where they lie for the next tick.
     * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
     *
     * @param host  the channel host
     * @param aim   the hold's aim this tick
     * @param aimed the block the cursor rests on
     */
    static void carryUnderDisc(ChannelHost host, ChannelAim aim, BlockPos aimed) {
        ChannelAim.FacePlane plane = aim.plane();
        if (plane == null) {
            return;
        }
        HoldMarks marks = host.holdMarks();
        List<BlockPos> overDisc = overDisc(plane, aimed);
        Map<BlockPos, Integer> lifted = lifted(host, marks.carriedMortar(), overDisc);
        if (lifted.isEmpty()) {
            marks.carryMortarAt(List.of());
            return;
        }
        int carried = lifted.values().stream().mapToInt(Integer::intValue).sum();
        List<BlockPos> gaps = middleFirst(plane.layer(aimed, 0), plane.face().getAxis()).stream()
                .filter(pos -> host.reaches(pos) && isGap(host.stateAt(pos))).toList();
        Deposit deposit = deposit(gaps, carried);
        Map<BlockPos, Integer> laid = lay(slots(host, overDisc, lifted.keySet()), deposit.layersLeft());
        lifted.keySet().stream().filter(pos -> !laid.containsKey(pos))
                .forEach(pos -> host.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        deposit.paved().forEach(pos -> host.transformBlock(pos, Blocks.STONE.defaultBlockState()));
        laid.forEach((pos, layers) -> layMortar(host, pos, layers));
        marks.carryMortarAt(laid.keySet());
    }

    /**
     * The cells over the disc the carry reaches, the layer touching the face
     * first, each layer's middle before its ring.
     *
     * @param plane the face the hold began on
     * @param aimed the block the cursor rests on
     * @return the cells
     */
    private static List<BlockPos> overDisc(ChannelAim.FacePlane plane, BlockPos aimed) {
        List<BlockPos> cells = new ArrayList<>();
        for (int out = 1; out <= CARRY_DEPTH; out++) {
            cells.addAll(middleFirst(plane.layer(aimed, out), plane.face().getAxis()));
        }
        return cells;
    }

    /**
     * A 3x3 slice square to an axis, its middle first and its ring after.
     *
     * @param middle the slice's middle
     * @param main   the axis the slice stands square to
     * @return the nine cells
     */
    private static List<BlockPos> middleFirst(BlockPos middle, Direction.Axis main) {
        List<BlockPos> ringIn = ChannelAim.sliceRingIn(middle, main);
        List<BlockPos> slice = new ArrayList<>(ringIn.size());
        slice.add(ringIn.getLast());
        slice.addAll(ringIn.subList(0, ringIn.size() - 1));
        return slice;
    }

    /**
     * The Mortar the disc lifts this tick, each cell with its layers: what
     * the hold laid last tick, wherever it lies now, and what lies over the
     * disc in reach.
     *
     * @param host     the channel host
     * @param carried  the cells the hold laid last tick
     * @param overDisc the cells over the disc
     * @return the cells lifted, each with its layers
     */
    private static Map<BlockPos, Integer> lifted(ChannelHost host, Set<BlockPos> carried, List<BlockPos> overDisc) {
        Map<BlockPos, Integer> lifted = new LinkedHashMap<>();
        Set<BlockPos> reached = new LinkedHashSet<>(carried);
        overDisc.stream().filter(host::reaches).forEach(reached::add);
        for (BlockPos pos : reached) {
            BlockState state = host.stateAt(pos);
            if (state.getBlock() instanceof MortarCellBlock) {
                lifted.put(pos, MortarCellBlock.layersOf(state));
            }
        }
        return lifted;
    }

    /**
     * The cells the carry may lie in: those over the disc standing open or
     * lifted, then any lifted cell off the disc, so a carry with no room over
     * the disc stays where it lay.
     *
     * @param host     the channel host
     * @param overDisc the cells over the disc
     * @param lifted   the cells lifted
     * @return the slots, the first filled first
     */
    private static List<BlockPos> slots(ChannelHost host, List<BlockPos> overDisc, Set<BlockPos> lifted) {
        List<BlockPos> slots = new ArrayList<>();
        for (BlockPos pos : overDisc) {
            if (lifted.contains(pos) || host.airAt(pos)) {
                slots.add(pos);
            }
        }
        lifted.stream().filter(pos -> !slots.contains(pos)).forEach(slots::add);
        return slots;
    }

    /**
     * Whether a block under the disc is a gap a stone block fills: air, or a
     * space a placed block replaces that holds no goo.
     *
     * @param state the block standing there
     * @return true where stone may be laid
     */
    static boolean isGap(BlockState state) {
        return state.isAir() || state.canBeReplaced() && !(state.getBlock() instanceof VolumeCellBlock);
    }

    /**
     * Lays a Mortar cell of a number of layers, crediting the channeling player.
     *
     * @param host   the channel host
     * @param pos    the cell
     * @param layers its layers
     */
    private static void layMortar(ChannelHost host, BlockPos pos, int layers) {
        MortarCellBlock mortar = GooBlocks.ROCK_MORTAR.get();
        BlockState standing = host.stateAt(pos);
        if (standing.is(mortar) && MortarCellBlock.layersOf(standing) == layers) {
            return;
        }
        host.level().setBlock(pos, mortar.defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers),
                Block.UPDATE_ALL);
        mortar.creditPlacer(host.level(), pos, host.placingPlayer());
    }
}
