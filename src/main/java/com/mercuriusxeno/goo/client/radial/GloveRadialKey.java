package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.GooCasters;
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
 * glove or raw goo held bare handed: its press opens
 * {@link GloveRadialScreen}, which reads the release itself.
 * decision g-opens-radial-while-glove-held
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveRadialKey {

    /** The goo key category, holding the glove menu and Goo values keys. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(Goo.MODID, GloveRadialKeyGate.CATEGORY_PATH));

    private static final GloveHeldConflictContext GLOVE_HELD =
            new GloveHeldConflictContext(GloveRadialKey::localPlayerCastsFrom);

    /** The glove menu mapping. */
    public static final KeyMapping MAPPING = new KeyMapping(GloveRadialKeyGate.NAME_KEY, GLOVE_HELD,
            InputConstants.Type.KEYSYM, GloveRadialKeyGate.DEFAULT_KEY, CATEGORY);

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
            if (GloveRadialKeyGate.pressOpensRadial(GLOVE_HELD.isActive(), mc.screen != null)) {
                GloveRadialScreen.open();
            }
        }
    }

    /**
     * Whether the local player casts from a hand: a glove there, or raw goo
     * there held bare handed.
     * decision raw-goo-items-work-bare-handed-for-the-held-type
     *
     * @param hand the hand
     * @return true where the hand holds the player's caster
     */
    private static boolean localPlayerCastsFrom(InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && GooCasters.casterHand(player) == hand;
    }
}
