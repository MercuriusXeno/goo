package com.mercuriusxeno.goo.client.tooltip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.radial.GloveHeldConflictContext;
import com.mercuriusxeno.goo.client.radial.GloveRadialKeyGate;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Goo values key: it shares G with the glove menu key without a
 * conflict, reads the input bound to it rather than shift, and carries its
 * English names (decision tooltip-key-is-its-own-g-binding).
 * Reads en_us.json through the classpath.
 */
class GooValuesKeyGateTest {

    /** Input state holding a fixed set of keys and mouse buttons down. */
    private record HeldInputs(Set<Integer> keys, Set<Integer> buttons) implements GooValuesKeyGate.InputStates {

        static HeldInputs keys(Integer... held) {
            return new HeldInputs(Set.of(held), Set.of());
        }

        @Override
        public boolean keyDown(int key) {
            return keys.contains(key);
        }

        @Override
        public boolean mouseButtonDown(int button) {
            return buttons.contains(button);
        }
    }

    @Nested
    class Binding {

        @Test
        void defaultsToTheGloveMenuKeysG() {
            assertEquals(GLFW.GLFW_KEY_G, GooValuesKeyGate.DEFAULT_KEY);
            assertEquals(GloveRadialKeyGate.DEFAULT_KEY, GooValuesKeyGate.DEFAULT_KEY);
        }

        @Test
        void sharesGWithTheGloveMenuKeyUnmarked() {
            GloveHeldConflictContext gloveHeld = new GloveHeldConflictContext(hand -> true);

            assertFalse(GooValuesKeyGate.CONFLICT_CONTEXT.conflicts(gloveHeld));
            assertFalse(gloveHeld.conflicts(GooValuesKeyGate.CONFLICT_CONTEXT));
        }
    }

    @Nested
    class BoundInput {

        @Test
        void heldGRevealsAtTheDefault() {
            assertTrue(GooValuesKeyGate.boundInputDown(
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, HeldInputs.keys(GLFW.GLFW_KEY_G)));
        }

        @Test
        void heldShiftRevealsNothingAtTheDefault() {
            assertFalse(GooValuesKeyGate.boundInputDown(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
                    HeldInputs.keys(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)));
        }

        @Test
        void heldShiftRevealsOnceReboundToShift() {
            assertTrue(GooValuesKeyGate.boundInputDown(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT,
                    HeldInputs.keys(GLFW.GLFW_KEY_LEFT_SHIFT)));
        }

        @Test
        void heldMouseButtonRevealsWhenBoundToIt() {
            HeldInputs middleHeld = new HeldInputs(Set.of(), Set.of(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));

            assertTrue(GooValuesKeyGate.boundInputDown(
                    InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, middleHeld));
        }

        @Test
        void unboundKeyRevealsNothing() {
            assertFalse(GooValuesKeyGate.boundInputDown(InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                    HeldInputs.keys(GLFW.GLFW_KEY_UNKNOWN)));
        }

        @Test
        void scancodeBindingRevealsNothing() {
            assertFalse(GooValuesKeyGate.boundInputDown(InputConstants.Type.SCANCODE, GLFW.GLFW_KEY_G,
                    HeldInputs.keys(GLFW.GLFW_KEY_G)));
        }
    }

    @Nested
    class Translations {

        @Test
        void mappingReadsGooValuesUnderTheGooCategory() throws Exception {
            JsonObject lang = readLang();
            String categoryKey = Identifier.fromNamespaceAndPath(Goo.MODID, GloveRadialKeyGate.CATEGORY_PATH)
                    .toLanguageKey("key.category");

            assertEquals("Goo values", lang.get(GooValuesKeyGate.NAME_KEY).getAsString());
            assertEquals("Goo", lang.get(categoryKey).getAsString());
        }

        private JsonObject readLang() throws Exception {
            try (InputStream in = GooValuesKeyGateTest.class.getResourceAsStream("/assets/goo/lang/en_us.json")) {
                assertNotNull(in, "en_us.json missing on the classpath");
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }
}
