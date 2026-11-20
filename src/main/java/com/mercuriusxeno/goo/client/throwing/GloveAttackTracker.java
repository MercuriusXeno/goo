package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveButton;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.ability.program.PortalStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooCasters;
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
 * Client-side tracker for the glove's attack key. With Portal selected the
 * press shoots the blue portal, the attack key held down shooting once.
 * While right click holds Dart's javelin, the press throws it once whole.
 * While a glove press holds a Grab, the press throws the held entity. While
 * a charge stands, the press cancels it. Every other press is vanilla's: on
 * a mob it swings the arm and lands the melee hit.
 * decision attack-key-touches-plus-punches
 * decision dart-forms-on-right-click-and-throws-on-left-click
 * decision grab-holds-and-throws-a-physics-body
 * decision abilities-not-channeled-charge-and-cancel-on-left-click
 * decision portal-takes-over-left-and-right-click-for-blue-and-orange
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveAttackTracker {

    private GloveAttackTracker() {
    }

    /** What an attack-key press does with the glove. */
    enum AttackOutcome {
        /** Shoots the blue portal. */
        SHOOT_PORTAL,
        /** Throws the javelin right click holds, once whole. */
        THROW_JAVELIN,
        /** Throws the entity a channeled Grab holds. */
        THROW_GRAB,
        /** Cancels the standing charge. */
        CANCEL_CHARGE,
        /** Leaves the press to vanilla, the melee hit on a mob. */
        VANILLA
    }

    /**
     * What an attack-key press does: with Portal selected it shoots the blue
     * portal whatever else stands, a javelin right click holds is thrown,
     * a channeled Grab throws its held mob, a charge standing cancels, and
     * any other press is vanilla's.
     * decision dart-forms-on-right-click-and-throws-on-left-click
     * decision portal-takes-over-left-and-right-click-for-blue-and-orange
     *
     * @param shootsPortal  whether the pressing hand's selection is Portal
     * @param holdsAJavelin whether right click holds a javelin forming or formed
     * @param charging      whether a charge stands, a press that is not channeled previewing
     * @param grabThrows    whether a channeled Grab holds a mob to throw
     * @return the outcome
     */
    static AttackOutcome outcomeOf(boolean shootsPortal, boolean holdsAJavelin, boolean charging,
                                   boolean grabThrows) {
        if (shootsPortal) {
            return AttackOutcome.SHOOT_PORTAL;
        }
        if (holdsAJavelin) {
            return AttackOutcome.THROW_JAVELIN;
        }
        if (grabThrows) {
            return AttackOutcome.THROW_GRAB;
        }
        if (charging) {
            return AttackOutcome.CANCEL_CHARGE;
        }
        return AttackOutcome.VANILLA;
    }

    /**
     * Carries out an attack-key press made with the glove.
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
        ClientAbility selected = selectedAbility(player, pressingHand);
        boolean grabThrows = selected != null
                && GrabStep.throwsOnAttack(GloveUseTracker.showsArea(), selected.behaviors());
        boolean shootsPortal = selected != null && PortalStep.placesPortals(selected.behaviors());
        resolve(event, player, outcomeOf(shootsPortal, GloveUseTracker.holdsAJavelin(player), GloveUseTracker.charging(),
                grabThrows));
    }

    /**
     * Carries out what an attack-key press does.
     *
     * @param event   the key interaction
     * @param player  the local player
     * @param outcome what the press does
     */
    private static void resolve(InputEvent.InteractionKeyMappingTriggered event, LocalPlayer player,
                                AttackOutcome outcome) {
        if (outcome == AttackOutcome.SHOOT_PORTAL) {
            shootThePortal(event, player);
        } else {
            resolveTheGlovePress(event, player, outcome);
        }
    }

    /**
     * Shoots the blue portal on a fresh press, taking the press from vanilla
     * whether it shoots or not.
     * portal-takes-over-left-and-right-click-for-blue-and-orange
     *
     * @param event  the key interaction
     * @param player the local player
     */
    private static void shootThePortal(InputEvent.InteractionKeyMappingTriggered event, LocalPlayer player) {
        event.setCanceled(true);
        event.setSwingHand(false);
        PortalAim.shoot(player, GloveButton.ATTACK);
    }

    /**
     * Carries out what an attack-key press does with any selection but Portal.
     *
     * @param event   the key interaction
     * @param player  the local player
     * @param outcome what the press does
     */
    private static void resolveTheGlovePress(InputEvent.InteractionKeyMappingTriggered event, LocalPlayer player,
                                             AttackOutcome outcome) {
        switch (outcome) {
            case THROW_JAVELIN -> {
                // dart-forms-on-right-click-and-throws-on-left-click: the click throws the javelin whole in the hand
                event.setCanceled(true);
                event.setSwingHand(false);
                GloveUseTracker.throwTheJavelin(player);
            }
            case THROW_GRAB -> {
                // grab-holds-and-throws-a-physics-body: the click throws the held entity
                event.setCanceled(true);
                GloveThrowSender.sendPayload(GrabThrowPayload.INSTANCE);
            }
            case CANCEL_CHARGE -> {
                event.setCanceled(true);
                event.setSwingHand(false);
                GloveUseTracker.cancelCharge(player);
            }
            case SHOOT_PORTAL, VANILLA -> {
                // the press stays vanilla's; resolve shoots Portal before this
            }
        }
    }

    /**
     * Whether the attack key presses with the player's caster, the glove or
     * raw goo held bare handed.
     * decision raw-goo-items-work-bare-handed-for-the-held-type
     *
     * @param player       the local player
     * @param pressingHand the stack in the pressing hand
     * @return true where the pressing hand holds the caster
     */
    private static boolean castsFrom(LocalPlayer player, ItemStack pressingHand) {
        return pressingHand == GooCasters.casterOf(player);
    }

    private static @Nullable ClientAbility selectedAbility(LocalPlayer player, ItemStack pressingHand) {
        if (!castsFrom(player, pressingHand)) {
            return null;
        }
        GloveSelection selection = GooCasters.selectionOf(pressingHand);
        return selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
    }
}
