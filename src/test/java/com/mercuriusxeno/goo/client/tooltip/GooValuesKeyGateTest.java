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
import org.lwjgl.sdl.SDLScancode;
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
            assertEquals(InputConstants.KEY_G, GooValuesKeyGate.DEFAULT_KEY);
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
                    InputConstants.Type.KEYBOARD, InputConstants.KEY_G, HeldInputs.keys(InputConstants.KEY_G)));
        }

        @Test
        void heldShiftRevealsNothingAtTheDefault() {
            assertFalse(GooValuesKeyGate.boundInputDown(InputConstants.Type.KEYBOARD, InputConstants.KEY_G,
                    HeldInputs.keys(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT)));
        }

        @Test
        void heldShiftRevealsOnceReboundToShift() {
            assertTrue(GooValuesKeyGate.boundInputDown(InputConstants.Type.KEYBOARD, InputConstants.KEY_LSHIFT,
                    HeldInputs.keys(InputConstants.KEY_LSHIFT)));
        }

        @Test
        void heldMouseButtonRevealsWhenBoundToIt() {
            HeldInputs middleHeld = new HeldInputs(Set.of(), Set.of(InputConstants.MOUSE_BUTTON_MIDDLE));

            assertTrue(GooValuesKeyGate.boundInputDown(
                    InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE, middleHeld));
        }

        @Test
        void unboundKeyRevealsNothing() {
            assertFalse(GooValuesKeyGate.boundInputDown(InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_UNKNOWN,
                    HeldInputs.keys(SDLScancode.SDL_SCANCODE_UNKNOWN)));
        }

        // 26.3 dropped the scancode binding type, so a binding of the one other type stands for it.
        @Test
        void mouseBindingRevealsNothingWhileItsCodeIsHeldAsAKey() {
            assertFalse(GooValuesKeyGate.boundInputDown(InputConstants.Type.MOUSE, InputConstants.KEY_G,
                    HeldInputs.keys(InputConstants.KEY_G)));
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
