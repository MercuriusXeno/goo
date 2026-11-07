package com.mercuriusxeno.goo.client.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.item.GooGloveItem;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The glove arm layer cuts the arm holding a goo glove at the glove's cuff, at a height derived from the glove's
 * third-person display transform, and stands on every player skin's player and mannequin renderers
 * (decision diagnose-and-correct-the-arm-through-the-glove).
 */
class GloveArmLayerTest {

    private static final float EPSILON = 1.0e-4f;
    private static final String MODELS = "assets/goo/models/item/";
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer()).create();

    private static JsonObject modelJson(String name) throws IOException {
        try (InputStream in = GloveArmLayerTest.class.getClassLoader().getResourceAsStream(MODELS + name + ".json")) {
            assertNotNull(in, name + " is not on the classpath");
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    private static ItemTransform thirdPerson(String glove, String hand) throws IOException {
        JsonElement display = modelJson(glove).getAsJsonObject("display").get("thirdperson_" + hand);
        return GSON.fromJson(display, ItemTransform.class);
    }

    private static float cuffMiddleY(String body) throws IOException {
        for (JsonElement element : modelJson(body).getAsJsonArray("elements")) {
            JsonObject cube = element.getAsJsonObject();
            if ("cuff".equals(cube.get("name").getAsString())) {
                return (cube.getAsJsonArray("from").get(1).getAsFloat() + cube.getAsJsonArray("to").get(1).getAsFloat()) / 2f;
            }
        }
        throw new AssertionError(body + " holds no cuff");
    }

    private static ItemTransform transform(float translationZPx, float scale) {
        return new ItemTransform(new Vector3f(90f, 0f, 0f), new Vector3f(0f, -2f / 16f, translationZPx / 16f),
                new Vector3f(scale, scale, scale));
    }

    /** Where the arm's cut lands, in the arm part's pixels below its shoulder pivot. */
    @Nested
    class CutHeight {

        /**
         * Hand-computed through vanilla's item-in-hand chain: the hand offset's 10 px less the display
         * translation's 5.25 px z, less the scale 0.675 times the cuff middle's 2 px above the model center.
         */
        private static final float GLOVE_CUT_PX = 10f - 5.25f - 0.675f * (10f - 8f);

        @ParameterizedTest
        @ValueSource(strings = {"righthand", "lefthand"})
        void cutSitsAtTheGloveCuffsMiddle(String hand) throws IOException {
            float cut = GloveArmLayer.cutHeight(thirdPerson("goo_glove", hand), "lefthand".equals(hand),
                    cuffMiddleY("glove_body"));
            assertEquals(GLOVE_CUT_PX, cut, EPSILON);
        }

        @Test
        void smallerScaleMovesTheCutUpTheArmTowardTheHand() {
            assertEquals(10f - 5.25f - 0.5f * 2f, GloveArmLayer.cutHeight(transform(5.25f, 0.5f), false, 10f),
                    EPSILON);
        }

        @Test
        void smallerTranslationMovesTheCutDownTheArm() {
            assertEquals(10f - 4f - 0.675f * 2f, GloveArmLayer.cutHeight(transform(4f, 0.675f), false, 10f),
                    EPSILON);
        }

        @ParameterizedTest
        @ValueSource(strings = {"glove_body", "glove_netherite_body", "glove_exorite_body"})
        void layerCutsAtEveryGloveBodysCuffMiddle(String body) throws IOException {
            assertEquals(cuffMiddleY(body), GloveArmLayer.CUFF_MODEL_Y, EPSILON);
        }
    }

    /** Which arm the layer cuts, by the hand holding the glove and the entity's main arm. */
    @Nested
    class ArmChoice {

        private ItemStack glove() {
            ItemStack stack = mock(ItemStack.class);
            when(stack.getItem()).thenReturn(mock(GooGloveItem.class));
            return stack;
        }

        private ItemStack holding(Item item) {
            ItemStack stack = mock(ItemStack.class);
            when(stack.getItem()).thenReturn(item);
            return stack;
        }

        private ItemStack bare() {
            return holding(mock(Item.class));
        }

        @Test
        void rightHandedGloveInTheMainHandCutsTheRightArm() {
            assertEquals(EnumSet.of(HumanoidArm.RIGHT), GloveArmLayer.glovedArms(HumanoidArm.RIGHT, glove(), bare()));
        }

        @Test
        void leftHandedGloveInTheMainHandCutsTheLeftArm() {
            assertEquals(EnumSet.of(HumanoidArm.LEFT), GloveArmLayer.glovedArms(HumanoidArm.LEFT, glove(), bare()));
        }

        @Test
        void gloveInTheOffHandCutsTheOppositeArm() {
            assertEquals(EnumSet.of(HumanoidArm.LEFT), GloveArmLayer.glovedArms(HumanoidArm.RIGHT, bare(), glove()));
            assertEquals(EnumSet.of(HumanoidArm.RIGHT), GloveArmLayer.glovedArms(HumanoidArm.LEFT, bare(), glove()));
        }

        @Test
        void gloveInBothHandsCutsBothArms() {
            assertEquals(EnumSet.allOf(HumanoidArm.class), GloveArmLayer.glovedArms(HumanoidArm.RIGHT, glove(), glove()));
        }

        @Test
        void noGloveCutsNoArm() {
            assertEquals(EnumSet.noneOf(HumanoidArm.class), GloveArmLayer.glovedArms(HumanoidArm.RIGHT, bare(), bare()));
        }

        @Test
        void anotherItemInTheHandCutsNoArm() {
            Item sword = mock(Item.class);
            assertEquals(EnumSet.noneOf(HumanoidArm.class),
                    GloveArmLayer.glovedArms(HumanoidArm.RIGHT, holding(sword), holding(sword)));
        }
    }

    /** The layer stands on the renderers every player skin draws through. */
    @Nested
    class Registration {

        @Test
        @SuppressWarnings("unchecked")
        void layerStandsOnEverySkinsPlayerAndMannequinRenderers() {
            Map<PlayerModelType, AvatarRenderer<AbstractClientPlayer>> players = new EnumMap<>(PlayerModelType.class);
            Map<PlayerModelType, AvatarRenderer<ClientMannequin>> mannequins = new EnumMap<>(PlayerModelType.class);
            for (PlayerModelType skin : PlayerModelType.values()) {
                players.put(skin, mock(AvatarRenderer.class));
                mannequins.put(skin, mock(AvatarRenderer.class));
            }
            GloveArmLayer.addToSkins(new EntityRenderersEvent.AddLayers(Map.of(), players, mannequins, null));
            Set<PlayerModelType> skins = EnumSet.allOf(PlayerModelType.class);
            for (PlayerModelType skin : skins) {
                verify(players.get(skin)).addLayer(any(GloveArmLayer.class));
                verify(mannequins.get(skin)).addLayer(any(GloveArmLayer.class));
            }
        }
    }
}
