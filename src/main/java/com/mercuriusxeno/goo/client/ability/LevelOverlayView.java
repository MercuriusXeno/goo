package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

/**
 * Draws in the camera's view after the level: AfterLevel fires once the
 * level has popped its view rotation off the model view stack, so geometry
 * laid in camera-relative world space takes the event's view matrix back
 * for the draw, and the stack is restored after it.
 * decision knives-and-dart-function-as-projectiles: knives and javelins draw after the clouds, turned with the view
 */
final class LevelOverlayView {

    private LevelOverlayView() {
    }

    /**
     * Runs a draw with the event's view matrix on the model view stack.
     *
     * @param event the after-level stage event
     * @param draw  the draw, laid in camera-relative world space
     */
    static void inView(RenderLevelStageEvent.AfterLevel event, Runnable draw) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        try {
            draw.run();
        } finally {
            modelView.popMatrix();
        }
    }
}
