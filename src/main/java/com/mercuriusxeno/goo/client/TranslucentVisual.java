package com.mercuriusxeno.goo.client;

import org.joml.Matrix4fc;
import java.util.function.Consumer;

/**
 * A translucent goo visual drawn by the {@link TranslucentVisualPath}: each
 * frame it hands the path one {@link SortedDraw} per shape it shows, and the
 * path runs them in the order its {@link SortRoute} names.
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 */
@FunctionalInterface
public interface TranslucentVisual {

    /**
     * Hands the path this frame's draws.
     *
     * @param modelView the level's model view matrix this frame
     * @param draws     where each draw goes
     */
    void collect(Matrix4fc modelView, Consumer<SortedDraw> draws);

    /**
     * One shape a visual draws this frame.
     *
     * @param cameraDistanceSquared the squared distance from the camera to the shape's middle
     * @param draw                  draws the shape
     */
    record SortedDraw(double cameraDistanceSquared, Runnable draw) {
    }
}
