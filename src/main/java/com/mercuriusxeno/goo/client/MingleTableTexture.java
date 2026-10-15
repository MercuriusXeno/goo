package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The texture goo_fluid_surface.fsh reads the frame's {@link MingleTable}
 * from (decision one-pass-blends-every-type): emptied as each frame starts,
 * filled as surfaces submit, and uploaded when a surface's geometry is
 * built, before the draw that reads it. It grows to hold every row a frame
 * writes, so a surface's type count meets no cap.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MingleTableTexture {

    /** The texture the surface render type binds as MingleTable. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Goo.MODID, "dynamic/mingle_table");

    private static final MingleTable TABLE = new MingleTable();
    private static final int INITIAL_ROWS = 64;
    private static final String LABEL = "goo_mingle_table";

    private static @Nullable DynamicTexture texture;
    private static int textureRows;
    private static boolean stale;

    private MingleTableTexture() {
    }

    /**
     * Empties the table as a frame starts.
     *
     * @param event the frame event
     */
    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        TABLE.clear();
    }

    /**
     * Appends a surface's layers to the frame's table.
     *
     * @param layers the layers, the first drawn first
     * @return the packed lightmap coordinates naming the surface's rows
     */
    public static int append(List<MingleLayer> layers) {
        stale = true;
        return TABLE.append(layers);
    }

    /**
     * Uploads every row written since the last upload, growing the texture
     * when the frame wrote more rows than it holds. Called as a surface's
     * geometry is built, outside any render pass.
     */
    public static void uploadIfStale() {
        if (!stale) {
            return;
        }
        if (texture == null || TABLE.rows() > textureRows) {
            replaceTexture(Math.max(INITIAL_ROWS,
                Integer.highestOneBit(Math.max(TABLE.rows(), 1)) * MingleTable.GROWTH_FACTOR));
        }
        NativeImage pixels = texture.getPixels();
        for (int row = 0; row < TABLE.rows(); row++) {
            for (int column = 0; column < MingleTable.COLUMNS; column++) {
                pixels.setPixelABGR(column, row, TABLE.texel(row, column));
            }
        }
        texture.upload();
        stale = false;
    }

    private static void replaceTexture(int rows) {
        texture = new DynamicTexture(() -> LABEL, MingleTable.COLUMNS, rows, true);
        textureRows = rows;
        Minecraft.getInstance().getTextureManager().register(ID, texture);
    }
}
