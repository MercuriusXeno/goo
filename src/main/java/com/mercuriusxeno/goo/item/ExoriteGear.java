package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * The seam every exorite piece shares (decision zero-durability-stops-working):
 * damage that would break a piece leaves it at 0 durability instead, and a
 * piece at 0 durability works as a bare hand until exorite repairs it.
 */
public interface ExoriteGear {

    /** A bare hand's mining speed on any block. */
    float BARE_HAND_DESTROY_SPEED = 1.0F;
    String BROKEN_TOOLTIP_KEY = "item." + Goo.MODID + ".exorite.broken";

    /**
     * Whether a piece at this damage has no durability left.
     *
     * @param damage    the damage taken
     * @param maxDamage the damage the piece can take
     * @return true at or past max damage
     */
    static boolean isBroken(int damage, int maxDamage) {
        return damage >= maxDamage;
    }

    /**
     * Whether this stack has no durability left.
     *
     * @param stack the stack
     * @return true at or past max damage
     */
    static boolean isBroken(ItemStack stack) {
        return isBroken(stack.getDamageValue(), stack.getMaxDamage());
    }

    /**
     * Whether a hit carries a piece to max damage, where vanilla would break it.
     *
     * @param damage    the damage taken
     * @param maxDamage the damage the piece can take
     * @param amount    the damage the hit deals
     * @return true when the hit reaches max damage
     */
    static boolean hitReachesFloor(int damage, int maxDamage, int amount) {
        return amount >= maxDamage - damage;
    }

    /**
     * Sets a piece a hit would break at max damage and answers no damage for
     * vanilla to apply, so vanilla's break callback never runs; any other hit
     * passes through whole. A holder with infinite materials takes no damage
     * in vanilla, so the hit passes through for vanilla to discard.
     *
     * @param stack  the exorite stack
     * @param amount the damage the hit deals
     * @param holder the entity using the piece, if any
     * @return the damage left for vanilla to apply
     */
    static int absorbBreakingDamage(ItemStack stack, int amount, @Nullable LivingEntity holder) {
        boolean takesDamage = holder == null || !holder.hasInfiniteMaterials();
        if (!takesDamage || !hitReachesFloor(stack.getDamageValue(), stack.getMaxDamage(), amount)) {
            return amount;
        }
        stack.setDamageValue(stack.getMaxDamage());
        return 0;
    }

    /**
     * The mining speed a piece has: its own while intact, a bare hand's broken.
     *
     * @param broken      whether the piece is broken
     * @param intactSpeed the speed the intact piece mines at
     * @return the speed to mine at
     */
    static float destroySpeed(boolean broken, float intactSpeed) {
        return broken ? BARE_HAND_DESTROY_SPEED : intactSpeed;
    }

    /**
     * Whether a piece gets block drops: as its tool rules say while intact,
     * never broken, so a block that needs a tool drops what a bare hand gets.
     *
     * @param broken      whether the piece is broken
     * @param intactRules whether the intact piece's tool rules allow drops
     * @return whether the block drops as mined with the correct tool
     */
    static boolean correctToolForDrops(boolean broken, boolean intactRules) {
        return !broken && intactRules;
    }

    /**
     * Adds the broken line to a broken piece's tooltip.
     *
     * @param stack   the stack
     * @param builder the tooltip sink
     */
    static void appendBrokenLine(ItemStack stack, Consumer<Component> builder) {
        if (isBroken(stack)) {
            builder.accept(Component.translatable(BROKEN_TOOLTIP_KEY).withStyle(ChatFormatting.RED));
        }
    }
}
