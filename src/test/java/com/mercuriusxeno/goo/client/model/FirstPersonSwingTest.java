package com.mercuriusxeno.goo.client.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A matrix out of the swung hand's pose carries back to where it stands
 * unswung, and a forgotten swing leaves it as it is
 * (decision blob-holds-its-angle-through-a-throws-swing).
 */
class FirstPersonSwingTest {

    private static final Matrix4f BEFORE_THE_SWING = new Matrix4f().translation(0.56f, -0.52f, -0.72f)
            .rotateXYZ(0.3f, -0.6f, 0.1f);
    private static final Matrix4f THE_SWING = new Matrix4f().rotateXYZ(-1.2f, 0.3f, -0.35f);
    private static final Matrix4f BELOW_THE_SWING = new Matrix4f().rotateXYZ(0.2f, 1.1f, -0.4f)
            .translate(-0.5f, -0.5f, -0.5f).scale(0.6f);
    private static final float WITHIN = 1e-5f;

    @AfterEach
    void forgetTheSwing() {
        FirstPersonSwing.forgetSwing();
    }

    @Test
    void aMatrixBelowTheSwingCarriesBackToWhereItStandsUnswung() {
        FirstPersonSwing.recordSwing(BEFORE_THE_SWING, new Matrix4f(BEFORE_THE_SWING).mul(THE_SWING));
        Matrix4f swung = new Matrix4f(BEFORE_THE_SWING).mul(THE_SWING).mul(BELOW_THE_SWING);

        assertTrue(FirstPersonSwing.unswung(swung).equals(new Matrix4f(BEFORE_THE_SWING).mul(BELOW_THE_SWING),
                WITHIN), "the swing should come back out of the glove's matrix");
    }

    @Test
    void aForgottenSwingLeavesTheMatrixAsItIs() {
        FirstPersonSwing.recordSwing(BEFORE_THE_SWING, new Matrix4f(BEFORE_THE_SWING).mul(THE_SWING));
        FirstPersonSwing.forgetSwing();
        Matrix4f glove = new Matrix4f(BEFORE_THE_SWING).mul(BELOW_THE_SWING);

        Vector3f corner = FirstPersonSwing.unswung(glove).transformPosition(new Vector3f(1f, 1f, 1f));
        assertTrue(corner.distance(glove.transformPosition(new Vector3f(1f, 1f, 1f))) < WITHIN,
                "with the swing forgotten the glove's matrix should stand as it is");
    }
}
