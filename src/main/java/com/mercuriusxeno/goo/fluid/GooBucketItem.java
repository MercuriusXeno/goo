package com.mercuriusxeno.goo.fluid;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A bucket of one bundled goo type over the generic goo fluid. Emptying it
 * stamps the type onto the fluid block it places (decision
 * generic-goo-fluids); buckets stay one per bundled type until
 * generic-goo-items moves the type onto a component.
 */
public class GooBucketItem extends BucketItem {

    private final GooType type;

    /**
     * @param type       the goo type this bucket holds
     * @param fluid      the generic goo source fluid
     * @param properties the item properties
     */
    public GooBucketItem(GooType type, Fluid fluid, Properties properties) {
        super(fluid, properties);
        this.type = type;
    }

    /**
     * @return the goo type this bucket holds
     */
    public GooType type() {
        return type;
    }

    /**
     * Stamps the type on the block the vanilla bucket placed into. When the
     * target cannot hold fluid the vanilla bucket re-enters this method at
     * the block beside the hit, so the stamp lands there on that pass.
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
        if (emptied) {
            GooFluidBlock.stampType(level, pos, type.key());
        }
        return emptied;
    }
}
