package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.SelfEatRoute;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.ability.oculus.OculusCharge;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.StepContext;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Server side of a self delivery: nothing leaves the hand. A self ability
 * wearing the self badge runs on command: its cost at stack zero drains and
 * its programs run on the invoking player the tick it is invoked. A self +
 * brew ability, one wearing the brew badge, starts the player eating the
 * glove instead, and when the eat finishes its programs run and the effect is
 * held, paying its upkeep each tick after; an eat let go or interrupted
 * before then runs nothing and drains nothing, and the eat replaces the throw
 * sound on that route. Invoking a held effect again ends it.
 * decision self-delivery-runs-on-player
 * decision self-brew-goos-eat-before-the-effect
 * decision self-effects-trickle-until-ended
 */
public final class GooSelfHandler {

    private static final String LOG_NO_GOO = "Self ability {} rejected: insufficient goo";
    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on the player host: {}";

    private GooSelfHandler() {
    }

    /**
     * Delivers a self ability: a self + brew ability starts the eat, a
     * channel runs only through its held ticks and so nothing here
     * (decision flatten-disc-cursor-breaks-above-the-plane), every other
     * runs on command with the throw sound.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     * @param pin     the face plane the press pinned, which a blink slides on; empty for free aim
     */
    static void deliver(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability,
            Optional<ChannelAim.FacePlane> pin) {
        if (HeldRoute.channelsOnSelf(ability.delivery(), ability.badge())) {
            return;
        }
        boolean held = HeldEffectsEvents.holds(player, ability.id());
        if (SelfEatRoute.endsHeld(ability.delivery(), ability.badge(), held)) {
            HeldEffectsEvents.end(player, ability.id());
        } else if (SelfEatRoute.eats(ability.delivery(), ability.badge())) {
            beginEating(player, gooType, ability);
        } else if (invoke(player, gooType, ability, pin)) {
            GooEffectScheduler.playThrowSound(player, ability.delivery());
        }
    }

    /**
     * Starts the eat for a self + brew ability the player can afford, in the
     * hand holding the glove; a player short of a tick's upkeep starts no eat.
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
     * eat route is held and runs.
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
        SelfEatRoute.finish(ability.delivery(), ability.badge(), () -> hold(player, gooType, ability));
    }

    /**
     * Starts a held self + brew effect with no one-shot drain and runs its
     * programs on the player, when the player holds a tick's upkeep. The
     * effect is held before the programs run, so a heart-changing effect it
     * replaces clears before its own hearts lay.
     * self-effects-trickle-until-ended
     *
     * @param player  the eating player
     * @param gooType the ability's goo type
     * @param ability the self + brew ability
     */
    private static void hold(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        if (!affords(player, gooType, ability)) {
            return;
        }
        ReagentScanner.consumeOneOfEach(player, ability.consumes());
        HeldEffectsEvents.start(player, gooType, ability);
        runOn(new PlayerHost(player.level(), player), ability);
    }

    /**
     * Drains a self ability's cost and runs its programs on the player, when
     * the player holds its cost. A blink's cost is priced from the trip it
     * is about to make, before it runs.
     * decision blink-lands-safely-costed-by-distance
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     * @param pin     the face plane the press pinned, empty for free aim
     * @return true when the cost drained and the programs ran
     */
    private static boolean invoke(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability, Optional<ChannelAim.FacePlane> pin) {
        PlayerHost host = PlayerHost.blinking(player.level(), player, pin);
        Optional<BlinkLanding> trip = TeleportStep.tripOf(ability.behaviors(), player, player.position(),
                player.getLookAngle(), pin);
        int price = ability.distancePrice().priceOf(ability.cost(), trip);
        // oculus-prism-becomes-a-hovering-eye: a charged oculus pays for the blink to it
        int cost = OculusCharge.costAt(player.level(), price, trip);
        if (!affords(player, gooType, ability, cost) || !admits(host, ability)) {
            return false;
        }
        GooSourceScanner.deplete(player, gooType, cost);
        ReagentScanner.consumeOneOfEach(player, ability.consumes());
        runOn(host, ability);
        OculusCharge.settle(player.level(), price, trip);
        return true;
    }

    /**
     * Whether every step of the ability can act on the player now; Fungal
     * Shift aimed at no fungus cannot, and so neither runs nor drains
     * (decision fungal-shift-blinks-to-the-aimed-fungus).
     *
     * @param host    the player host
     * @param ability the self ability
     * @return true when every step admits
     */
    private static boolean admits(PlayerHost host, AbilityDefinition ability) {
        StepContext context = new StepContext(host, 0, 0);
        return ability.behaviors().stream().allMatch(step -> step.admits(context));
    }

    /**
     * Runs a drunk brew: the type's brew ability starts prepaid, the same
     * held effect and program the glove runs, for the brew's duration with no
     * goo drained. A type with no brew ability yet runs nothing.
     * decision brew-grants-the-self-ability-for-an-hour
     * decision brew-runs-the-crawl-prepaid-on-a-shown-clock
     *
     * @param player   the drinking player
     * @param gooType  the brew's goo type
     * @param duration the brew's duration in ticks
     */
    public static void drinkBrew(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, int duration) {
        AbilityDefinition ability = AbilityRegistry.of(player.level()).brewAbilityFor(gooType);
        if (ability != null) {
            HeldEffectsEvents.startPrepaid(player, gooType, ability, duration);
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
     * Whether the player holds the ability's cost, or a tick's upkeep for a
     * held effect, logging the refusal.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     * @return true when the inventory covers the cost
     */
    private static boolean affords(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        return affords(player, gooType, ability, ability.cost());
    }

    /**
     * Whether the player holds a priced cost, or a tick's upkeep for a held
     * effect, logging the refusal.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     * @param cost    the cost this invocation drains
     * @return true when the inventory covers the cost
     */
    private static boolean affords(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability, int cost) {
        // ability-json-names-its-reagent
        if (GooSourceScanner.hasEnough(player, gooType, Math.max(cost, ability.upkeep()))
                && ReagentScanner.holdsEvery(player, ability.consumes())) {
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
