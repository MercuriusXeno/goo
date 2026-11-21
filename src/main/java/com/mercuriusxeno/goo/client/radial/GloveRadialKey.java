package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * The glove menu key, G by default, bound only while a caster is held, a
 * glove or raw goo held bare handed, or a sprayer: its press opens
 * {@link GloveRadialScreen}, or the sprayer's {@link SprayerRadialScreen}, which read the release themselves.
 * decision g-opens-radial-while-glove-held
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveRadialKey {

    /** The goo key category, holding the glove menu and Goo values keys. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(Goo.MODID, GloveRadialKeyGate.CATEGORY_PATH));

    private static final GloveHeldConflictContext GLOVE_HELD =
            new GloveHeldConflictContext(GloveRadialKey::localPlayerOpensAWheelFrom);

    /** The glove menu mapping. */
    public static final KeyMapping MAPPING = new KeyMapping(GloveRadialKeyGate.NAME_KEY, GLOVE_HELD,
            InputConstants.Type.KEYBOARD, GloveRadialKeyGate.DEFAULT_KEY, CATEGORY);

    private GloveRadialKey() {}

    /**
     * Registers the glove menu category and mapping.
     *
     * @param event the key mapping registration
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(MAPPING);
    }

    /**
     * Opens the radial for a press of the glove menu key.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (MAPPING.consumeClick()) {
            // sprayer-wheel-picks-nozzle-flow-and-goo-type: the held sprayer opens its own wheel
            switch (wheelOpenedBy(mc.player, mc.gui.screen() != null)) {
                case SPRAYER -> SprayerRadialScreen.open();
                case GLOVE -> GloveRadialScreen.open();
                case NONE -> { }
            }
        }
    }

    /**
     * The wheel a press opens for what the local player holds.
     *
     * @param player     the local player, or null
     * @param screenOpen whether a screen is open
     * @return the wheel the press opens
     */
    private static GloveRadialKeyGate.Wheel wheelOpenedBy(LocalPlayer player, boolean screenOpen) {
        if (player == null) {
            return GloveRadialKeyGate.Wheel.NONE;
        }
        return GloveRadialKeyGate.wheelOpened(player.getMainHandItem().getItem() instanceof SprayerItem,
                GooCasters.casterHand(player) != null, screenOpen);
    }

    /**
     * Whether a hand holds what opens a wheel: the player's caster, a glove or raw goo
     * held bare handed, or, in the main hand, a sprayer.
     * decision raw-goo-items-work-bare-handed-for-the-held-type
     * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
     *
     * @param hand the hand
     * @return true where the hand holds what opens a wheel
     */
    private static boolean localPlayerOpensAWheelFrom(InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (GooCasters.casterHand(player) == hand
                || hand == InteractionHand.MAIN_HAND && player.getMainHandItem().getItem() instanceof SprayerItem);
    }
}
