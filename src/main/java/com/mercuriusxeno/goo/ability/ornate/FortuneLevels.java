package com.mercuriusxeno.goo.ability.ornate;

import com.mercuriusxeno.goo.ability.program.FortuneStep;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * The fortune level Fortune goo gives a break or a kill: one level per the
 * step's layers per level, rounded up, to its most levels, read from the
 * fullest Fortune cell holding on one of a broken block's faces, or from the
 * cell a killed mob stands in.
 * fortune-is-ornates-residual-that-makes-things-drop-more
 */
public final class FortuneLevels {

    private FortuneLevels() {
    }

    /**
     * The level a Fortune cell of the layers given sets.
     *
     * @param layers the cell's layers, 0 where no Fortune goo stands
     * @param step   the Fortune step naming the layers per level and the most levels
     * @return the level, 0 for no layers
     */
    public static int ofLayers(int layers, FortuneStep step) {
        if (layers <= 0) {
            return 0;
        }
        return Math.min(step.maxLevel(), (layers + step.layersPerLevel() - 1) / step.layersPerLevel());
    }

    /**
     * The fortune level a block's break rolls at, from the fullest Fortune
     * cell holding on one of its faces.
     *
     * @param fortuneLayersOnFace the layers of Fortune goo holding on each face of the block, 0 where none
     * @param step                the Fortune step
     * @return the level, 0 where no Fortune goo holds on the block
     */
    public static int forBreak(ToIntFunction<Direction> fortuneLayersOnFace, FortuneStep step) {
        return fullestFace(fortuneLayersOnFace).map(face -> ofLayers(fortuneLayersOnFace.applyAsInt(face), step))
                .orElse(0);
    }

    /**
     * The face the fullest Fortune cell holds on.
     *
     * @param fortuneLayersOnFace the layers of Fortune goo holding on each face of the block, 0 where none
     * @return the face, empty where no Fortune goo holds on the block
     */
    public static Optional<Direction> fullestFace(ToIntFunction<Direction> fortuneLayersOnFace) {
        return Arrays.stream(Direction.values()).filter(face -> fortuneLayersOnFace.applyAsInt(face) > 0)
                .max(Comparator.comparingInt(fortuneLayersOnFace));
    }

    /**
     * The layers a Fortune cell beside a block holds on the face between
     * them: a cell above lies on the block's top, a cell below hangs from
     * its underside where it holds its ceiling, and a cell beside holds on
     * the side its wall faces where it is attached there.
     *
     * @param beside      the state of the block beside the face
     * @param face        the face, from the block toward the one beside
     * @param fortuneCell the Fortune cell block
     * @return the cell's layers, 0 where no Fortune goo holds on the face
     */
    public static int layersOnFace(BlockState beside, Direction face, Block fortuneCell) {
        if (beside.getBlock() != fortuneCell) {
            return 0;
        }
        boolean holds = switch (face) {
            case UP -> true;
            case DOWN -> Boolean.TRUE.equals(beside.getValue(VolumeCellBlock.CEILING));
            default -> VolumeCellBlock.coatOn(beside, face.getOpposite()) == SideCoat.ATTACHED;
        };
        return holds ? beside.getValue(VolumeCellBlock.LAYERS) : 0;
    }
}
