package com.mercuriusxeno.goo.ability.pulse;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * How Zap ticks the redstone device its blob landed on, one row per kind of
 * device: a lever, button, door, trapdoor or fence gate toggles as a hand
 * would, a repeater fires one pulse, and a redstone receiver in the
 * {@code goo:zap_receivers} tag takes a full-power source beside it for a
 * moment. Any other block is no device, and the Zap disperses into the
 * Signal wave there instead.
 * zap-ticks-the-device-and-stuns
 * zap-disperses-into-signal
 * zap-powers-anything-a-signal-runs
 */
public enum ZapDevice {
    /** A lever flips. */
    LEVER {
        @Override
        void tick(ServerLevel level, BlockPos device, BlockState state, BlockPos cell) {
            ((LeverBlock) state.getBlock()).pull(state, level, device, null);
        }
    },
    /** A button presses, and releases on its own timer; one already pressed stays as it is. */
    BUTTON {
        @Override
        void tick(ServerLevel level, BlockPos device, BlockState state, BlockPos cell) {
            if (!state.getValue(BlockStateProperties.POWERED)) {
                ((ButtonBlock) state.getBlock()).press(state, level, device, null);
            }
        }
    },
    /** A door opens or closes, both halves together. */
    DOOR {
        @Override
        void tick(ServerLevel level, BlockPos device, BlockState state, BlockPos cell) {
            DoorBlock door = (DoorBlock) state.getBlock();
            door.setOpen(null, level, state, device, !door.isOpen(state));
        }
    },
    /** A trapdoor or fence gate opens or closes. */
    OPENABLE {
        @Override
        void tick(ServerLevel level, BlockPos device, BlockState state, BlockPos cell) {
            BlockState toggled = state.cycle(BlockStateProperties.OPEN);
            level.setBlock(device, toggled, Block.UPDATE_ALL);
            level.gameEvent(null, toggled.getValue(BlockStateProperties.OPEN) ? GameEvent.BLOCK_OPEN
                    : GameEvent.BLOCK_CLOSE, device);
        }
    },
    /**
     * A repeater fires one pulse of its own delay: its scheduled tick turns
     * it on, and with no input standing it turns itself off again.
     */
    REPEATER {
        @Override
        void tick(ServerLevel level, BlockPos device, BlockState state, BlockPos cell) {
            if (!state.getValue(BlockStateProperties.POWERED)) {
                level.scheduleTick(device, state.getBlock(), 1);
            }
        }
    },
    /**
     * A redstone receiver takes a full-power source for one pulse, which
     * powers the dust, lamp, piston, plexer or reactor it touches and removes
     * itself. The source stands in the landing cell where that cell is
     * empty, and otherwise in the receiver's first empty neighbor, so a
     * landing inside the receiver's own cell or in a cell holding water or a
     * plant still pulses it; a receiver with no empty neighbor takes none.
     * zap-powers-anything-a-signal-runs
     */
    POWER_SOURCE {
        @Override
        void tick(ServerLevel level, BlockPos device, BlockState state, BlockPos cell) {
            pulseCell(pos -> level.getBlockState(pos).isAir(), device, cell).ifPresent(source ->
                    level.setBlock(source, GooBlocks.ZAP_PULSE.get().defaultBlockState(), Block.UPDATE_ALL));
        }
    };

    /**
     * The cell a receiver's pulse stands in: the landing cell where it is
     * empty and beside the receiver, otherwise the receiver's first empty
     * neighbor.
     *
     * @param empty  whether a cell holds nothing
     * @param device the receiver
     * @param cell   the cell the blob landed in, beside the receiver or its own
     * @return the cell, or empty where the receiver has no empty neighbor
     */
    static Optional<BlockPos> pulseCell(Predicate<BlockPos> empty, BlockPos device, BlockPos cell) {
        if (!cell.equals(device) && empty.test(cell)) {
            return Optional.of(cell);
        }
        return Arrays.stream(Direction.values()).map(device::relative).filter(empty).findFirst();
    }

    /**
     * The redstone receivers a Zap powers through the power source: dust,
     * lamps, pistons, the plexer, the reactor and every other block that
     * reads a neighbor's signal and takes no hand.
     * zap-disperses-into-signal
     * zap-powers-anything-a-signal-runs
     */
    public static final TagKey<Block> RECEIVERS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Goo.MODID, "zap_receivers"));

    /**
     * The block classes with a row of their own; every other block falls
     * under the power source. No block descends from two of them, so the order they
     * are read in decides nothing.
     */
    private static final List<Row> TOGGLES = List.of(
            new Row(LeverBlock.class, LEVER),
            new Row(ButtonBlock.class, BUTTON),
            new Row(DoorBlock.class, DOOR),
            new Row(TrapDoorBlock.class, OPENABLE),
            new Row(FenceGateBlock.class, OPENABLE),
            new Row(RepeaterBlock.class, REPEATER));

    /**
     * One row of the table: the block class and the row that ticks it.
     *
     * @param block  the block class, matching its subclasses too
     * @param device the row that ticks it
     */
    private record Row(Class<? extends Block> block, ZapDevice device) {
    }

    /**
     * Ticks the device once.
     *
     * @param level  the server level
     * @param device the block the blob landed on
     * @param state  that block's state
     * @param cell   the cell the blob landed in, beside the device or the device's own
     */
    abstract void tick(ServerLevel level, BlockPos device, BlockState state, BlockPos cell);

    /**
     * Ticks the block a Zap landed on as its row says.
     *
     * @param level  the server level
     * @param device the block the blob landed on
     * @param cell   the cell the blob landed in
     */
    public static void pulse(ServerLevel level, BlockPos device, BlockPos cell) {
        BlockState state = level.getBlockState(device);
        of(state.getBlock().getClass()).tick(level, device, state, cell);
    }

    /**
     * The block a Zap landed on: the landing cell's own block where the blob
     * landed in place, the struck block where it landed in the empty cell
     * beside it.
     *
     * @param level the level
     * @param cell  the cell the blob landed in
     * @param face  the struck block's face the blob landed on
     * @return the landed-on block
     */
    public static BlockPos landedOn(BlockGetter level, BlockPos cell, Direction face) {
        return level.getBlockState(cell).isAir() ? cell.relative(face.getOpposite()) : cell;
    }

    /**
     * Whether a Zap ticks the block as a redstone device rather than
     * dispersing into the Signal wave (decision zap-disperses-into-signal).
     *
     * @param state the landed-on block's state
     * @return true for a block with a row of its own or a redstone receiver
     */
    public static boolean ticks(BlockState state) {
        return ticks(of(state.getBlock().getClass()), state.is(RECEIVERS));
    }

    /**
     * Whether a Zap ticks a block, read off its row and the receiver tag.
     *
     * @param row      the block's row
     * @param receiver whether the block stands in {@link #RECEIVERS}
     * @return true for a row of its own, or the power source's row on a receiver
     */
    static boolean ticks(ZapDevice row, boolean receiver) {
        return row != POWER_SOURCE || receiver;
    }

    /**
     * The device a hand could toggle standing at a block, named by the block
     * that toggles it: a door's lower half for either half, so a wave
     * crossing both halves toggles the door once. Zap's Signal wave toggles
     * these and nothing else (decision zap-disperses-into-signal).
     *
     * @param level the level
     * @param pos   the block
     * @return the device's block, or empty where no lever, button, door,
     *         trapdoor or fence gate stands
     */
    public static Optional<BlockPos> handDevice(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ZapDevice row = of(state.getBlock().getClass());
        if (!row.byHand()) {
            return Optional.empty();
        }
        boolean upperDoor = row == DOOR && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
        return Optional.of(upperDoor ? pos.below() : pos.immutable());
    }

    /**
     * Toggles the hand device at a block as a hand would.
     *
     * @param level the server level
     * @param pos   the device's block, as {@link #handDevice} names it
     */
    public static void toggleByHand(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ZapDevice row = of(state.getBlock().getClass());
        if (row.byHand()) {
            row.tick(level, pos, state, pos);
        }
    }

    /**
     * Whether Tick pulses the block rather than toggling it: a device a Zap
     * powers that no hand toggles, the repeater and every receiver in
     * {@link #RECEIVERS}.
     * tick-pulses-anything-a-signal-runs-while-held
     *
     * @param state the block's state
     * @return true for a repeater or a redstone receiver
     */
    public static boolean pulsedByTick(BlockState state) {
        return pulsedByTick(of(state.getBlock().getClass()), state.is(RECEIVERS));
    }

    /**
     * Whether Tick pulses a block, read off its row and the receiver tag.
     *
     * @param row      the block's row
     * @param receiver whether the block stands in {@link #RECEIVERS}
     * @return true for the repeater's row, or the power source's row on a receiver
     */
    static boolean pulsedByTick(ZapDevice row, boolean receiver) {
        return !row.byHand() && ticks(row, receiver);
    }

    /**
     * Gives a repeater or a receiver the one pulse a Zap landing inside its
     * cell would: the repeater fires its own, a receiver takes the power
     * source in its first empty neighbor.
     * tick-pulses-anything-a-signal-runs-while-held
     *
     * @param level  the server level
     * @param device the repeater or receiver
     */
    public static void pulseInPlace(ServerLevel level, BlockPos device) {
        pulse(level, device, device);
    }

    /**
     * Whether a hand toggles the row's devices: every row but the repeater's
     * pulse and the power source.
     *
     * @return true for a lever, button, door, trapdoor or fence gate
     */
    boolean byHand() {
        return this != REPEATER && this != POWER_SOURCE;
    }

    /**
     * The row a block class falls under. Read by class, so the table reads
     * without a registry.
     *
     * @param block the landed-on block's class
     * @return the row that ticks it
     */
    public static ZapDevice of(Class<? extends Block> block) {
        return TOGGLES.stream().filter(row -> row.block().isAssignableFrom(block))
                .map(Row::device).findFirst().orElse(POWER_SOURCE);
    }
}
