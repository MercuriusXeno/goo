package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.List;

/**
 * What an ability blob does when it lands on a block: a blob striking a
 * block its own ability places grows that block one size, and any other
 * strike places or stacks the ability's chain marker.
 */
public final class AbilityImpact {

    /** The block state property a placed block grows along. */
    private static final String SIZE_PROPERTY = "size";

    private AbilityImpact() {
    }

    /**
     * Lands an ability blob on a block.
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the blob names
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability) {
        if (absorbIntoPlacedBlock(level, pos, ability)) {
            return;
        }
        EffectBlockPlacement.placeOrStackAbility(level, pos, type, face, ability);
    }

    /**
     * Absorbs the blob into the struck block when the ability's place_block
     * step names that block and the block grows by size, growing it one
     * size unless it is already at its largest (decision
     * place-block-ability-grows-block).
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param ability the ability the blob names
     * @return true if the blob was absorbed and no marker should be placed
     */
    private static boolean absorbIntoPlacedBlock(ServerLevel level, BlockPos pos, AbilityDefinition ability) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!ability.placedBlocks().contains(BuiltInRegistries.BLOCK.getKey(block))) {
            return false;
        }
        Property<?> size = block.getStateDefinition().getProperty(SIZE_PROPERTY);
        if (size == null) {
            return false;
        }
        level.setBlock(pos, nextSize(state, size), Block.UPDATE_ALL);
        return true;
    }

    /**
     * Answers the state one value further along the property, or the state
     * unchanged at the last value.
     *
     * @param state    the current state
     * @param property the property to advance
     * @param <T>      the property's value type
     * @return the advanced state
     */
    private static <T extends Comparable<T>> BlockState nextSize(BlockState state, Property<T> property) {
        List<T> values = List.copyOf(property.getPossibleValues());
        int next = values.indexOf(state.getValue(property)) + 1;
        return next < values.size() ? state.setValue(property, values.get(next)) : state;
    }
}
