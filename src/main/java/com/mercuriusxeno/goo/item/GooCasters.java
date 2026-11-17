package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * The item a player casts from: a glove in either hand, or, with no glove held, raw goo in the main hand. Raw goo
 * casts as the glove does, its selection carried in the same component, but only its own goo type; the glove is
 * that caster with an arm model and every type.
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
public final class GooCasters {

    private GooCasters() {
    }

    /**
     * The hand a player casts from: a glove's, the main hand's first, or the main hand holding raw goo where neither
     * hand holds a glove.
     *
     * @param player the player
     * @return the hand, or null where the player holds no caster
     */
    public static @Nullable InteractionHand casterHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof GooGloveItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof GooGloveItem) {
            return InteractionHand.OFF_HAND;
        }
        return player.getMainHandItem().getItem() instanceof GooItem ? InteractionHand.MAIN_HAND : null;
    }

    /**
     * The stack a player casts from.
     *
     * @param player the player
     * @return the glove or the raw goo in the caster hand, or null where the player holds no caster
     */
    public static @Nullable ItemStack casterOf(Player player) {
        InteractionHand hand = casterHand(player);
        if (hand == null) {
            return null;
        }
        return hand == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem();
    }

    /**
     * Whether a player holds a caster at all.
     *
     * @param player the player
     * @return true for a glove in either hand or raw goo in the main hand
     */
    public static boolean holdsCaster(Player player) {
        return casterHand(player) != null;
    }

    /**
     * Whether a stack drawn for a hand is the stack the player holds in either hand.
     *
     * @param player the player
     * @param drawn  the stack being drawn
     * @return true where the drawn stack stands for one of the player's hands
     */
    public static boolean drawnInHand(Player player, ItemStack drawn) {
        return drawnAs(drawn, player.getMainHandItem(), player.getOffhandItem())
                || drawnAs(drawn, player.getOffhandItem(), player.getMainHandItem());
    }

    /**
     * Whether a stack drawn for a hand is the stack the player casts from.
     *
     * @param player the player
     * @param drawn  the stack being drawn
     * @return true where the drawn stack stands for the player's caster
     */
    public static boolean drawnAsCaster(Player player, ItemStack drawn) {
        InteractionHand hand = casterHand(player);
        if (hand == null) {
            return false;
        }
        boolean main = hand == InteractionHand.MAIN_HAND;
        return main ? drawnAs(drawn, player.getMainHandItem(), player.getOffhandItem())
                : drawnAs(drawn, player.getOffhandItem(), player.getMainHandItem());
    }

    /**
     * Whether a drawn stack stands for a held one: the same stack, or raw goo
     * of the held goo's type the other hand does not hold. The first-person
     * hand draws the stack it last took, which lags the one the server sends
     * each tick a bare-handed cast drains the held goo; a glove's stack never
     * changes, so it is always the same stack.
     * decision raw-goo-items-work-bare-handed-for-the-held-type
     *
     * @param drawn     the stack being drawn
     * @param held      the stack held in the hand
     * @param otherHand the stack held in the other hand
     * @return true where the drawn stack is the held one
     */
    static boolean drawnAs(ItemStack drawn, ItemStack held, ItemStack otherHand) {
        return drawn == held || drawn != otherHand && sameRawGoo(drawn, held);
    }

    /**
     * Whether two stacks are raw goo of one type.
     *
     * @param drawn the stack being drawn
     * @param held  the stack held in the hand
     * @return true for raw goo of the same type
     */
    private static boolean sameRawGoo(ItemStack drawn, ItemStack held) {
        return drawn.getItem() instanceof GooItem && held.getItem() instanceof GooItem
                && Objects.equals(GooStacks.keyOf(drawn), GooStacks.keyOf(held));
    }

    /**
     * Whether the player's caster is a glove, which casts every type.
     *
     * @param player the player
     * @return true for a glove in either hand
     */
    public static boolean castsFromAGlove(Player player) {
        ItemStack caster = casterOf(player);
        return caster != null && caster.getItem() instanceof GooGloveItem;
    }

    /**
     * The goo type of the raw goo a player casts from bare handed.
     *
     * @param player the player
     * @return the held goo's type, or null where the player holds a glove or no caster
     */
    public static @Nullable ResourceKey<GooTypeDefinition> bareHandedType(Player player) {
        ItemStack caster = casterOf(player);
        return caster != null && caster.getItem() instanceof GooItem ? GooStacks.keyOf(caster) : null;
    }

    /**
     * Whether a player may cast an ability of a goo type: any type from a glove, only the held type from raw goo.
     *
     * @param player the player
     * @param type   the cast's goo type
     * @return true where the player's caster casts the type
     */
    public static boolean castsType(Player player, ResourceKey<GooTypeDefinition> type) {
        return stackCastsType(casterOf(player), type);
    }

    /**
     * Whether a caster stack casts a goo type: a glove every type, raw goo only its own.
     *
     * @param caster the caster stack, or null for none
     * @param type   the goo type
     * @return true where the stack casts the type
     */
    public static boolean stackCastsType(@Nullable ItemStack caster, ResourceKey<GooTypeDefinition> type) {
        if (caster == null) {
            return false;
        }
        return caster.getItem() instanceof GooGloveItem || type.equals(GooStacks.keyOf(caster));
    }

    /**
     * Whether the player's caster is in the right hand: the main arm for a caster in the main hand, the other arm for
     * a glove in the off hand, and the main arm where the player holds no caster. Knives' knives and every cast
     * drawn from the lifted palm blob leave from this side.
     * decision knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
     *
     * @param player the player
     * @return true for a caster in the right hand
     */
    public static boolean casterOnTheRight(Player player) {
        HumanoidArm arm = casterHand(player) == InteractionHand.OFF_HAND
                ? player.getMainArm().getOpposite() : player.getMainArm();
        return arm == HumanoidArm.RIGHT;
    }

    /**
     * The selection a caster stack holds: a glove's, or raw goo's where it names the goo's own type.
     *
     * @param caster the caster stack, or null for none
     * @return the selection, or null with no caster, no selection or raw goo naming another type
     */
    public static @Nullable GloveSelection selectionOf(@Nullable ItemStack caster) {
        if (caster == null) {
            return null;
        }
        if (caster.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelection(caster);
        }
        if (!(caster.getItem() instanceof GooItem)) {
            return null;
        }
        GloveSelection selection = GooGloveItem.getSelection(caster);
        return selection != null && Objects.equals(selection.getGooType(), GooStacks.keyOf(caster)) ? selection : null;
    }

    /**
     * The goo type a caster stack's selection names.
     *
     * @param caster the caster stack, or null for none
     * @return the selected type, or null where the stack selects none
     */
    public static @Nullable ResourceKey<GooTypeDefinition> selectedTypeOf(@Nullable ItemStack caster) {
        if (caster != null && caster.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(caster);
        }
        GloveSelection selection = selectionOf(caster);
        return selection == null ? null : selection.getGooType();
    }

    /**
     * The selection of the caster a player holds. Every selection names an
     * ability, so a caster with none selected casts nothing.
     * decision no-throw-without-ability
     *
     * @param player the player
     * @return the selection, or null where the player holds no caster or it selects nothing
     */
    public static @Nullable GloveSelection heldSelection(Player player) {
        return selectionOf(casterOf(player));
    }
}
