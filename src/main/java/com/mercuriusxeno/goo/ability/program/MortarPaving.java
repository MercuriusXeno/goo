package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.MortarCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Level's disc dragged under Mortar: the disc scoops the Mortar on its far
 * side into its carry rather than breaking it, and each gap on its near side
 * takes a stone block for a layer. While the hold lasts the carry is no block,
 * nine piles of layers riding the 3x3 over the disc, which the client draws;
 * when the hold ends it is set down as Mortar where it rides. The disc
 * carries a 3x3 of piles of fifteen at most.
 * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
 * level-scoops-up-to-three-by-three-piles-of-fifteen-layers
 * carried-mortar-stops-being-a-block-on-the-level-plane
 * layer-of-rock-goo-is-a-stone-block
 */
public final class MortarPaving {

    /** How many layers out from the face the disc reaches Mortar, as deep as Level's swath reaches. */
    private static final int CARRY_DEPTH = 3;
    /** The piles the disc carries: a 3x3. */
    public static final int PILES = 9;
    /** The most layers the disc carries: a 3x3 of piles of fifteen. */
    private static final int MOST_CARRIED = PILES * VolumeCellBlock.MAX_LAYERS;

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
     * What the disc scoops from the Mortar over it.
     *
     * @param pickedUp each pile the disc takes layers from, with the layers taken
     * @param leftOut  each pile left holding layers, with the layers it keeps
     */
    public record Scoop(Map<BlockPos, Integer> pickedUp, Map<BlockPos, Integer> leftOut) {
    }

    /**
     * Scoops the Mortar over the disc in turn until the disc carries a 3x3 of
     * piles of fifteen, taking only part of the pile that fills it; the rest
     * stays where it lies, to be come back for.
     * level-scoops-up-to-three-by-three-piles-of-fifteen-layers
     *
     * @param piles     the piles over the disc, each with its layers, the first scooped first
     * @param carriedIn the layers the disc already carries
     * @return the layers picked up and the layers left out
     */
    public static Scoop scoop(Map<BlockPos, Integer> piles, int carriedIn) {
        Map<BlockPos, Integer> pickedUp = new LinkedHashMap<>();
        Map<BlockPos, Integer> leftOut = new LinkedHashMap<>();
        int room = Math.max(0, MOST_CARRIED - carriedIn);
        for (Map.Entry<BlockPos, Integer> pile : piles.entrySet()) {
            int taken = Math.min(pile.getValue(), room);
            room -= taken;
            if (taken > 0) {
                pickedUp.put(pile.getKey(), taken);
            }
            if (pile.getValue() > taken) {
                leftOut.put(pile.getKey(), pile.getValue() - taken);
            }
        }
        return new Scoop(pickedUp, leftOut);
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
     * A carry's layers as its nine piles, filled as cells are laid: the middle
     * pile first, then the ring, each whole before the next.
     * carried-mortar-stops-being-a-block-on-the-level-plane
     *
     * @param layers the layers carried
     * @return the nine piles' layers, in pile order
     */
    static List<Integer> pilesOf(int layers) {
        List<Integer> piles = new ArrayList<>(PILES);
        int left = layers;
        for (int pile = 0; pile < PILES; pile++) {
            int held = Math.clamp(left, 0, VolumeCellBlock.MAX_LAYERS);
            piles.add(held);
            left -= held;
        }
        return piles;
    }

    /**
     * Scoops the Mortar over the disc where the cursor rests into the hold's
     * carry, paves the gaps under the disc from the carry, and lets the carry
     * ride over the disc as its nine piles, no block standing for it.
     * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
     * carried-mortar-stops-being-a-block-on-the-level-plane
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
        MortarCarry carry = host.holdMarks().mortarCarry();
        Map<BlockPos, Integer> piles = mortarIn(host, overDisc(plane, aimed).stream().filter(host::reaches).toList());
        Scoop scoop = scoop(piles, carry.layers());
        scoop.pickedUp().keySet().forEach(pos -> takeScooped(host, pos, scoop.leftOut().getOrDefault(pos, 0)));
        List<BlockPos> gaps = middleFirst(plane.layer(aimed, 0), plane.face().getAxis()).stream()
                .filter(pos -> host.reaches(pos) && isGap(host.stateAt(pos))).toList();
        Deposit deposit = deposit(gaps, carry.layers() + layersIn(scoop.pickedUp()));
        deposit.paved().forEach(pos -> host.transformBlock(pos, Blocks.STONE.defaultBlockState()));
        carry.ride(host, plane, aimed, pilesOf(deposit.layersLeft()));
    }

    /**
     * Takes a scooped cell's layers off it: the cell goes where the scoop took
     * every layer, and keeps what the scoop left otherwise.
     *
     * @param host the channel host
     * @param pos  the cell
     * @param kept the layers the scoop left in it
     */
    private static void takeScooped(ChannelHost host, BlockPos pos, int kept) {
        if (kept > 0) {
            layMortar(host, pos, kept);
        } else {
            host.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Sets a carry down as Mortar where it rides: each pile into the open
     * cells of its own column over the disc, the one touching the face first,
     * then any open cell over the disc, crediting the channeling player.
     * carried-mortar-stops-being-a-block-on-the-level-plane
     *
     * @param host   the channel host the hold last ran through
     * @param plane  the face the hold began on
     * @param middle the disc's middle where the carry last rode
     * @param piles  the nine piles' layers
     */
    static void setDown(ChannelHost host, ChannelAim.FacePlane plane, BlockPos middle, List<Integer> piles) {
        List<BlockPos> columns = pileCells(plane, middle);
        List<BlockPos> openOverDisc = overDisc(plane, middle).stream().filter(host::airAt).toList();
        Set<BlockPos> taken = new HashSet<>();
        for (int pile = 0; pile < piles.size(); pile++) {
            List<BlockPos> slots = new ArrayList<>();
            for (int out = 1; out <= CARRY_DEPTH; out++) {
                slots.add(plane.layer(columns.get(pile), out));
            }
            slots.retainAll(openOverDisc);
            openOverDisc.stream().filter(pos -> !slots.contains(pos)).forEach(slots::add);
            slots.removeAll(taken);
            Map<BlockPos, Integer> laid = lay(slots, piles.get(pile));
            laid.forEach((pos, layers) -> layMortar(host, pos, layers));
            taken.addAll(laid.keySet());
        }
    }

    private static int layersIn(Map<BlockPos, Integer> cells) {
        return cells.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * The cells the carry's nine piles ride in: the 3x3 just out from the
     * face in line with the disc's middle, the middle first, then its ring.
     * carried-mortar-stops-being-a-block-on-the-level-plane
     *
     * @param plane  the face the hold began on
     * @param middle the block the cursor rests on
     * @return the nine cells, in pile order
     */
    public static List<BlockPos> pileCells(ChannelAim.FacePlane plane, BlockPos middle) {
        return middleFirst(plane.cellOutFrom(middle), plane.face().getAxis());
    }

    /**
     * The cells over the disc the scoop reaches, the layer touching the face
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
     * The Mortar standing in some cells, each cell holding it with its layers.
     *
     * @param host  the channel host
     * @param cells the cells read
     * @return the cells holding Mortar, each with its layers, in the order read
     */
    private static Map<BlockPos, Integer> mortarIn(ChannelHost host, Collection<BlockPos> cells) {
        Map<BlockPos, Integer> mortar = new LinkedHashMap<>();
        for (BlockPos pos : cells) {
            BlockState state = host.stateAt(pos);
            if (state.getBlock() instanceof MortarCellBlock) {
                mortar.put(pos, MortarCellBlock.layersOf(state));
            }
        }
        return mortar;
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
