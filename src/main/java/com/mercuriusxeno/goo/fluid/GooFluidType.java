package com.mercuriusxeno.goo.fluid;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The one goo fluid type. Density, viscosity, temperature and extinguishing
 * of a placed goo fluid come from the type stamped on the block entity at
 * that position (decision generic-goo-fluids); the positionless overloads
 * answer the water-like defaults the properties hold.
 */
public class GooFluidType extends FluidType {

    /**
     * @param properties the defaults answered where no position is in hand
     */
    public GooFluidType(Properties properties) {
        super(properties);
    }

    @Override
    public int getDensity(@NonNull FluidState state, @NonNull BlockAndLightGetter getter, @NonNull BlockPos pos) {
        GooTypeDefinition definition = definitionAt(getter, pos);
        return definition == null ? super.getDensity(state, getter, pos) : definition.density();
    }

    @Override
    public int getViscosity(@NonNull FluidState state, @NonNull BlockAndLightGetter getter, @NonNull BlockPos pos) {
        GooTypeDefinition definition = definitionAt(getter, pos);
        return definition == null ? super.getViscosity(state, getter, pos) : definition.viscosity();
    }

    @Override
    public int getTemperature(@NonNull FluidState state, @NonNull BlockAndLightGetter getter, @NonNull BlockPos pos) {
        GooTypeDefinition definition = definitionAt(getter, pos);
        return definition == null ? super.getTemperature(state, getter, pos) : definition.temperature();
    }

    @Override
    public boolean canExtinguish(@NonNull FluidState state, @NonNull BlockGetter getter, @NonNull BlockPos pos) {
        GooTypeDefinition definition = definitionAt(getter, pos);
        return definition == null ? super.canExtinguish(state, getter, pos) : definition.extinguishes();
    }

    /**
     * Whether the goo the entity stands in puts it out: the type stamped at
     * the entity's block position.
     *
     * @param entity the entity in the goo
     * @return true when the type stamped at the entity's position extinguishes
     */
    @Override
    public boolean canExtinguish(@NonNull Entity entity) {
        GooTypeDefinition definition = definitionAt(entity.level(), entity.blockPosition());
        return definition == null ? super.canExtinguish(entity) : definition.extinguishes();
    }

    /**
     * The bucket of the type a goo stack carries, while buckets are one per
     * bundled type.
     *
     * @param stack the goo fluid stack
     * @return the bucket of the stamped bundled type, or the fluid's own bucket for a stack with none
     */
    @Override
    public @NonNull ItemStack getBucket(@NonNull FluidStack stack) {
        ResourceKey<GooTypeDefinition> key = stack.getComponents().get(GooDataComponents.GOO_TYPE.get());
        GooType type = key == null ? null : GooType.fromKey(key);
        return type == null ? super.getBucket(stack) : new ItemStack(GooItems.BUCKETS.get(type).get());
    }

    private static @Nullable GooTypeDefinition definitionAt(BlockGetter getter, BlockPos pos) {
        return getter instanceof LevelReader reader
                ? GooFluidBlockEntity.definitionAt(getter, pos, reader.registryAccess())
                : null;
    }
}
