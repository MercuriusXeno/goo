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
import java.util.function.IntPredicate;

/**
 * Level's disc dragged under Mortar: the disc scoops the Mortar on its far
 * side into its carry rather than breaking it, and each gap on its near side
 * takes a stone block for a layer. While the hold lasts the carry is no block,
 * nine piles of layers riding the 3x3 over the disc, which the client draws;
 * when the hold ends it is set down as Mortar where it rides. Each layer
 * scooped climbs onto the closest pile with room, and the disc carries a 3x3
 * of piles of fifteen at most.
 * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
 * level-scoops-up-to-three-by-three-piles-of-fifteen-layers
 * carried-mortar-stops-being-a-block-on-the-level-plane
 * level-gathers-loose-mortar-into-the-closest-empty-slot
 * layer-of-rock-goo-is-a-stone-block
 */
public final class MortarPaving {

    /** How many layers out from the face the disc reaches Mortar, as deep as Level's swath reaches. */
    private static final int CARRY_DEPTH = 3;
    /** The piles the disc carries: a 3x3. */
    public static final int PILES = 9;
    /** The pile a search answers where no pile qualifies. */
    static final int NO_PILE = -1;

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
     * @param piles    the carry's nine piles once the scoop climbs onto them, in pile order
     * @param pickedUp each pile the disc takes layers from, with the layers taken
     * @param leftOut  each pile left holding layers, with the layers it keeps
     */
    public record Scoop(List<Integer> piles, Map<BlockPos, Integer> pickedUp, Map<BlockPos, Integer> leftOut) {
    }

    /**
     * Gathers the loose Mortar over the disc: each layer scooped, cell by cell,
     * climbs onto the closest of the nine piles holding fewer than fifteen,
     * so a pile already carrying layers takes the climb like an empty one.
     * Once all nine hold fifteen, the 3x3 of piles of fifteen the disc carries
     * at most, the scoop takes nothing more and the rest stays where it lies,
     * to be come back for.
     * level-gathers-loose-mortar-into-the-closest-empty-slot
     * level-scoops-up-to-three-by-three-piles-of-fifteen-layers
     *
     * @param carried   the carry's nine piles' layers, in pile order
     * @param pileCells the cells the nine piles ride in, in pile order
     * @param loose     the loose Mortar over the disc, each cell with its layers, the first scooped first
     * @return the carry's piles after, the layers picked up and the layers left out
     */
    public static Scoop scoop(List<Integer> carried, List<BlockPos> pileCells, Map<BlockPos, Integer> loose) {
        List<Integer> piles = new ArrayList<>(carried);
        Map<BlockPos, Integer> pickedUp = new LinkedHashMap<>();
        Map<BlockPos, Integer> leftOut = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Integer> cell : loose.entrySet()) {
            int taken = 0;
            int pile = closestWithRoom(piles, pileCells, cell.getKey());
            while (taken < cell.getValue() && pile != NO_PILE) {
                piles.set(pile, piles.get(pile) + 1);
                taken++;
                pile = closestWithRoom(piles, pileCells, cell.getKey());
            }
            if (taken > 0) {
                pickedUp.put(cell.getKey(), taken);
            }
            if (cell.getValue() > taken) {
                leftOut.put(cell.getKey(), cell.getValue() - taken);
            }
        }
        return new Scoop(List.copyOf(piles), pickedUp, leftOut);
    }

    /**
     * The pile a layer scooped from a cell climbs onto: the one with room whose
     * column lies nearest the cell, ties going to the pile nearer the middle,
     * then to the pile first in the ring's order.
     * level-gathers-loose-mortar-into-the-closest-empty-slot
     *
     * @param piles     the nine piles' layers, in pile order
     * @param pileCells the cells the nine piles ride in, in pile order, the middle first
     * @param from      the cell the layer comes from
     * @return the pile's index, or NO_PILE where every pile holds fifteen
     */
    static int closestWithRoom(List<Integer> piles, List<BlockPos> pileCells, BlockPos from) {
        return closest(piles, pileCells, from, layers -> layers < VolumeCellBlock.MAX_LAYERS);
    }

    /**
     * The pile nearest a cell among those a test keeps, ties going to the pile
     * nearer the middle, then to the pile first in the ring's order.
     *
     * @param piles     the nine piles' layers, in pile order
     * @param pileCells the cells the nine piles ride in, in pile order, the middle first
     * @param from      the cell
     * @param keeps     whether a pile of so many layers qualifies
     * @return the pile's index, or NO_PILE where no pile qualifies
     */
    private static int closest(List<Integer> piles, List<BlockPos> pileCells, BlockPos from, IntPredicate keeps) {
        int closest = NO_PILE;
        for (int pile = 0; pile < piles.size(); pile++) {
            if (keeps.test(piles.get(pile)) && (closest == NO_PILE || nearer(pileCells, from, pile, closest))) {
                closest = pile;
            }
        }
        return closest;
    }

    /**
     * Whether one pile stands nearer a cell than another, the nearer the
     * middle winning a tie; a pile later in pile order wins no tie both share.
     *
     * @param pileCells the cells the nine piles ride in, the middle first
     * @param from      the cell
     * @param pile      the pile weighed
     * @param against   the pile it is weighed against, earlier in pile order
     * @return true where the pile weighed stands nearer
     */
    private static boolean nearer(List<BlockPos> pileCells, BlockPos from, int pile, int against) {
        double toPile = from.distSqr(pileCells.get(pile));
        double toAgainst = from.distSqr(pileCells.get(against));
        if (toPile != toAgainst) {
            return toPile < toAgainst;
        }
        BlockPos middle = pileCells.getFirst();
        return middle.distSqr(pileCells.get(pile)) < middle.distSqr(pileCells.get(against));
    }

    /**
     * Spends a layer of the carry on each gap paved, from the non-empty pile
     * nearest the gap, the nearer the middle winning a tie.
     * layer-of-rock-goo-is-a-stone-block
     *
     * @param piles     the nine piles' layers, in pile order
     * @param pileCells the cells the nine piles ride in, in pile order
     * @param paved     the gaps paved
     * @return the piles once the paving is paid
     */
    static List<Integer> spend(List<Integer> piles, List<BlockPos> pileCells, List<BlockPos> paved) {
        List<Integer> left = new ArrayList<>(piles);
        for (BlockPos gap : paved) {
            int nearest = closest(left, pileCells, gap, layers -> layers > 0);
            if (nearest != NO_PILE) {
                left.set(nearest, left.get(nearest) - 1);
            }
        }
        return List.copyOf(left);
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
        fillMortarHoles(host, plane, aimed);
        MortarCarry carry = host.holdMarks().mortarCarry();
        List<BlockPos> pileCells = pileCells(plane, aimed);
        Map<BlockPos, Integer> loose = mortarIn(host, overDisc(plane, aimed).stream().filter(host::reaches).toList());
        Scoop scoop = scoop(carry.piles(), pileCells, loose);
        scoop.pickedUp().keySet().forEach(pos -> takeScooped(host, pos, scoop.leftOut().getOrDefault(pos, 0)));
        List<BlockPos> gaps = middleFirst(plane.layer(aimed, 0), plane.face().getAxis()).stream()
                .filter(pos -> host.reaches(pos) && isGap(host.stateAt(pos))).toList();
        Deposit deposit = deposit(gaps, layersIn(scoop.piles()));
        deposit.paved().forEach(pos -> host.transformBlock(pos, Blocks.STONE.defaultBlockState()));
        carry.ride(host, plane, aimed, spend(scoop.piles(), pileCells, deposit.paved()));
    }

    /**
     * Fills each hole holding Mortar in the row under the disc, whatever the
     * carry holds: the hole turns to stone, its own Mortar paying the one
     * layer, and the rest of that Mortar rises into the cell one out from it,
     * joining any Mortar there, for the scoop to gather. A hole whose cell one
     * out can take no raised layers stays as it is.
     * level-passing-over-mortar-below-picks-it-up
     * layer-of-rock-goo-is-a-stone-block
     *
     * @param host  the channel host
     * @param plane the face the hold began on
     * @param aimed the block the cursor rests on
     */
    private static void fillMortarHoles(ChannelHost host, ChannelAim.FacePlane plane, BlockPos aimed) {
        List<BlockPos> row = middleFirst(plane.layer(aimed, 0), plane.face().getAxis()).stream()
                .filter(host::reaches).toList();
        mortarIn(host, row).forEach((hole, layers) -> {
            BlockPos above = plane.layer(hole, 1);
            int raised = layers - 1;
            if (takesRaised(host.stateAt(above), raised)) {
                // the stone lands first, so the raised Mortar lies on it rather than falling back into the hole
                host.transformBlock(hole, Blocks.STONE.defaultBlockState());
                if (raised > 0) {
                    layMortar(host, above, MortarCellBlock.layersOf(host.stateAt(above)) + raised);
                }
            }
        });
    }

    /**
     * Whether the cell one out from a Mortar hole can take the layers the
     * hole raises: air, or Mortar with room for them.
     * level-passing-over-mortar-below-picks-it-up
     *
     * @param above  the block standing one out from the hole
     * @param raised the layers the hole raises
     * @return true where the hole may fill
     */
    static boolean takesRaised(BlockState above, int raised) {
        return above.isAir() || above.getBlock() instanceof MortarCellBlock
                && MortarCellBlock.layersOf(above) + raised <= VolumeCellBlock.MAX_LAYERS;
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

    private static int layersIn(Collection<Integer> piles) {
        return piles.stream().mapToInt(Integer::intValue).sum();
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
