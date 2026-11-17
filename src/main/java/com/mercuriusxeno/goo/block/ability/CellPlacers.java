package com.mercuriusxeno.goo.block.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The player who placed each standing goo block whose hit credits its
 * placer, a goo cell or a light rail cell, by the block's position, since
 * each is a plain block with no block entity to remember one. Saved in each
 * level's data, so a block keeps its placer through unloads and restarts.
 * standing-goo-thing-credits-whoever-placed-it
 */
public class CellPlacers extends SavedData {

    /** Saves each cell's placer. */
    public static final Codec<CellPlacers> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Placed.CODEC.listOf().fieldOf("cells").forGetter(CellPlacers::placedList)
    ).apply(inst, CellPlacers::new));

    /** Stored as goo/cell_placers.dat in each level's data. */
    public static final SavedDataType<CellPlacers> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "cell_placers"), CellPlacers::new, CODEC);

    private final Map<BlockPos, UUID> placers = new HashMap<>();

    /** Creates a level's placers with no cell placed. */
    public CellPlacers() {
        this(List.of());
    }

    /**
     * Creates a level's placers from what it saved.
     *
     * @param placed each cell's placer
     */
    public CellPlacers(List<Placed> placed) {
        placed.forEach(cell -> placers.put(cell.pos(), cell.placer()));
    }

    /**
     * One cell's placer, as saved.
     *
     * @param pos    the cell
     * @param placer the player who placed it
     */
    public record Placed(BlockPos pos, UUID placer) {

        static final Codec<Placed> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Placed::pos),
                UUIDUtil.CODEC.fieldOf("placer").forGetter(Placed::placer)
        ).apply(inst, Placed::new));
    }

    /**
     * The level's placers, kept in its data.
     *
     * @param level the level
     * @return the placers
     */
    public static CellPlacers of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * @param cell a cell
     * @return the player who placed it, empty where no player did
     */
    public Optional<UUID> placerAt(BlockPos cell) {
        return Optional.ofNullable(placers.get(cell));
    }

    /**
     * Records a player placing goo in a cell; the last player to place in a
     * cell is the one it credits.
     * operator ruling 2026-11-17: when two players place, the last player overrides
     *
     * @param cell   the cell
     * @param player the player
     */
    public void place(BlockPos cell, UUID player) {
        if (!player.equals(placers.put(cell.immutable(), player))) {
            setDirty();
        }
    }

    /**
     * Records a layer moving from one cell into another: the cell it lands in
     * takes the placer of the cell it left, the layer being the last goo
     * placed there; a layer no player placed leaves the landing cell's placer standing.
     * operator ruling 2026-11-17: a poured cell inherits its source's placer
     *
     * @param from the cell the layer leaves
     * @param to   the cell the layer lands in
     */
    public void carry(BlockPos from, BlockPos to) {
        placerAt(from).ifPresent(player -> place(to, player));
    }

    /**
     * Forgets a cell that no longer stands.
     *
     * @param cell the cell
     */
    public void forget(BlockPos cell) {
        if (placers.remove(cell) != null) {
            setDirty();
        }
    }

    private List<Placed> placedList() {
        return placers.entrySet().stream().map(entry -> new Placed(entry.getKey(), entry.getValue())).toList();
    }
}
