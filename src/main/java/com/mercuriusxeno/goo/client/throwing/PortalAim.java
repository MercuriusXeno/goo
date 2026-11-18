package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveButton;
import com.mercuriusxeno.goo.ability.program.PortalStep;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Portal in the glove: left click shoots the blue portal and right click the
 * orange one, each the moment its button goes down, once a press however
 * long the button is held, the palm blob hopping as it shoots.
 * portal-takes-over-left-and-right-click-for-blue-and-orange
 * operator UAT 2026-11-17: both buttons fire on press, each hopping the palm blob as it shoots
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PortalAim {

    /** Whether the attack key stood down as the last client tick ended. */
    private static boolean attackDownLastTick;
    /** Whether the use key stood down as the last client tick ended. */
    private static boolean useDownLastTick;

    private PortalAim() {
    }

    /**
     * Whether the selected ability places portals.
     *
     * @param abilityId the selected ability id string
     * @return true for Portal
     */
    public static boolean selected(@Nullable String abilityId) {
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability != null && PortalStep.placesPortals(ability.behaviors());
    }

    /**
     * Whether a button's interaction shoots Portal: a fresh press does, and
     * the interaction vanilla repeats while the button stays down does not.
     *
     * @param downLastTick whether the button stood down as the last tick ended
     * @return true for a fresh press
     */
    static boolean shootsOnThisPress(boolean downLastTick) {
        return !downLastTick;
    }

    /**
     * Shoots Portal by a button on a fresh press, hopping the palm blob as
     * the shot leaves it.
     *
     * @param player the local player
     * @param button the button pressed
     */
    public static void shoot(LocalPlayer player, GloveButton button) {
        boolean downLastTick = button == GloveButton.ATTACK ? attackDownLastTick : useDownLastTick;
        if (shootsOnThisPress(downLastTick) && GloveThrowSender.sendPortalShot(player, button)) {
            LiftedPalmBlob.held(player.getId(), player.getEyePosition(), player.level().getGameTime());
        }
    }

    /**
     * Notes whether each button stands down as the client tick ends.
     *
     * @param event the client's tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        attackDownLastTick = mc.options.keyAttack.isDown();
        useDownLastTick = mc.options.keyUse.isDown();
    }
}
