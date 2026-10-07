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
import org.lwjgl.glfw.GLFW;

/**
 * The Goo values key mapping, held over an item to reveal its goo tooltip
 * rows; {@link GooValuesKeyGate} holds its decisions.
 * decision tooltip-key-is-its-own-g-binding
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooValuesKey {

    /** The Goo values mapping. */
    public static final KeyMapping MAPPING = new KeyMapping(GooValuesKeyGate.NAME_KEY,
            GooValuesKeyGate.CONFLICT_CONTEXT, InputConstants.Type.KEYSYM, GooValuesKeyGate.DEFAULT_KEY,
            GloveRadialKey.CATEGORY);

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
     * Whether the input bound to Goo values is down, read from the window
     * rather than the mapping's state, since an open container screen keeps
     * the mapping from seeing presses.
     *
     * @return true while the bound key or mouse button is held
     */
    public static boolean isHeld() {
        InputConstants.Key bound = MAPPING.getKey();
        long window = Minecraft.getInstance().getWindow().handle();
        return GooValuesKeyGate.boundInputDown(bound.getType(), bound.getValue(), new GooValuesKeyGate.InputStates() {
            @Override
            public boolean keyDown(int key) {
                return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            }

            @Override
            public boolean mouseButtonDown(int button) {
                return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
            }
        });
    }
}
