package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.PlayerUtils;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Goo glove held in main/offhand. The glove menu key, G by default, opens
 * the radial to change selection while held and selects on release.
 * Right-click throws the selected goo type on the press, resolved on the
 * client off the use key.
 */
public class GooGloveItem extends Item {

    /** Recollect pickup sound volume. */
    private static final float PICKUP_VOLUME = 0.5f;
    /** Recollect pickup sound pitch. */
    private static final float PICKUP_PITCH = 1.2f;

    /**
     * The glove tiers, each spelling the attack damage its main-hand melee
     * hit adds over the fist.
     * decision glove-damage-by-tier
     */
    public enum GloveTier {
        /** The goo glove. */
        GLOVE(2),
        /** The goo gauntlet. */
        GAUNTLET(4),
        /** The exo gauntlet. */
        EXO_GAUNTLET(6);

        private final double attackDamageBonus;

        GloveTier(double attackDamageBonus) {
            this.attackDamageBonus = attackDamageBonus;
        }

        /**
         * The attack damage this tier adds to a main-hand melee hit.
         *
         * @return the bonus in half hearts
         */
        public double attackDamageBonus() {
            return attackDamageBonus;
        }
    }

    /**
     * The tier's main-hand attack damage modifier, carried by vanilla's base
     * attack damage id the way a sword carries its damage.
     * decision glove-damage-by-tier
     *
     * @param tier the glove tier
     * @return the item's attribute modifiers
     */
    public static ItemAttributeModifiers attackModifiers(GloveTier tier) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                        tier.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /**
     * Creates a goo glove item with the given properties.
     *
     * @param properties the item properties
     */
    public GooGloveItem(Properties properties) {
        super(properties);
    }

    /**
     * Shift+right-click on a chain marker recollects goo. Returns the
     * goo to the player's inventory and removes the marker.
     *
     * @param context the use-on-block context
     * @return SUCCESS if recollected, PASS otherwise
     */
    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) { return InteractionResult.PASS; }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            recollectGoo(level, pos, be, player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Gives the marker's one goo back to the player and removes the block.
     * @param level the world the marker exists in
     * @param pos the marker block position
     * @param be the chain marker block entity holding goo data
     * @param player the player receiving the recollected goo
     */
    private static void recollectGoo(Level level, BlockPos pos,
            ChainMarkerBlockEntity be, Player player) {
        ResourceKey<GooTypeDefinition> type = be.getGooType();
        PlayerUtils.addOrDrop(player, GooStacks.createForOutput(type, GooStacks.THOUSAND));
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                PICKUP_VOLUME, PICKUP_PITCH);
    }

    /**
     * Hands the press to the client's input gate and answers PASS: the
     * glove never enters the using state, and vanilla re-equips the held
     * item on any Success, so either would animate a click that throws
     * nothing (decision use-animation-only-when-goo-throws).
     *
     * @param level the world
     * @param player the player using the item
     * @param hand the hand holding the glove
     * @return PASS
     */
    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player,
            @NonNull InteractionHand hand) {
        if (level.isClientSide()) {
            ISidedProxy.get().pressGlove(hand);
        }
        return InteractionResult.PASS;
    }

    /**
     * Reads the goo type of this glove's selection.
     *
     * @param stack the glove stack
     * @return the selected goo type key, or null if none selected
     */
    public static @Nullable ResourceKey<GooTypeDefinition> getSelectedType(ItemStack stack) {
        GloveSelection sel = getSelection(stack);
        return sel == null ? null : sel.getGooType();
    }

    /**
     * Reads the selection (type and ability) from this glove. A stored
     * selection naming no ability reads as none (decision
     * no-throw-without-ability).
     *
     * @param stack the glove stack
     * @return the selection, or null if none
     */
    public static @Nullable GloveSelection getSelection(ItemStack stack) {
        GloveSelection sel = stack.get(GooDataComponents.SELECTED_ABILITY.get());
        return sel != null && sel.hasType() && sel.hasAbility() ? sel : null;
    }

    /**
     * Writes a selection (type and ability) to this glove; a selection
     * missing either clears the glove.
     *
     * @param stack     the glove stack
     * @param selection the selection to set
     */
    public static void setSelection(ItemStack stack, GloveSelection selection) {
        if (selection.hasType() && selection.hasAbility()) {
            stack.set(GooDataComponents.SELECTED_ABILITY.get(), selection);
        } else {
            stack.remove(GooDataComponents.SELECTED_ABILITY.get());
        }
    }
}
