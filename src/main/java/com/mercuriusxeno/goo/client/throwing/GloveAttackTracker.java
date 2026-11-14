package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.GooTouchHandler;
import com.mercuriusxeno.goo.network.GooTouchHandler.AttackPress;
import com.mercuriusxeno.goo.network.GrabThrowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-side tracker for the glove's attack key: a press on an entity
 * within reach, with a mob ability selected on the glove in the pressing
 * hand, sends the touch the use key sends. The event is never canceled and
 * its swing never cleared, so vanilla swings the arm and lands the melee hit
 * in the same press; every other attack-key press stays vanilla. While a
 * glove press holds a Grab, the press throws the held entity instead and
 * lands no punch, and while a charge stands it cancels the charge, landing
 * no punch and no touch.
 * decision attack-key-touches-plus-punches
 * decision grab-holds-and-throws-a-physics-body
 * decision abilities-not-channeled-charge-and-cancel-on-left-click
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveAttackTracker {

    private GloveAttackTracker() {
    }

    /** What an attack-key press does with the glove. */
    enum AttackOutcome {
        /** Throws the entity a channeled Grab holds, landing no punch. */
        THROW_GRAB,
        /** Cancels a charge standing, sending nothing and landing no punch. */
        CANCEL_CHARGE,
        /** Sends the touch the use key sends, beside vanilla's punch. */
        TOUCH,
        /** Leaves the press to vanilla. */
        VANILLA
    }

    /**
     * What an attack-key press does: a channeled Grab throws its held mob, a
     * charge standing cancels, a press that touches touches, and any other
     * stays vanilla.
     *
     * @param charging   whether a charge stands, a press that is not channeled previewing
     * @param grabThrows whether a channeled Grab holds a mob to throw
     * @param touches    whether the press touches what it aims at
     * @return the outcome
     */
    static AttackOutcome outcomeOf(boolean charging, boolean grabThrows, boolean touches) {
        if (grabThrows) {
            return AttackOutcome.THROW_GRAB;
        }
        if (charging) {
            return AttackOutcome.CANCEL_CHARGE;
        }
        return touches ? AttackOutcome.TOUCH : AttackOutcome.VANILLA;
    }

    /**
     * Sends the touch on an attack-key press that touches.
     *
     * @param event the key interaction
     */
    @SubscribeEvent
    public static void onAttackKeyInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isAttack() || player == null) {
            return;
        }
        ItemStack pressingHand = player.getItemInHand(event.getHand());
        ClientAbility selected = selectedAbility(pressingHand);
        boolean grabThrows = selected != null
                && GrabStep.throwsOnAttack(GloveUseTracker.showsArea(), selected.behaviors());
        switch (outcomeOf(GloveUseTracker.charging(), grabThrows,
                GooTouchHandler.attackTouches(pressOf(player, pressingHand)))) {
            case THROW_GRAB -> {
                // grab-holds-and-throws-a-physics-body: the click throws the held entity instead of punching it
                event.setCanceled(true);
                GloveThrowSender.sendPayload(GrabThrowPayload.INSTANCE);
            }
            case CANCEL_CHARGE -> {
                event.setCanceled(true);
                event.setSwingHand(false);
                GloveUseTracker.cancelCharge(player);
            }
            case TOUCH -> GloveThrowSender.sendThrow(player, 0);
            case VANILLA -> {
                // the press stays vanilla's
            }
        }
    }

    /**
     * Reads what an attack-key press aims with off the pressing hand and
     * the frame's aim.
     *
     * @param player       the local player
     * @param pressingHand the stack in the hand the attack key presses with
     * @return the press
     */
    private static AttackPress pressOf(LocalPlayer player, ItemStack pressingHand) {
        ClientAbility selected = selectedAbility(pressingHand);
        TargetResult target = AimTracker.currentTarget();
        return new AttackPress(pressingHand.getItem() instanceof GooGloveItem,
                selected == null ? null : selected.delivery(), selected == null ? null : selected.badge(),
                target instanceof TargetResult.EntityTarget et ? et.entity() : null,
                player.position(), player.entityInteractionRange());
    }

    private static @Nullable ClientAbility selectedAbility(ItemStack pressingHand) {
        if (!(pressingHand.getItem() instanceof GooGloveItem)) {
            return null;
        }
        GloveSelection selection = GooGloveItem.getSelection(pressingHand);
        return selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
    }
}
