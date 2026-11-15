package com.mercuriusxeno.goo.client.model;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * The turn vanilla's swing laid on the first-person hand being drawn, kept
 * so the glove's model can be carried back to where it would stand unswung.
 * ItemInHandRenderer.applyItemArmAttackTransform turns the hand's pose by the
 * swing; this holds the pose before that turn against the pose after it, for
 * the hand renderArmWithItem draws now, and nothing outside that draw.
 * decision blob-holds-its-angle-through-a-throws-swing
 */
public final class FirstPersonSwing {

    private static final Matrix4fc NO_SWING = new Matrix4f();
    /** The swung pose onto the unswung one, in the hand pass's camera-relative world axes. */
    private static volatile Matrix4fc swingUndone = NO_SWING;

    private FirstPersonSwing() {
    }

    /**
     * Records the swing's turn from the hand's pose on either side of it.
     *
     * @param beforeTheSwing the hand's pose before the swing turned it
     * @param afterTheSwing  the hand's pose after the swing turned it
     */
    public static void recordSwing(Matrix4fc beforeTheSwing, Matrix4fc afterTheSwing) {
        swingUndone = new Matrix4f(beforeTheSwing).mul(new Matrix4f(afterTheSwing).invert());
    }

    /** Forgets the swing, as each hand's draw begins and ends. */
    public static void forgetSwing() {
        swingUndone = NO_SWING;
    }

    /**
     * Where a matrix out of the hand's swung pose stands with the swing taken out.
     *
     * @param swungToHandPass a matrix from the swung hand's pose, or from below it, onto the hand pass
     * @return the same matrix had the swing not turned the hand
     */
    public static Matrix4f unswung(Matrix4fc swungToHandPass) {
        return new Matrix4f(swingUndone).mul(swungToHandPass);
    }
}
