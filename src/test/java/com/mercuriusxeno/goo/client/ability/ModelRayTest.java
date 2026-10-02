package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A goo's aim, cast against a posed model's cubes, lands on the first part
 * it meets: a pig's snout standing proud of its box before the body behind.
 */
class ModelRayTest {

    private static final float EPSILON = 1e-4f;

    /** A pig-like body: 0.6 deep along z, its head a box standing 0.3 proud of it at the front. */
    private static final ModelRay.CubeBox BODY = box("/body", new Matrix4f(), -0.3f, 0.2f, -0.3f, 0.3f, 0.8f, 0.3f);
    private static final ModelRay.CubeBox HEAD = box("/head", new Matrix4f(), -0.2f, 0.4f, 0.3f, 0.2f, 0.8f, 0.6f);
    private static final Vector3f FROM_THE_FRONT = new Vector3f(0, 0, -1);

    private static ModelRay.CubeBox box(String partPath, Matrix4f cubePose, float x0, float y0, float z0, float x1,
            float y1, float z1) {
        return new ModelRay.CubeBox(partPath, new Matrix4f(cubePose).invert(), new Vector3f(x0, y0, z0),
                new Vector3f(x1, y1, z1));
    }

    private static void assertPoint(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }

    @Nested
    class FirstHit {

        @Test
        void aimAtTheFaceMeetsTheSnoutBeforeTheBody() {
            ModelRay.PartHit hit = ModelRay.firstHit(List.of(BODY, HEAD), new Vector3f(0, 0.6f, 2), FROM_THE_FRONT)
                    .orElseThrow();

            assertEquals("/head", hit.partPath());
            assertPoint(new Vector3f(0, 0.6f, 0.6f), new Vector3f(hit.partPoint()));
        }

        @Test
        void aimBelowTheHeadMeetsTheBody() {
            ModelRay.PartHit hit = ModelRay.firstHit(List.of(BODY, HEAD), new Vector3f(0, 0.3f, 2), FROM_THE_FRONT)
                    .orElseThrow();

            assertEquals("/body", hit.partPath());
            assertPoint(new Vector3f(0, 0.3f, 0.3f), new Vector3f(hit.partPoint()));
        }

        @Test
        void aimPastEveryCubeMissesTheModel() {
            assertTrue(ModelRay.firstHit(List.of(BODY, HEAD), new Vector3f(2, 0.5f, 2), FROM_THE_FRONT).isEmpty());
        }

        @Test
        void cubeTurnedByItsPoseIsMetWhereItStands() {
            Matrix4f turnedAndMoved = new Matrix4f().translate(0, 0, 1).rotateY((float) Math.PI / 2);
            ModelRay.CubeBox turned = box("/plank", turnedAndMoved, -0.5f, 0f, -0.1f, 0.5f, 1f, 0.1f);

            ModelRay.PartHit hit = ModelRay.firstHit(List.of(turned), new Vector3f(0, 0.5f, 3), FROM_THE_FRONT)
                    .orElseThrow();

            assertPoint(new Vector3f(-0.5f, 0.5f, 0), new Vector3f(hit.partPoint()));
            assertPoint(new Vector3f(0, 0.5f, 1.5f), turnedAndMoved.transformPosition(hit.partPoint(), new Vector3f()));
        }

        @Test
        void cubeBehindTheOriginIsNotMet() {
            assertTrue(ModelRay.firstHit(List.of(BODY), new Vector3f(0, 0.5f, -2), FROM_THE_FRONT).isEmpty());
        }
    }

    @Nested
    class Cubes {

        @Test
        void grownCubeIsCastAtTheSizeItDraws() {
            ModelPart.Cube woolly = new ModelPart.Cube(0, 0, 0f, 0f, 0f, 16f, 16f, 16f, 2f, 2f, 2f, false, 64f, 64f,
                    EnumSet.allOf(Direction.class));
            ModelPart root = new ModelPart(List.of(woolly), Map.of());

            ModelRay.CubeBox box = ModelRay.cubesOf(root).getFirst();

            assertPoint(new Vector3f(-0.125f, -0.125f, -0.125f), new Vector3f(box.min()));
            assertPoint(new Vector3f(1.125f, 1.125f, 1.125f), new Vector3f(box.max()));
        }
    }

    @Nested
    class PinRidesItsPart {

        private ModelPart pigWithAHead(ModelPart head) {
            ModelPart.Cube body = new ModelPart.Cube(0, 0, -4f, 0f, -4f, 8f, 8f, 8f, 0f, 0f, 0f, false, 64f, 64f,
                    EnumSet.allOf(Direction.class));
            return new ModelPart(List.of(body), Map.of("head", head));
        }

        @Test
        void pinOnATurningHeadTurnsWithIt() {
            ModelPart.Cube snout = new ModelPart.Cube(0, 0, -2f, -2f, 0f, 4f, 4f, 4f, 0f, 0f, 0f, false, 64f, 64f,
                    EnumSet.allOf(Direction.class));
            ModelPart head = new ModelPart(List.of(snout), Map.of());
            head.setPos(0f, 4f, 4f);
            ModelPart root = pigWithAHead(head);
            ModelRay.PartHit pin = ModelRay.firstHit(ModelRay.cubesOf(root), new Vector3f(0, 0.25f, 2),
                    FROM_THE_FRONT).orElseThrow();

            head.yRot = (float) Math.PI / 2;
            Vector3f turned = MobCoatLayer.pinFrame(root, pin).transformPosition(pin.partPoint(), new Vector3f());

            assertEquals("/head", pin.partPath());
            assertPoint(new Vector3f(0.25f, 0.25f, 0.25f), turned);
        }

        @Test
        void pinOnNoPartStaysInTheRootsFrame() {
            ModelPart root = pigWithAHead(new ModelPart(List.of(), Map.of()));

            assertEquals(new Matrix4f(), MobCoatLayer.pinFrame(root, new ModelRay.PartHit(null, new Vector3f())));
        }
    }

    @Nested
    class CarriedOntoTheModel {

        @Test
        void aimEnteringTheBoxBehindTheSnoutLandsOnTheSnout() {
            Vec3 boxEntry = new Vec3(0, 0.6, 0.3);
            Vec3 aim = new Vec3(0, 0, -1);

            ModelRay.PartHit pin = MobCoatLayer.aimedModelHit(List.of(BODY, HEAD), new Matrix4f(), boxEntry, aim,
                    Vec3.ZERO);

            assertEquals("/head", pin.partPath());
            assertPoint(new Vector3f(0, 0.6f, 0.6f), new Vector3f(pin.partPoint()));
        }

        @Test
        void aimMissingTheModelKeepsTheBoxPoint() {
            Vec3 boxEntry = new Vec3(1.5, 0.6, 0.3);

            ModelRay.PartHit pin = MobCoatLayer.aimedModelHit(List.of(BODY, HEAD), new Matrix4f(), boxEntry,
                    new Vec3(0, 0, -1), Vec3.ZERO);

            assertNull(pin.partPath());
            assertPoint(new Vector3f(1.5f, 0.6f, 0.3f), new Vector3f(pin.partPoint()));
        }

        @Test
        void strikeWithNoAimKeepsTheBoxPoint() {
            Vec3 boxEntry = new Vec3(0, 0.6, 0.3);

            ModelRay.PartHit pin = MobCoatLayer.aimedModelHit(List.of(BODY, HEAD), new Matrix4f(), boxEntry,
                    Vec3.ZERO, Vec3.ZERO);

            assertNull(pin.partPath());
            assertPoint(new Vector3f(0, 0.6f, 0.3f), new Vector3f(pin.partPoint()));
        }
    }
}
