package com.mercuriusxeno.goo.fluid;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The one goo liquid block. Its goo type rides on a
 * {@link GooFluidBlockEntity}, so the map color and the bucket a pickup
 * yields read that entity rather than the block state (decision
 * generic-goo-fluids).
 */
public class GooFluidBlock extends LiquidBlock implements EntityBlock {

    /**
     * @param fluid      the generic goo source fluid
     * @param properties the block properties
     */
    public GooFluidBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new GooFluidBlockEntity(pos, state);
    }

    /**
     * Stamps the type onto the block entity at a position, where a goo fluid
     * block stands there with no type yet. A block already stamped keeps its
     * type, so placing beside older goo never recolors it.
     *
     * @param level the level accessor
     * @param pos   the position of the goo fluid block
     * @param key   the goo type placed there
     */
    public static void stampType(LevelAccessor level, BlockPos pos, ResourceKey<GooTypeDefinition> key) {
        if (level.getBlockEntity(pos) instanceof GooFluidBlockEntity be && be.typeKey() == null) {
            be.stamp(key);
        }
    }

    @Override
    public @NonNull MapColor getMapColor(@NonNull BlockState state, @NonNull BlockGetter level,
                                         @NonNull BlockPos pos, @NonNull MapColor defaultColor) {
        GooTypeDefinition definition = level instanceof LevelReader reader
                ? GooFluidBlockEntity.definitionAt(level, pos, reader.registryAccess())
                : null;
        return definition == null ? defaultColor : definition.mapColor();
    }

    /**
     * Picks the block up into the goo bucket stamped with the type here
     * (decision generic-goo-items); a source block with no type yields
     * nothing.
     *
     * @param entity the entity picking up, or null
     * @param level  the level accessor
     * @param pos    the goo block position
     * @param state  the goo block state
     * @return the bucket of the stamped type, or an empty stack
     */
    @Override
    public @NonNull ItemStack pickupBlock(@Nullable LivingEntity entity, @NonNull LevelAccessor level,
                                          @NonNull BlockPos pos, @NonNull BlockState state) {
        ResourceKey<GooTypeDefinition> key = GooFluidBlockEntity.typeAt(level, pos);
        if (key == null || state.getValue(LEVEL) != 0) {
            return ItemStack.EMPTY;
        }
        super.pickupBlock(entity, level, pos, state);
        return GooBucketItem.of(key);
    }
}
