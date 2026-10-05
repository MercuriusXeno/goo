package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.SelfEatRoute;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Server side of a self delivery: nothing leaves the hand. Invoking starts
 * the player eating the glove, and when the eat finishes the ability's cost
 * at stack zero drains from the inventory and its programs run on the
 * invoking player; an eat let go or interrupted before then runs nothing
 * and drains nothing. The eat replaces the throw sound on this route.
 * decision self-delivery-runs-on-player
 * decision self-brew-goos-eat-before-the-effect
 */
public final class GooSelfHandler {

    private static final String LOG_NO_GOO = "Self ability {} rejected: insufficient goo";
    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on the player host: {}";

    private GooSelfHandler() {
    }

    /**
     * Starts the eat for a self ability the player can afford, in the hand
     * holding the glove; a player short of the cost starts no eat.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     */
    static void beginEating(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        if (!affords(player, gooType, ability)) {
            return;
        }
        player.startUsingItem(gloveHand(player));
    }

    /**
     * Finishes an eat of the glove on the server: the glove's selection is
     * resolved again, and an ability the player may still use whose
     * delivery takes the eat route drains and runs.
     *
     * @param player the eating player
     * @param glove  the glove eaten
     */
    public static void finishEating(ServerPlayer player, ItemStack glove) {
        GloveSelection selection = GooGloveItem.getSelection(glove);
        ResourceKey<GooTypeDefinition> gooType = selection == null ? null : selection.getGooType();
        if (gooType == null) {
            return;
        }
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, selection.abilityId(), gooType);
        if (ability == null) {
            return;
        }
        SelfEatRoute.finish(ability.delivery(), () -> invoke(player, gooType, ability));
    }

    /**
     * Drains a self ability's cost and runs its programs on the player, when
     * the player holds its cost.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     */
    static void invoke(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        if (!affords(player, gooType, ability)) {
            return;
        }
        GooSourceScanner.deplete(player, gooType, ability.cost());
        try {
            ProgramBehavior.forHost(ability.behaviors(), HostKind.PLAYER).tick(new PlayerHost(player.level(), player));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
        }
    }

    /**
     * Whether the player holds the ability's cost, logging the refusal.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     * @return true when the inventory covers the cost
     */
    private static boolean affords(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        if (GooSourceScanner.hasEnough(player, gooType, ability.cost())) {
            return true;
        }
        if (Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_NO_GOO, ability.id());
        }
        return false;
    }

    /**
     * The hand holding the glove, main hand first.
     *
     * @param player the invoking player
     * @return the glove's hand
     */
    private static InteractionHand gloveHand(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof GooGloveItem
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
    }
}
