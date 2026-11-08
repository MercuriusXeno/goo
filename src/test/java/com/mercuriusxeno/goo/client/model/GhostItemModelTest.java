package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.item.GhostItem;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The ghost's model resolves the model of the item the ghost stands for and draws it as one faded
 * layer; a stack standing for nothing draws nothing
 * (decision ghost-is-one-item-type-whose-entity-self-destroys).
 */
class GhostItemModelTest {

    private static final Identifier STONE = Identifier.withDefaultNamespace("stone");
    private static final int SEED = 7;

    @Nested
    class Update {
        @Test
        void ghostResolvesTheRepresentedItemIntoOneFadedLayer() {
            ItemStack ghost = mock(ItemStack.class);
            ItemStack stone = mock(ItemStack.class);
            ItemStackRenderState output = mock(ItemStackRenderState.class);
            ItemStackRenderState.LayerRenderState layer = mock(ItemStackRenderState.LayerRenderState.class);
            when(output.newLayer()).thenReturn(layer);
            ItemModelResolver resolver = mock(ItemModelResolver.class);
            try (MockedStatic<GhostItem> ghosts = mockStatic(GhostItem.class)) {
                ghosts.when(() -> GhostItem.represented(ghost)).thenReturn(STONE);
                ghosts.when(() -> GhostItem.representedStack(ghost)).thenReturn(stone);

                new GhostItemModel.Unbaked().bake(null, null)
                        .update(output, ghost, resolver, ItemDisplayContext.GUI, null, null, SEED);
            }
            verify(resolver).updateForTopItem(any(ItemStackRenderState.class), same(stone),
                    eq(ItemDisplayContext.GUI), isNull(), isNull(), eq(SEED));
            verify(output).newLayer();
            verify(layer).setupSpecialModel(same(GhostItemModel.FADED_LAYERS), any(ItemStackRenderState.class));
            verify(output).appendModelIdentityElement(STONE);
        }

        @Test
        void stackStandingForNothingDrawsNothing() {
            ItemStack plain = mock(ItemStack.class);
            ItemStackRenderState output = mock(ItemStackRenderState.class);
            ItemModelResolver resolver = mock(ItemModelResolver.class);

            new GhostItemModel.Unbaked().bake(null, null)
                    .update(output, plain, resolver, ItemDisplayContext.GUI, null, null, SEED);

            verifyNoInteractions(resolver);
            verify(output, never()).newLayer();
        }
    }

    @Nested
    class Fade {
        @Test
        void opaqueTintFadesToTheGhostAlpha() {
            assertEquals(GhostItemModel.GHOST_ALPHA << 24 | 0x123456, GhostItemModel.fade(0xFF123456));
        }

        @Test
        void halfAlphaTintFadesByTheGhostAlphaAgain() {
            assertEquals((0x80 * GhostItemModel.GHOST_ALPHA / 0xFF) << 24 | 0xFFFFFF,
                    GhostItemModel.fade(0x80FFFFFF));
        }
    }
}
