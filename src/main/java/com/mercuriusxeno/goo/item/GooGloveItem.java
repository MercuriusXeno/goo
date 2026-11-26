package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Goo glove held in main/offhand. The glove menu key, G by default, opens
 * the radial to change selection while held and selects on release.
 * Right-click throws the selected goo type on the press, resolved on the
 * client off the use key; a durative ability charges and fires on release
 * like any other press, so the glove is never eaten.
 * decision durative-self-abilities-wash-over-the-view-in-place-of-eating
 */
public class GooGloveItem extends Item {

    /**
     * The glove tiers, each spelling the attack damage its main-hand melee
     * hit adds over the fist, and how fast it refills the palm blob's buffer.
     * decision glove-damage-by-tier
     * decision buffer-refills-at-a-constant-speed-set-by-the-glove
     */
    public enum GloveTier {
        /** The goo glove: 1% a tick. */
        GLOVE(2, 2),
        /** The goo gauntlet: 1.5% a tick. */
        GAUNTLET(4, 3),
        /** The exo gauntlet: 2% a tick. */
        EXO_GAUNTLET(6, 4);

        private final double attackDamageBonus;
        private final int bufferRefill;

        GloveTier(double attackDamageBonus, int bufferRefill) {
            this.attackDamageBonus = attackDamageBonus;
            this.bufferRefill = bufferRefill;
        }

        /**
         * How fast this tier refills the palm blob's buffer.
         *
         * @return the refill a tick, in half percents
         */
        public int bufferRefill() {
            return bufferRefill;
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

    /** The glove's tier. */
    private final GloveTier tier;

    /**
     * Creates a goo glove item of a tier with the given properties.
     *
     * @param properties the item properties
     * @param tier       the glove's tier
     */
    public GooGloveItem(Properties properties, GloveTier tier) {
        super(properties);
        this.tier = tier;
    }

    /**
     * The glove's tier.
     *
     * @return the tier
     */
    public GloveTier tier() {
        return tier;
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
