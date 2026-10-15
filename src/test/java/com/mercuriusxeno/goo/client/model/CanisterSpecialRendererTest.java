package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.StampedGoo;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The canister item renderer builds its fill from every type the canister holds:
 * the vat's type bands, one per type by volume, drawn as one surface at the fill's
 * own bounds (decisions canisters-hold-more-than-one-goo-type,
 * noise-mingled-type-textures and one-pass-blends-every-type). StampedGoo stands
 * the goo resources.
 */
class CanisterSpecialRendererTest {

    private static final int CAPACITY = 1000;
    private static final int ROCK_VOLUME = 200;
    private static final int BLAZE_VOLUME = 600;

    private StampedGoo goo;

    @BeforeEach
    void stampGooTypes() {
        goo = new StampedGoo();
    }

    @AfterEach
    void releaseGooFluids() {
        goo.close();
    }

    @Test
    void aTwoTypeCanisterCarriesTheVatsBandsForBothTypes() {
        CanisterFluidContent content = CanisterFluidContent.of(goo.resource(GooTypes.ROCK), ROCK_VOLUME)
                .withVolume(goo.resource(GooTypes.BLAZE), BLAZE_VOLUME);

        CanisterSpecialRenderer.GooData data = CanisterSpecialRenderer.dataOf(content, CAPACITY, false, false);

        assertEquals(TypeBands.over(new GooContents(Map.of(GooTypes.ROCK, ROCK_VOLUME, GooTypes.BLAZE, BLAZE_VOLUME))),
                data.bands());
        assertEquals(GooTypes.BLAZE, data.gooType());
        assertEquals((float) (ROCK_VOLUME + BLAZE_VOLUME) / CAPACITY, data.fill());
    }

    @Test
    void aTwoTypeFillSubmitsOneSurfaceAtTheSingleTypeFillsBounds() {
        float fill = (float) (ROCK_VOLUME + BLAZE_VOLUME) / CAPACITY;
        List<TypeBand> bands = TypeBands.over(new GooContents(
                Map.of(GooTypes.ROCK, ROCK_VOLUME, GooTypes.BLAZE, BLAZE_VOLUME)));
        CanisterSpecialRenderer.GooData data =
                new CanisterSpecialRenderer.GooData(GooTypes.BLAZE, null, fill, false, false, bands);
        List<List<TypeBand>> submitted = new ArrayList<>();
        RecordingVertexConsumer mingled = new RecordingVertexConsumer();

        CanisterSpecialRenderer.submitMingledFluid((surfaceBands, emitter) -> {
            submitted.add(surfaceBands);
            emitter.accept(new RenderContext(new PoseStack().last(), mingled, 0));
        }, data);

        RecordingVertexConsumer single = new RecordingVertexConsumer();
        FluidFaceEmitter.emitFluidFaces(new RenderContext(new PoseStack().last(), single, 0),
                CanisterSpecialRenderer.fluidBounds(fill), wholeAtlasSprite(), GooRenderUtil.OPAQUE_WHITE);
        assertEquals(List.of(bands), submitted);
        assertEquals(positions(single.vertices()), positions(mingled.vertices()));
    }

    private static TextureAtlasSprite wholeAtlasSprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV1()).thenReturn(1f);
        return sprite;
    }

    private static List<List<Float>> positions(List<RecordingVertexConsumer.Vertex> vertices) {
        return vertices.stream().map(vertex -> List.of(vertex.x(), vertex.y(), vertex.z())).toList();
    }
}
