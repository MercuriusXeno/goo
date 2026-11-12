package com.mercuriusxeno.goo.ability.hole;

import com.mercuriusxeno.goo.ability.quantum.Wormhole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.function.Predicate;

/**
 * The blocks a portable hole's mouth covers: the 3x3 centred on the struck
 * block where every cell there offers the face the cast took, else the
 * largest smaller rectangle over the struck block whose cells all do, down
 * to the struck block alone, fitted in the order the tunnel fits its mouth.
 * decision hole-mouth-opens-three-by-three-and-fits-its-room
 */
public final class HoleFootprint {

    private HoleFootprint() {
    }

    /**
     * The footprint a cast opens in a level: a cell offers its face when the
     * face is sturdy and the cell beyond it holds nothing to collide with.
     *
     * @param level  the level
     * @param struck the struck block
     * @param face   the struck face
     * @return the footprint's blocks
     */
    public static List<BlockPos> of(ServerLevel level, BlockPos struck, Direction face) {
        return fit(struck, face, cell -> offers(level, cell, face));
    }

    /**
     * Whether a cell offers a face a mouth opens across: the face is sturdy
     * and the cell beyond it holds nothing to collide with. A hole opens only
     * where every cell of its footprint offers its face, and stands only while
     * every one still does.
     *
     * @param level the level
     * @param cell  a block
     * @param face  the face
     * @return true where a mouth may lie across that face
     */
    public static boolean offers(ServerLevel level, BlockPos cell, Direction face) {
        return level.getBlockState(cell).isFaceSturdy(level, cell, face)
                && level.getBlockState(cell.relative(face)).getCollisionShape(level, cell.relative(face)).isEmpty();
    }

    /**
     * The largest mouth over the struck block whose every cell offers its
     * face; the struck block alone where no larger one does, since the cast
     * already took that face.
     *
     * @param struck the struck block
     * @param face   the struck face
     * @param offers whether a cell offers the face the cast took
     * @return the footprint's blocks
     */
    static List<BlockPos> fit(BlockPos struck, Direction face, Predicate<BlockPos> offers) {
        return Wormhole.mouthsOn(struck, face).stream()
                .filter(mouth -> mouth.contains(struck) && mouth.stream().allMatch(offers))
                .findFirst()
                .orElse(List.of(struck));
    }
}
