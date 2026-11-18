package com.mercuriusxeno.goo.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bare arm raw goo held in first person draws in the glove body's place stays inside the glove body, the sleeve
 * included: nothing of it crosses the palm face the palm cube rests on, so the translucent sleeve never pokes through
 * the translucent cube and the two never swap which shows from frame to frame.
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
class BareArmTest {

    private static final float PIXELS = 16f;
    /** The glove body's sides in model pixels: x and z both span 5 to 11. */
    private static final float BODY_MIN_PX = 5f;
    private static final float BODY_MAX_PX = 11f;
    private static final float WITHIN = 1e-5f;

    @ParameterizedTest
    @CsvSource({"RIGHT, false", "RIGHT, true", "LEFT, false", "LEFT, true"})
    void theSleeveStaysBehindThePalmFaceAndInsideTheGloveBody(HumanoidArm side, boolean slim) {
        GloveArmLayer.BareArm arm = GloveArmLayer.bareArm(side, slim);

        for (Vector3f at : placed(arm, arm.sleeve())) {
            assertInsideTheBody(at, "sleeve");
        }
        for (Vector3f at : placed(arm, arm.arm())) {
            assertInsideTheBody(at, "arm");
        }
    }

    private static void assertInsideTheBody(Vector3f at, String part) {
        float zPx = at.z() * PIXELS;
        float xPx = at.x() * PIXELS;
        assertTrue(zPx >= GloveSpecialRenderer.PALM_FACE_Z_PX - WITHIN,
                "the " + part + " should stay behind the palm face at z " + GloveSpecialRenderer.PALM_FACE_Z_PX
                        + ", reaches z " + zPx);
        assertTrue(zPx <= BODY_MAX_PX + WITHIN && xPx >= BODY_MIN_PX - WITHIN && xPx <= BODY_MAX_PX + WITHIN,
                "the " + part + " should stay inside the glove body, reaches x " + xPx + " z " + zPx);
    }

    /** Every vertex of a part, placed in the glove's model. */
    private static List<Vector3f> placed(GloveArmLayer.BareArm arm, ModelPart part) {
        PoseStack pose = new PoseStack();
        arm.standInGloveBody(pose);
        List<Vector3f> vertices = new ArrayList<>();
        part.visit(pose, (at, path, index, cube) -> {
            for (ModelPart.Polygon polygon : cube.polygons) {
                for (ModelPart.Vertex vertex : polygon.vertices()) {
                    vertices.add(at.pose().transformPosition(new Vector3f(vertex.x() / PIXELS, vertex.y() / PIXELS,
                            vertex.z() / PIXELS)));
                }
            }
        });
        assertFalse(vertices.isEmpty(), "the part should hold a cube");
        return vertices;
    }
}
