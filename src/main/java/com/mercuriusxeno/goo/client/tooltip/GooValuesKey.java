package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.radial.GloveRadialKey;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The Goo values key, G by default, held over an item to reveal its goo
 * tooltip rows. Its GUI conflict context lets it share G with the glove menu
 * key unmarked, and each rebinds on its own.
 * decision tooltip-key-is-its-own-g-binding
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooValuesKey {

    /** The Goo values mapping's translation key. */
    public static final String NAME_KEY = "key.goo.goo_values";

    /** The Goo values mapping. */
    public static final KeyMapping MAPPING = new KeyMapping(NAME_KEY, KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, GloveRadialKey.CATEGORY);

    private GooValuesKey() {}

    /**
     * Registers the Goo values mapping.
     *
     * @param event the key mapping registration
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MAPPING);
    }

    /**
     * Whether the key bound to Goo values is down, read from the window
     * rather than the mapping's state, since an open container screen keeps
     * the mapping from seeing presses.
     *
     * @return true while the bound key or mouse button is held
     */
    public static boolean isHeld() {
        InputConstants.Key bound = MAPPING.getKey();
        long window = Minecraft.getInstance().getWindow().handle();
        return switch (bound.getType()) {
            case KEYSYM -> bound.getValue() != InputConstants.UNKNOWN.getValue()
                    && GLFW.glfwGetKey(window, bound.getValue()) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }
}
