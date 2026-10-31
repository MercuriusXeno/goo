package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests how a click finds its area by its button: a ref laid over its expression takes the right click and lets the
 * left click reach the view beneath it (decision left-click-modifies-right-click-follows).
 */
class ClickTargetsTest {

    /**
     * A right-only area laid over a left-only one runs its own action on a right click and lets a left click run the
     * area beneath it.
     */
    @Test
    void eachButtonRunsTheTopmostAreaHoldingAnActionForIt() {
        List<String> ran = new ArrayList<>();
        ClickTargets targets = new ClickTargets();
        targets.add(0, 0, 100, 20, () -> ran.add("modify"));
        targets.addRight(10, 5, 30, 10, () -> ran.add("follow"), null);
        targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        assertEquals(List.of("modify", "follow"), ran);
    }

    /**
     * A shield, as a dialog's backdrop, takes a right click too, so no link beneath the dialog follows.
     */
    @Test
    void shieldTakesTheRightClickAboveALink() {
        List<String> ran = new ArrayList<>();
        ClickTargets targets = new ClickTargets();
        targets.addRight(10, 5, 30, 10, () -> ran.add("follow"), null);
        targets.addShield(0, 0, 100, 20, () -> ran.add("shield"));
        targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        assertEquals(List.of("shield"), ran);
    }
}
