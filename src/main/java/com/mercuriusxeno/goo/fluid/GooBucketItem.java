package com.mercuriusxeno.goo.fluid;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypeNames;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The one goo bucket over the generic goo fluid, carrying its type in the
 * GOO_TYPE data component (decision generic-goo-items). Emptying it stamps
 * that type onto the fluid block it places (decision generic-goo-fluids).
 */
public class GooBucketItem extends BucketItem {

    /**
     * @param fluid      the generic goo source fluid
     * @param properties the item properties
     */
    public GooBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    /**
     * A full bucket of one goo type.
     *
     * @param key the goo type's registry key
     * @return a bucket stack stamped with that type
     */
    public static ItemStack of(ResourceKey<GooTypeDefinition> key) {
        ItemStack stack = new ItemStack(GooItems.GOO_BUCKET.get());
        stack.set(GooDataComponents.GOO_TYPE.get(), key);
        return stack;
    }

    /**
     * The type key a bucket stack carries.
     *
     * @param stack a bucket stack
     * @return the key in its GOO_TYPE component, or null for a stack carrying none
     */
    public static @Nullable ResourceKey<GooTypeDefinition> keyOf(ItemStack stack) {
        return stack.get(GooDataComponents.GOO_TYPE.get());
    }

    /**
     * The bundled type a bucket stack carries.
     *
     * @param stack a bucket stack
     * @return the enum value, or null for a stack carrying no bundled type
     */
    public static @Nullable GooType typeOf(ItemStack stack) {
        ResourceKey<GooTypeDefinition> key = keyOf(stack);
        return key == null ? null : GooType.fromKey(key);
    }

    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        return GooTypeNames.bucketName(keyOf(stack));
    }

    /**
     * Stamps the stack's type on the block the vanilla bucket placed into.
     * When the target cannot hold fluid the vanilla bucket re-enters this
     * method at the block beside the hit, so the stamp lands there on that
     * pass. A call carrying no stack places goo of no type.
     *
     * @param entity the entity emptying the bucket, or null
     * @param level  the level
     * @param pos    the position to place into
     * @param hit    the block hit that chose the position, or null
     * @param stack  the bucket stack, or null
     * @return true when the fluid was placed
     */
    @Override
    public boolean emptyContents(@Nullable LivingEntity entity, @NonNull Level level, @NonNull BlockPos pos,
                                 @Nullable BlockHitResult hit, @Nullable ItemStack stack) {
        boolean emptied = super.emptyContents(entity, level, pos, hit, stack);
        ResourceKey<GooTypeDefinition> key = stack == null ? null : keyOf(stack);
        if (emptied && key != null) {
            GooFluidBlock.stampType(level, pos, key);
        }
        return emptied;
    }
}
