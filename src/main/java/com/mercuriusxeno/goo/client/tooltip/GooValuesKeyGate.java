package com.mercuriusxeno.goo.client.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The Goo values key as pure decisions: its name, its default key, the
 * conflict context that lets it share G with the glove menu key, and whether
 * the input bound to it is down.
 * decision tooltip-key-is-its-own-g-binding
 */
public final class GooValuesKeyGate {

    /** The Goo values mapping's translation key. */
    public static final String NAME_KEY = "key.goo.goo_values";

    /** The Goo values mapping's default key. */
    public static final int DEFAULT_KEY = GLFW.GLFW_KEY_G;

    /** Active in any screen, conflicting only with other GUI keys. */
    public static final IKeyConflictContext CONFLICT_CONTEXT = KeyConflictContext.GUI;

    /** The window's live input state. */
    public interface InputStates {

        /**
         * Whether a keyboard key is down.
         *
         * @param key the GLFW key code
         * @return true while the key is held
         */
        boolean keyDown(int key);

        /**
         * Whether a mouse button is down.
         *
         * @param button the GLFW mouse button
         * @return true while the button is held
         */
        boolean mouseButtonDown(int button);
    }

    private GooValuesKeyGate() {}

    /**
     * Whether the input bound to Goo values is down. An unbound key or a
     * scancode binding reads up.
     *
     * @param type   the bound input's type
     * @param code   the bound input's code
     * @param states the window's input state
     * @return true while the bound input is held
     */
    public static boolean boundInputDown(InputConstants.Type type, int code, InputStates states) {
        return switch (type) {
            case KEYSYM -> code != GLFW.GLFW_KEY_UNKNOWN && states.keyDown(code);
            case MOUSE -> states.mouseButtonDown(code);
            case SCANCODE -> false;
        };
    }
}
