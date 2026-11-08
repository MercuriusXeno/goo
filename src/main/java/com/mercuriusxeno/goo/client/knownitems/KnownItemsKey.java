package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ClientKnownItems;
import com.mercuriusxeno.goo.client.radial.GloveRadialKey;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * The known items key, G by default, bound only while the main hand is empty and no glove is
 * held: its press opens {@link KnownItemsOverlay}.
 * decision g-opens-the-known-items-menu-empty-handed
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class KnownItemsKey {

    private static final EmptyHandConflictContext HANDS_FREE =
            new EmptyHandConflictContext(KnownItemsKey::localHandsFree);

    /** The known items mapping, in the goo key category beside the glove menu. */
    public static final KeyMapping MAPPING = new KeyMapping(KnownItemsKeyGate.NAME_KEY, HANDS_FREE,
            InputConstants.Type.KEYSYM, KnownItemsKeyGate.DEFAULT_KEY, GloveRadialKey.CATEGORY);

    private KnownItemsKey() {}

    /**
     * Registers the known items mapping.
     *
     * @param event the key mapping registration
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MAPPING);
    }

    /**
     * Opens the overlay for a press of the known items key.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (MAPPING.consumeClick()) {
            LocalPlayer player = mc.player;
            if (player != null && KnownItemsKeyGate.pressOpensOverlay(player.getMainHandItem().isEmpty(),
                    holdsGlove(player), mc.screen != null, !ClientKnownItems.current().items().isEmpty())) {
                KnownItemsOverlay.openFromKey();
            }
        }
    }

    private static boolean localHandsFree() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem().isEmpty() && !holdsGlove(player);
    }

    private static boolean holdsGlove(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof GooGloveItem) {
                return true;
            }
        }
        return false;
    }
}
