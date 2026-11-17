package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.FreezeBlocksStep;
import com.mercuriusxeno.goo.data.GooGroups;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What Rime goo freezes a source to: a water source to Rime ice and each lava
 * type's source to the block the molten groups pair it with, lava obsidian
 * and molten iron iron core. What stands frozen is no source, so it freezes
 * to nothing and the change runs one way: the water or lava under someone
 * crossing on it never comes back.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * rime-freezing-is-permanent-with-no-restore-on-removal
 * frost-cools-molten-blocks-into-molten-cooled-blocks
 * every-frost-freeze-turns-a-molten-iron-source-to-iron-core
 */
public final class RimeFreeze {

    private static final Identifier WATER = Identifier.withDefaultNamespace("water");
    private static final Identifier RIME_ICE = Identifier.fromNamespaceAndPath(Goo.MODID, "rime_ice");

    private RimeFreeze() {
    }

    /**
     * What a block freezes into under Rime.
     *
     * @param state the block
     * @return its frozen form, empty for anything other than a water or lava type source
     */
    static Optional<BlockState> frozenForm(BlockState state) {
        if (!state.getFluidState().isSource()) {
            return Optional.empty();
        }
        return frozenFormOf(GooGroups.current(), BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                .flatMap(BuiltInRegistries.BLOCK::getOptional).map(Block::defaultBlockState);
    }

    /**
     * The block a source freezes into, by block id: Rime ice for water, the
     * molten cooled group's member at the lava type's position in the molten
     * group for a lava type.
     *
     * @param groups each group's members by group name
     * @param source the source's block id
     * @return the frozen block's id, empty where the source neither is water nor stands paired in the molten groups
     */
    static Optional<Identifier> frozenFormOf(Map<String, List<Identifier>> groups, Identifier source) {
        if (source.equals(WATER)) {
            return Optional.of(RIME_ICE);
        }
        return FreezeBlocksStep.cooledFormOf(groups, source);
    }
}
