package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.client.ability.HoveringKnives;
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
     * Throws a knife of the local player's hold along its look; the server
     * launches it, aimed at no mob.
     * decision knives-form-on-right-click-and-throw-on-left-click
     * decision knives-do-not-seek-you-just-throw-them
     *
     * @param player the local player
     * @return true where a hold stood with a knife held
     */
    static boolean throwKnives(Player player) {
        if (begun == null || HoveringKnives.formedBy(player.getId()) <= 0) {
            return false;
        }
        GloveThrowSender.sendPayload(KnivesThrowPayload.INSTANCE);
        return true;
    }

    /** Tells the server the hold it was told of ends, its knives gone and none thrown. */
    static void end() {
        if (begun != null) {
            GloveThrowSender.sendPayload(new KnivesHoldPayload(false, begun.gooTypeId(), begun.abilityId()));
            begun = null;
        }
    }
}
