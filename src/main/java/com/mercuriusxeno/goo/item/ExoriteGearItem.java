package com.mercuriusxeno.goo.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Consumer;

/**
 * An exorite pickaxe, sword or armor piece: it keeps working until 0
 * durability, where it stays whole and works as a bare hand (decision
 * zero-durability-stops-working).
 */
public class ExoriteGearItem extends Item implements ExoriteGear {

    /**
     * Creates the piece.
     *
     * @param properties the item properties
     */
    public ExoriteGearItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
        return ExoriteGear.absorbBreakingDamage(stack, super.damageItem(stack, amount, entity, onBroken), entity);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return ExoriteGear.destroySpeed(ExoriteGear.isBroken(stack), super.getDestroySpeed(stack, state));
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return ExoriteGear.correctToolForDrops(ExoriteGear.isBroken(stack), super.isCorrectToolForDrops(stack, state));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return ExoriteGear.isBroken(context.getItemInHand()) ? InteractionResult.PASS : super.useOn(context);
    }
}
