package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.block.ability.RimeCellBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The blocks Rime goo has frozen in a level, kept in the level's saved data
 * so none is lost over a restart: each water source it turned to Rime ice
 * and each lava source it turned to obsidian. Once no Rime cell stands in
 * reach of one, it goes back to water or lava; a block no longer standing
 * frozen, mined or melted, leaves the record.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 */
public final class RimeFrozenBlocks extends SavedData {

    private static final String FIELD_FROZEN = "frozen";

    /** Codec for the level's record. */
    static final Codec<RimeFrozenBlocks> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockPos.CODEC.listOf().fieldOf(FIELD_FROZEN).forGetter(record -> List.copyOf(record.frozen))
    ).apply(inst, RimeFrozenBlocks::new));

    /** Saved data type, stored as goo/rime_frozen_blocks.dat in each level's data storage. */
    static final SavedDataType<RimeFrozenBlocks> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "rime_frozen_blocks"), RimeFrozenBlocks::new, CODEC);

    private final Set<BlockPos> frozen;

    /** An empty record. */
    RimeFrozenBlocks() {
        this(List.of());
    }

    /**
     * A record of the blocks given.
     *
     * @param frozen the blocks Rime froze
     */
    RimeFrozenBlocks(List<BlockPos> frozen) {
        this.frozen = new LinkedHashSet<>(frozen);
    }

    /**
     * The record of a level.
     *
     * @param level the server level
     * @return its record
     */
    public static RimeFrozenBlocks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Records a block Rime froze.
     *
     * @param pos where it stands
     */
    void record(BlockPos pos) {
        if (frozen.add(pos.immutable())) {
            setDirty();
        }
    }

    /**
     * Freezes the water and lava sources within a Rime cell's reach, each
     * kept in the level's record; a reach running into an unloaded chunk
     * freezes nothing this pass.
     *
     * @param level the server level
     * @param cell  the Rime cell
     * @param reach how far from the cell water and lava freeze, in blocks along each axis
     */
    public static void freezeAround(ServerLevel level, BlockPos cell, int reach) {
        if (!reachLoaded(level, cell, reach)) {
            return;
        }
        RimeFrozenBlocks record = of(level);
        for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-reach, -reach, -reach),
                cell.offset(reach, reach, reach))) {
            Optional<RimeFreeze> frozenForm = RimeFreeze.of(level.getBlockState(pos))
                    .flatMap(form -> form.settled(true));
            frozenForm.ifPresent(form -> {
                level.setBlock(pos, form.state(), Block.UPDATE_ALL);
                record.record(pos);
            });
        }
    }

    /**
     * Sends every recorded block no Rime cell stands in reach of back to
     * water or lava and drops it from the record, as it drops a block no
     * longer standing frozen; a block whose reach runs into an unloaded
     * chunk waits for a later pass.
     *
     * @param level the server level
     * @param reach how far from a Rime cell a block stands frozen, in blocks along each axis
     */
    void thawUnheld(ServerLevel level, int reach) {
        boolean changed = frozen.removeIf(pos -> {
            if (!reachLoaded(level, pos, reach)) {
                return false;
            }
            Optional<RimeFreeze> form = RimeFreeze.of(level.getBlockState(pos)).filter(RimeFreeze::frozen);
            if (form.isEmpty()) {
                return true;
            }
            Optional<RimeFreeze> thawed = form.get().settled(rimeStandsWithin(level, pos, reach));
            thawed.ifPresent(warm -> level.setBlock(pos, warm.state(), Block.UPDATE_ALL));
            return thawed.isPresent();
        });
        if (changed) {
            setDirty();
        }
    }

    /**
     * Whether every chunk a reach around a block runs into is loaded, read
     * at the reach's four corners: a reach of at most
     * RimeStep's longest spans no more than two chunks along each axis, so
     * its corners stand in every chunk it touches.
     *
     * @param level the level
     * @param pos   the block at the reach's middle
     * @param reach how far the reach runs, in blocks along each axis
     * @return true where all of it is loaded
     */
    private static boolean reachLoaded(Level level, BlockPos pos, int reach) {
        int[] edges = {-reach, reach};
        for (int dx : edges) {
            for (int dz : edges) {
                if (!level.isLoaded(pos.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a Rime cell stands within reach of a block.
     *
     * @param level the level
     * @param pos   the block
     * @param reach how far from the block a cell counts, in blocks along each axis
     * @return true where one does
     */
    static boolean rimeStandsWithin(Level level, BlockPos pos, int reach) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-reach, -reach, -reach),
                pos.offset(reach, reach, reach))) {
            if (level.getBlockState(near).getBlock() instanceof RimeCellBlock) {
                return true;
            }
        }
        return false;
    }
}
