package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
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
import java.util.OptionalInt;

/**
 * Server side of a self delivery: nothing leaves the hand. A self ability
 * wearing the self badge runs on command: its cost at stack zero drains and
 * its programs run on the invoking player the tick it is invoked. A self +
 * brew ability, one wearing the brew badge, starts the player eating the
 * glove instead, and drains and runs when the eat finishes; an eat let go or
 * interrupted before then runs nothing and drains nothing, and the eat
 * replaces the throw sound on that route.
 * decision self-delivery-runs-on-player
 * decision self-brew-goos-eat-before-the-effect
 */
public final class GooSelfHandler {

    private static final String LOG_NO_GOO = "Self ability {} rejected: insufficient goo";
    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on the player host: {}";

    private GooSelfHandler() {
    }

    /**
     * Delivers a self ability: a self + brew ability starts the eat, every
     * other runs on command with the throw sound.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     */
    static void deliver(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        if (SelfEatRoute.eats(ability.delivery(), ability.badge())) {
            beginEating(player, gooType, ability);
        } else if (invoke(player, gooType, ability)) {
            GooEffectScheduler.playThrowSound(player, ability.delivery());
        }
    }

    /**
     * Starts the eat for a self + brew ability the player can afford, in the
     * hand holding the glove; a player short of the cost starts no eat.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self + brew ability
     */
    private static void beginEating(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        if (affords(player, gooType, ability)) {
            player.startUsingItem(gloveHand(player));
        }
    }

    /**
     * Finishes an eat of the glove on the server: the glove's selection is
     * resolved again, and an ability the player may still use that takes the
     * eat route drains and runs.
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
        SelfEatRoute.finish(ability.delivery(), ability.badge(), () -> invoke(player, gooType, ability));
    }

    /**
     * Drains a self ability's cost and runs its programs on the player, when
     * the player holds its cost.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     * @return true when the cost drained and the programs ran
     */
    private static boolean invoke(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        if (!affords(player, gooType, ability)) {
            return false;
        }
        GooSourceScanner.deplete(player, gooType, ability.cost());
        runOn(new PlayerHost(player.level(), player), ability);
        return true;
    }

    /**
     * Runs a drunk brew: the type's brew ability runs on the player for the
     * brew's duration, the same program the glove runs, with no goo drained.
     * A type with no brew ability yet runs nothing.
     * decision brew-grants-the-self-ability-for-an-hour
     *
     * @param player   the drinking player
     * @param gooType  the brew's goo type
     * @param duration the brew's duration in ticks
     */
    public static void drinkBrew(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, int duration) {
        AbilityDefinition ability = AbilityRegistry.of(player.level()).brewAbilityFor(gooType);
        if (ability != null) {
            runOn(new PlayerHost(player.level(), player, OptionalInt.of(duration)), ability);
        }
    }

    /**
     * Runs an ability's programs on a player host, logging a program the host refuses.
     *
     * @param host    the player host
     * @param ability the ability run
     */
    private static void runOn(PlayerHost host, AbilityDefinition ability) {
        try {
            ProgramBehavior.forHost(ability.behaviors(), HostKind.PLAYER).tick(host);
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
