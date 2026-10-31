package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests how a click finds its area by its gesture: a ref laid over its expression takes the shift + left click and
 * lets the plain left click reach the view beneath it, and a right click reaches no link (decision
 * shift-left-click-follows-plain-left-modifies).
 */
class ClickTargetsTest {
    private static final int NO_MODIFIERS = 0;

    /**
     * A follow-only area laid over a left-only one runs its own action on a shift + left click, lets a plain left
     * click run the area beneath it, and takes no right click.
     */
    @Test
    void eachGestureRunsTheTopmostAreaHoldingAnActionForIt() {
        List<String> ran = new ArrayList<>();
        ClickTargets targets = new ClickTargets();
        targets.add(0, 0, 100, 20, () -> ran.add("modify"));
        targets.addFollow(10, 5, 30, 10, () -> ran.add("follow"), null);
        targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_LEFT, NO_MODIFIERS);
        targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT);
        assertFalse(targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_RIGHT, NO_MODIFIERS));
        assertEquals(List.of("modify", "follow"), ran);
    }

    /**
     * A shield, as a dialog's backdrop, takes a plain left, a shift + left and a right click alike, so neither the
     * link nor the view beneath the dialog runs.
     */
    @Test
    void shieldSwallowsEveryGestureAboveALinkAndItsView() {
        List<String> ran = new ArrayList<>();
        ClickTargets targets = new ClickTargets();
        targets.add(0, 0, 100, 20, () -> ran.add("modify"));
        targets.addFollow(10, 5, 30, 10, () -> ran.add("follow"), null);
        targets.addShield(0, 0, 100, 20, () -> ran.add("shield"));
        targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_LEFT, NO_MODIFIERS);
        targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT);
        targets.click(15, 8, GLFW.GLFW_MOUSE_BUTTON_RIGHT, NO_MODIFIERS);
        assertEquals(List.of("shield", "shield", "shield"), ran);
    }
}
