package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.GooTouchHandler;
import com.mercuriusxeno.goo.network.GooTouchHandler.AttackPress;
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
 * in the same press; every other attack-key press stays vanilla.
 * decision attack-key-touches-plus-punches
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveAttackTracker {

    private GloveAttackTracker() {
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
        if (GooTouchHandler.attackTouches(pressOf(player, player.getItemInHand(event.getHand())))) {
            GloveThrowSender.sendThrow(player);
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
