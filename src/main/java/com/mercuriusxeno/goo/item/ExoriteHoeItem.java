package com.mercuriusxeno.goo.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Consumer;

/**
 * The exorite hoe: it keeps the netherite hoe's right-click use until 0
 * durability, where it stays whole and works as a bare hand (decision
 * zero-durability-stops-working).
 */
public class ExoriteHoeItem extends HoeItem implements ExoriteGear {

    /**
     * Creates the piece.
     *
     * @param material             the exorite tool material
     * @param attackDamageBaseline the netherite piece's attack damage baseline
     * @param attackSpeedBaseline  the netherite piece's attack speed baseline
     * @param properties the item properties
     */
    public ExoriteHoeItem(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline, Item.Properties properties) {
        super(material, attackDamageBaseline, attackSpeedBaseline, properties);
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
