package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.item.GhostItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A ghost draws its item where the item itself draws: the ghost's own layer adds no transform of its
 * own, so the slot icon and the hand show the item in place rather than half a block off
 * (decision ghost-is-one-item-type-whose-entity-self-destroys). The display context is set by
 * reflection, as the resolver sets it from inside its package.
 */
class GhostItemPoseTest {

    private static final ItemDisplayContext CONTEXT = ItemDisplayContext.GUI;
    private static final float SLOT = 16f;
    private static final float EPSILON = 1e-4f;

    private static void setContext(ItemStackRenderState state) throws ReflectiveOperationException {
        Field field = ItemStackRenderState.class.getDeclaredField("displayContext");
        field.setAccessible(true);
        field.set(state, CONTEXT);
    }

    private static BakedQuad quad() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.atlasLocation()).thenReturn(Identifier.withDefaultNamespace("textures/atlas/items.png"));
        return new BakedQuad(new Vector3f(0, 0, 0.5f), new Vector3f(0, 1, 0.5f), new Vector3f(1, 1, 0.5f),
                new Vector3f(1, 0, 0.5f), 0L, 0L, 0L, 0L, Direction.SOUTH,
                new BakedQuad.MaterialInfo(sprite, ChunkSectionLayer.CUTOUT, Sheets.cutoutItemSheet(), -1, true, 0, true));
    }

    /** A resolver whose every item is one quad under a turned, moved and scaled display transform. */
    private static ItemModelResolver resolverOfOneTransformedLayer() {
        ItemModelResolver resolver = mock(ItemModelResolver.class);
        doAnswer(call -> {
            ItemStackRenderState state = call.getArgument(0);
            state.clear();
            setContext(state);
            ItemStackRenderState.LayerRenderState layer = state.newLayer();
            layer.setItemTransform(new ItemTransform(new Vector3f(0, 30, 0), new Vector3f(0.1f, 0.2f, 0),
                    new Vector3f(0.5f, 0.5f, 0.5f)));
            layer.prepareQuadList().add(quad());
            return null;
        }).when(resolver).updateForTopItem(any(), any(), any(), any(), any(), anyInt());
        return resolver;
    }

    /** The poses the state submits its quads at, inside a GUI slot's pose. */
    private static List<Matrix4f> submittedPoses(ItemStackRenderState state) {
        List<Matrix4f> poses = new ArrayList<>();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            poses.add(new Matrix4f(((PoseStack) call.getArgument(0)).last().pose()));
            return null;
        }).when(collector).submitItem(any(), any(), anyInt(), anyInt(), anyInt(), any(), any(), any());
        PoseStack slot = new PoseStack();
        slot.translate(SLOT / 2, SLOT / 2, 0);
        slot.scale(SLOT, -SLOT, SLOT);
        state.submit(slot, collector, 0, 0, 0);
        return poses;
    }

    @Test
    void ghostSubmitsItsItemAtThePoseTheItemItselfDoes() throws ReflectiveOperationException {
        ItemModelResolver resolver = resolverOfOneTransformedLayer();
        ItemStackRenderState plain = new ItemStackRenderState();
        resolver.updateForTopItem(plain, mock(ItemStack.class), CONTEXT, null, null, 0);

        ItemStack ghost = mock(ItemStack.class);
        ItemStackRenderState output = new ItemStackRenderState();
        setContext(output);
        try (MockedStatic<GhostItem> ghosts = mockStatic(GhostItem.class)) {
            ghosts.when(() -> GhostItem.represented(ghost)).thenReturn(Identifier.withDefaultNamespace("stone"));
            ghosts.when(() -> GhostItem.representedStack(ghost)).thenReturn(mock(ItemStack.class));
            new GhostItemModel.Unbaked().bake(null, null).update(output, ghost, resolver, CONTEXT, null, null, 0);
        }

        List<Matrix4f> plainPoses = submittedPoses(plain);
        List<Matrix4f> ghostPoses = submittedPoses(output);
        assertEquals(1, ghostPoses.size());
        assertTrue(plainPoses.getFirst().equals(ghostPoses.getFirst(), EPSILON),
                "the ghost should draw at " + plainPoses.getFirst() + " but drew at " + ghostPoses.getFirst());
    }
}
