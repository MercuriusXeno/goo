package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The mingle table a fluid surface's draw binds stays open until that draw
 * runs: Minecraft 26.2 takes a render type's texture views as the draw is
 * prepared, before the surface's geometry is built, so the frame's table
 * texture stands, sized for every row the frame wrote, before any draw is
 * prepared. The texture manager here keeps the game's own register, which
 * closes the texture a registration replaces, and its lookup, which
 * registers a placeholder for an id it holds none for.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
class MingleTableTextureTest {

    /** More rows than the table's first texture holds, so the frame's table outgrows it. */
    private static final int MANY_LAYERS = 80;

    @Test
    void theTableADrawWasPreparedWithStaysOpenThroughTheFrame() {
        Map<Identifier, AbstractTexture> registered = new HashMap<>();
        TextureManager textures = textureManager(registered);
        GpuDevice device = device();
        Minecraft mc = mock(Minecraft.class);
        try (MockedStatic<RenderSystem> render = mockStatic(RenderSystem.class, Mockito.RETURNS_DEEP_STUBS);
             MockedStatic<Minecraft> client = mockStatic(Minecraft.class)) {
            render.when(RenderSystem::getDevice).thenReturn(device);
            when(mc.getTextureManager()).thenReturn(textures);
            client.when(Minecraft::getInstance).thenReturn(mc);

            MingleTableTexture.onFrameStart(null);
            List<MingleLayer> layers = new ArrayList<>();
            for (int i = 0; i < MANY_LAYERS; i++) {
                layers.add(new MingleLayer(new GooRenderUtil.UvRect(0, 0, 1, 1), -1, 1f, i));
            }
            MingleTableTexture.append(layers);
            // The surface's draw is prepared: its render type takes the table's view now.
            GpuTextureView prepared = textures.getTexture(MingleTableTexture.ID).getTextureView();
            // Then its geometry is built.
            MingleTableTexture.uploadIfStale();

            verify(prepared, never()).close();
        }
    }

    private static GpuDevice device() {
        GpuDevice device = mock(GpuDevice.class);
        when(device.createTexture(any(java.util.function.Supplier.class), anyInt(), any(), anyInt(), anyInt(),
                anyInt(), anyInt())).thenAnswer(call -> mock(GpuTexture.class));
        when(device.createTextureView(any())).thenAnswer(call -> mock(GpuTextureView.class));
        when(device.createCommandEncoder()).thenReturn(mock(CommandEncoder.class));
        return device;
    }

    // The game's own register and lookup, over a map.
    private static TextureManager textureManager(Map<Identifier, AbstractTexture> registered) {
        TextureManager textures = mock(TextureManager.class);
        Mockito.doAnswer(call -> {
            AbstractTexture previous = registered.put(call.getArgument(0), call.getArgument(1));
            if (previous != null && previous != call.getArgument(1)) {
                previous.close();
            }
            return null;
        }).when(textures).register(any(Identifier.class), any(AbstractTexture.class));
        when(textures.getTexture(any(Identifier.class))).thenAnswer(call -> registered.computeIfAbsent(
                call.getArgument(0), id -> placeholder()));
        return textures;
    }

    private static AbstractTexture placeholder() {
        AbstractTexture texture = mock(AbstractTexture.class);
        GpuTextureView view = mock(GpuTextureView.class);
        when(texture.getTextureView()).thenReturn(view);
        Mockito.doAnswer(call -> {
            view.close();
            return null;
        }).when(texture).close();
        return texture;
    }
}
