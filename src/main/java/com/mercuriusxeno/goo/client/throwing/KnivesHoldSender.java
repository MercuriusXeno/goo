package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.client.ability.HoveringKnives;
import com.mercuriusxeno.goo.network.KnivesHoldPayload;
import com.mercuriusxeno.goo.network.KnivesThrowPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Tells the server when the local player's Knives hold begins and ends, so
 * the server's stage clock forms the knives: a hold begins with the press's
 * first held tick and ends as right click comes up or the press drops. While
 * the hold stands, each mob the outline picks is painted, and letting go
 * clears the paint.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-form-in-fibonacci-stages-to-twelve
 * decision knives-paint-mobs-by-hovering-while-forming
 */
public final class KnivesHoldSender {

    /** The selection whose hold the server was told began, or null where none stands. */
    private static @Nullable GloveSelection begun;
    /** The mobs the standing hold has painted. */
    private static final PaintedMobs PAINTED = new PaintedMobs();

    private KnivesHoldSender() {
    }

    /**
     * Whether a player's glove selection forms knives in stages on a held press.
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
                && delivery.charge().formsInStages();
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
     * Paints the mob the outline picks this tick while a hold stands, first
     * dropping each painted mob no longer standing.
     *
     * @param hovered the outlined mob's entity id, empty for none
     * @param stands  whether the mob of an id still stands on this client
     */
    static void paint(OptionalInt hovered, IntPredicate stands) {
        if (begun != null) {
            PAINTED.dropGone(stands);
            PAINTED.hovered(hovered);
        }
    }

    /**
     * Throws the knives in the local player's fan at the mobs the hold
     * painted, in painted order, and clears the paint; the server splits them.
     * decision knives-form-on-right-click-and-throw-on-left-click
     *
     * @param player the local player
     * @return true where a hold stood with a knife in its fan
     */
    static boolean throwKnives(Player player) {
        if (begun == null || HoveringKnives.formedBy(player.getId()) <= 0) {
            return false;
        }
        GloveThrowSender.sendPayload(new KnivesThrowPayload(PAINTED.painted()));
        PAINTED.thrown();
        return true;
    }

    /**
     * Whether the standing hold has painted an entity, which wears the outlined mob's highlight.
     *
     * @param entity the entity
     * @return true for a painted mob while the hold stands
     */
    public static boolean paints(Entity entity) {
        return begun != null && PAINTED.paints(entity.getId());
    }

    /** Tells the server the hold it was told of ends, its knives gone and none thrown, its paint cleared. */
    static void end() {
        PAINTED.dismissed();
        if (begun != null) {
            GloveThrowSender.sendPayload(new KnivesHoldPayload(false, begun.gooTypeId(), begun.abilityId()));
            begun = null;
        }
    }
}
