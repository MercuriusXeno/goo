package com.mercuriusxeno.goo.ability.gate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * One gate of a Dragon Gate pair: where it lies, the face it looks out of,
 * and the blocks it covers, kept to put back when it closes.
 * Decision dragon-gate-banishes-blocks-and-opens-a-portal.
 *
 * @param dimension the level the gate lies in
 * @param center    the gate's centre cell
 * @param face      the face the gate looks out of
 * @param covered   each covered cell and the block it held
 */
public record GatePatch(ResourceKey<Level> dimension, BlockPos center, Direction face, List<Covered> covered) {

    /** Saves the gate with the server's gates. */
    public static final Codec<GatePatch> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(GatePatch::dimension),
            BlockPos.CODEC.fieldOf("center").forGetter(GatePatch::center),
            Direction.CODEC.fieldOf("face").forGetter(GatePatch::face),
            Covered.CODEC.listOf().fieldOf("covered").forGetter(GatePatch::covered)
    ).apply(inst, GatePatch::new));

    /**
     * Copies the covered list so the record holds it unmodifiable.
     */
    public GatePatch {
        covered = List.copyOf(covered);
    }

    /**
     * One cell a gate covers and the block it held.
     *
     * @param pos   the cell
     * @param state the block the cell held before the gate
     */
    public record Covered(BlockPos pos, BlockState state) {

        static final Codec<Covered> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Covered::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Covered::state)
        ).apply(inst, Covered::new));
    }

    /**
     * Whether a cell in a level is one of this gate's.
     *
     * @param level the level
     * @param pos   the cell
     * @return true for a covered cell of this gate
     */
    public boolean covers(ResourceKey<Level> level, BlockPos pos) {
        return dimension.equals(level) && covered.stream().anyMatch(cell -> cell.pos().equals(pos));
    }

    /**
     * Where this gate sets a traveller's feet down.
     *
     * @return the feet
     */
    public Vec3 arrival() {
        return GateFootprint.arrival(center, face);
    }
}
