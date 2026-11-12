package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.spider.SpiderModel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The skin size the encasement layer reads off a baked model, width and height
 * apart, and the overlay coordinates it hands the shader that size in
 * (decision diagnose-and-correct-petrify-stone-stretching-off-native-texels).
 */
class ModelSkinSizeTest {

    private static ModelPart humanoidOn(float grow, int skinWidth, int skinHeight) {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(grow), 0f), skinWidth, skinHeight)
                .bakeRoot();
    }

    private static void assertUnpacksTo(int width, int height, int overlayCoords) {
        assertEquals(width, overlayCoords & 0xFFFF, "the width rides the overlay's u");
        assertEquals(height, overlayCoords >>> 16, "the height rides the overlay's v");
    }

    @Nested
    class ReadOffTheModel {

        @Test
        void theSpidersModelReadsItsSixtyFourByThirtyTwoSkin() {
            assertEquals(new ModelSkinSize(64, 32), ModelSkinSize.of(SpiderModel.createSpiderBodyLayer().bakeRoot()));
        }

        @Test
        void aZombiesModelReadsItsSixtyFourSquareSkin() {
            assertEquals(new ModelSkinSize(64, 64), ModelSkinSize.of(humanoidOn(0f, 64, 64)));
        }

        @Test
        void cubesGrownPastTheirTexelsStillReadTheSkin() {
            assertEquals(new ModelSkinSize(64, 32), ModelSkinSize.of(humanoidOn(0.5f, 64, 32)));
            assertEquals(new ModelSkinSize(128, 64), ModelSkinSize.of(humanoidOn(1f, 128, 64)));
        }
    }

    @Nested
    class HandedToTheShader {

        @Test
        void theOverlayCoordinatesUnpackToTheSkinSize() {
            assertUnpacksTo(64, 32, new ModelSkinSize(64, 32).overlayCoords());
            assertUnpacksTo(64, 64, new ModelSkinSize(64, 64).overlayCoords());
            assertUnpacksTo(256, 128, new ModelSkinSize(256, 128).overlayCoords());
        }

        @Test
        void theSpidersReadPacksItsOwnSize() {
            assertUnpacksTo(64, 32, ModelSkinSize.of(SpiderModel.createSpiderBodyLayer().bakeRoot()).overlayCoords());
        }
    }
}
