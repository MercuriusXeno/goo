package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.network.KnivesHoldPayload;
import com.mercuriusxeno.goo.network.KnivesThrowPayload;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Tells the server when the local player's Knives hold begins and ends, so
 * the server's clock forms the knives: a hold begins with the press's
 * first held tick and ends as right click comes up or the press drops.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-charge-one-per-three-ticks-up-to-ten
 */
public final class KnivesHoldSender {

    /** The selection whose hold the server was told began, or null where none stands. */
    private static @Nullable GloveSelection begun;
    /** Whether the server was told left click went down and not yet that it lifted. */
    private static boolean throwing;

    private KnivesHoldSender() {
    }

    /**
     * Whether a player's glove selection forms knives on a held press.
     *
     * @param player the player
     * @return true for a held throw whose charge forms knives
     */
    public static boolean selectedFormsKnives(Player player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        return selection != null && formsKnives(selection);
    }

    private static boolean formsKnives(GloveSelection selection) {
        Delivery delivery = GloveThrowSender.selectedDelivery(selection.abilityId());
        return HeldRoute.throwsOnAttack(delivery, GloveThrowSender.selectedBadge(selection.abilityId()))
                && delivery.charge().formsKnives();
    }

    /**
     * Tells the server a hold begins, for a selection forming knives.
     *
     * @param player the local player
     */
    static void begin(Player player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        if (selection == null || selection.getGooType() == null || !formsKnives(selection)) {
            return;
        }
        GloveThrowSender.sendPayload(new KnivesHoldPayload(true, selection.gooTypeId(), selection.abilityId()));
        begun = selection;
    }

    /**
     * Tells the server left click went down during the local player's hold,
     * which throws a knife at once and one every 2 ticks after until it lifts;
     * the server launches each, aimed at no mob.
     * decision knives-throw-one-per-two-ticks-while-left-click-is-held
     * decision knives-do-not-seek-you-just-throw-them
     *
     * @return true where a hold stood
     */
    static boolean throwKnives() {
        if (begun == null) {
            return false;
        }
        GloveThrowSender.sendPayload(new KnivesThrowPayload(true));
        throwing = true;
        return true;
    }

    /**
     * Tells the server left click lifted, once the attack key the throw went
     * down with is up.
     * decision knives-throw-one-per-two-ticks-while-left-click-is-held
     *
     * @param attackDown whether the attack key is down this tick
     */
    static void watchTheLeftClick(boolean attackDown) {
        if (throwing && !attackDown) {
            GloveThrowSender.sendPayload(new KnivesThrowPayload(false));
            throwing = false;
        }
    }

    /** Tells the server the hold it was told of ends, its knives gone and none thrown. */
    static void end() {
        throwing = false;
        if (begun != null) {
            GloveThrowSender.sendPayload(new KnivesHoldPayload(false, begun.gooTypeId(), begun.abilityId()));
            begun = null;
        }
    }
}
