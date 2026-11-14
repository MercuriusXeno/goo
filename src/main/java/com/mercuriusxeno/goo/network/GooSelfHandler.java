package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.DurativeRoute;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepContext;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;

/**
 * Server side of a self delivery: nothing leaves the hand. A self ability
 * wearing the self badge runs on command: its cost at stack zero drains and
 * its programs run on the invoking player the tick it is invoked. A durative
 * ability, one wearing the durative badge, arrives once its charge is let
 * go: its programs run and the effect is held, paying its upkeep each tick
 * after, and the players watching see the caster's lifted blob spent.
 * Invoking a held effect again ends it.
 * decision self-delivery-runs-on-player
 * decision self-effects-trickle-until-ended
 * decision durative-self-abilities-wash-over-the-view-in-place-of-eating
 */
public final class GooSelfHandler {

    private static final String LOG_NO_GOO = "Self ability {} rejected: insufficient goo";
    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on the player host: {}";
    private static final String LOG_COMBO_REFUSED = "Ability {} refused as a prism's combo: {}";

    private GooSelfHandler() {
    }

    /**
     * Delivers a self ability: a durative ability is held, a
     * channel runs only through its held ticks and so nothing here
     * (decision flatten-disc-cursor-breaks-above-the-plane), every other
     * runs on command with the throw sound.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     */
    static void deliver(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        if (HeldRoute.channelsOnSelf(ability.delivery(), ability.badge()) || ability.delivery().charges()) {
            return;
        }
        boolean held = HeldEffectsEvents.holds(player, ability.id());
        if (DurativeRoute.endsHeld(ability.delivery(), ability.badge(), held)) {
            HeldEffectsEvents.end(player, ability.id());
        } else if (DurativeRoute.isDurative(ability.delivery(), ability.badge()) && hold(player, gooType, ability)) {
            // durative-self-abilities-wash-over-the-view-in-place-of-eating: the watchers see the blob spent
            PalmBlobChargePayload.sendSpent(player);
        } else if (invoke(new PlayerHost(player.level(), player), gooType, ability) && !playsItsOwnSound(ability)) {
            GooEffectScheduler.playThrowSound(player, ability.delivery());
        }
    }

    /**
     * Fires a charged self ability let go after a hold: its programs run on
     * the player carrying the share of a full charge the hold reached.
     * nova-ring-grows-with-the-hold
     *
     * @param player    the releasing player
     * @param gooType   the ability's goo type
     * @param ability   the charged ability
     * @param heldTicks the ticks the use key was held
     */
    static void release(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability,
            int heldTicks) {
        if (ability.delivery().kind() != DeliveryKind.SELF || !ability.delivery().charges()) {
            return;
        }
        PlayerHost host = PlayerHost.charged(player.level(), player, ability.delivery().chargeShare(heldTicks));
        if (invoke(host, gooType, ability) && !playsItsOwnSound(ability)) {
            GooEffectScheduler.playThrowSound(player, ability.delivery());
        }
    }

    /**
     * Whether a self ability's program plays its own sound, which stands in
     * for the snowball throw a thrown goo makes: nothing leaves the hand.
     *
     * @param ability the self ability
     * @return true when a sound step runs in its program
     */
    static boolean playsItsOwnSound(AbilityDefinition ability) {
        return ability.behaviors().stream().anyMatch(SoundStep.class::isInstance);
    }

    /**
     * Starts a held durative effect with no one-shot drain and runs its
     * programs on the player, when the player holds a tick's upkeep. The
     * effect is held before the programs run, so a heart-changing effect it
     * replaces clears before its own hearts lay.
     * self-effects-trickle-until-ended
     *
     * @param player  the casting player
     * @param gooType the ability's goo type
     * @param ability the durative ability
     * @return true when the effect is held and its programs ran
     */
    private static boolean hold(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        if (!affords(player, gooType, ability)) {
            return false;
        }
        ReagentScanner.consumeOneOfEach(player, ability.consumes());
        HeldEffectsEvents.start(player, gooType, ability);
        runOn(new PlayerHost(player.level(), player), ability);
        return true;
    }

    /**
     * Drains a self ability's cost and runs its programs on the player, when
     * the player holds its cost. A blink's cost is priced from the trip it
     * is about to make, before it runs.
     * decision blink-lands-safely-costed-by-distance
     *
     * @param host    the host over the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     * @return true when the cost drained and the programs ran
     */
    private static boolean invoke(PlayerHost host, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability) {
        ServerPlayer player = host.player();
        Optional<BlinkLanding> trip = TeleportStep.tripOf(ability.behaviors(), player, player.position(),
                player.getLookAngle());
        GooFraction cost = ability.distancePrice().exactPriceOf(ability.cost().exactIn(GooValues.of(player.level())),
                trip);
        if (!affords(player, gooType, ability, cost)) {
            return false;
        }
        if (!admits(host, ability)) {
            playRefusal(host, ability);
            return false;
        }
        AbilityCharges.charge(player, gooType, cost);
        ReagentScanner.consumeOneOfEach(player, ability.consumes());
        Optional<BlockPos> converting = trip.flatMap(BlinkLanding::converts);
        if (converting.isPresent()) {
            spendInto(player, gooType, ability, converting.get());
        } else {
            runOn(host, ability);
        }
        return true;
    }

    /**
     * Spends a blink into a bare prism: the blinker stays, and the prism
     * runs the blink's on_prism reaction as its combo, becoming an oculus.
     * blink-at-a-prism-spends-into-an-oculus
     *
     * @param player  the blinking player
     * @param gooType the blink's goo type
     * @param ability the blink ability
     * @param prism   the bare prism's block
     */
    private static void spendInto(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability, BlockPos prism) {
        if (player.level().getBlockEntity(prism) instanceof PrismBlockEntity bare) {
            try {
                bare.runCombo(gooType, ability.id().toString(), PrismCombos.comboSteps(ability), player.getUUID());
            } catch (ProgramLoadException e) {
                Goo.LOGGER.error(LOG_COMBO_REFUSED, ability.id(), e.getMessage());
            }
        }
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
     * Plays the refusal sound the first refusing step names, if it names
     * one; Fuse with no pair fizzles (decision fuse-two-books-for-hex-goo).
     *
     * @param host    the player host
     * @param ability the refused self ability
     */
    private static void playRefusal(PlayerHost host, AbilityDefinition ability) {
        StepContext context = new StepContext(host, 0, 0);
        ability.behaviors().stream().filter(step -> !step.admits(context)).findFirst()
                .flatMap(Step::refusal).ifPresent(host::playSound);
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
        return affords(player, gooType, ability, ability.cost().exactIn(GooValues.of(player.level())));
    }

    /**
     * Whether the player holds a priced cost, or a tick's upkeep for a held
     * effect, logging the refusal.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the self ability
     * @param cost    the exact cost this invocation drains, the player's remainder paying first
     * @return true when the inventory covers the cost
     */
    private static boolean affords(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
            AbilityDefinition ability, GooFraction cost) {
        GooFraction upkeep = ability.upkeep().exactIn(GooValues.of(player.level()));
        // ability-json-names-its-reagent
        if (AbilityCharges.affords(player, gooType, cost.compareTo(upkeep) >= 0 ? cost : upkeep)
                && ReagentScanner.holdsEvery(player, ability.consumes())) {
            return true;
        }
        if (Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_NO_GOO, ability.id());
        }
        return false;
    }
}
