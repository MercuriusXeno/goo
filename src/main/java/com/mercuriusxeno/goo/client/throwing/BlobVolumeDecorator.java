package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Renders a goo type icon (top-right) on all blob and omniblob item slots.
 * Additionally renders a compact volume label (bottom-right) on omniblobs only,
 * since regular blobs use vanilla stack count display.
 */
public class BlobVolumeDecorator implements IItemDecorator {

    private static final int ICON_RENDER_SIZE = 6;
    private static final float TEXT_SCALE = 0.5f;

    /** Namespace for goo icon textures. */
    private static final String NAMESPACE_GOO = "goo";
    /** Texture path prefix for goo type icons. */
    private static final String ICON_PATH_PREFIX = "textures/goo/type/";
    /** Texture path suffix for goo type icons. */
    private static final String ICON_PATH_SUFFIX = ".png";
    /** Icon X offset from slot left edge. */
    private static final int ICON_X_OFFSET = 10;
    /** Source and dest size for icon blit. */
    private static final int ICON_BLIT_SIZE = 11;
    /** Right edge X offset for volume text. */
    private static final int TEXT_RIGHT_EDGE = 15;
    /** Bottom edge Y offset for volume text. */
    private static final int TEXT_BOTTOM_EDGE = 11;
    /** Full white color for text rendering. */
    private static final int WHITE = 0xFFFFFFFF;

    @Override
    public boolean render(@NonNull GuiGraphicsExtractor graphics, @NonNull Font font, ItemStack stack, int xOffset, int yOffset) {
        if (stack.getItem() instanceof GooBlobItem) {
            renderTypeIcon(graphics, BlobStacks.keyOf(stack), xOffset, yOffset);
            return true;
        }
        return stack.getItem() instanceof GooOmniblobItem
                && renderOmniblob(graphics, font, stack, xOffset, yOffset);
    }

    /**
     * Renders the icon and volume label for an omniblob stack.
     * @param graphics the GUI graphics context
     * @param font the font renderer
     * @param stack the omniblob item stack
     * @param xOffset the horizontal slot position
     * @param yOffset the vertical slot position
     * @return true if decorations were rendered, false if the omniblob is empty
     */
    private boolean renderOmniblob(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        int volume = GooOmniblobItem.getVolume(stack);
        if (volume <= 0) { return false; }
        renderTypeIcon(graphics, BlobStacks.keyOf(stack), xOffset, yOffset);
        renderVolumeLabel(graphics, font, volume, xOffset, yOffset);
        return true;
    }

    /**
     * Blits the goo type icon in the top-right of the slot, at a texture path
     * built from the type's short id. A bundled type's id is a bare path and
     * resolves; a datapack type's id carries its namespace, which no texture
     * path accepts, so such a type reaches here and the path refuses it.
     * Decision type-named-textures is where a type names its own textures.
     *
     * @param graphics the GUI graphics context
     * @param type the goo type to draw, of any namespace, or null where the stack carries none
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    private void renderTypeIcon(GuiGraphicsExtractor graphics, @Nullable ResourceKey<GooTypeDefinition> type, int x, int y) {
        if (type == null) {
            return;
        }
        Identifier texture = Identifier.fromNamespaceAndPath(
                NAMESPACE_GOO, ICON_PATH_PREFIX + GooTypes.id(type) + ICON_PATH_SUFFIX);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x + ICON_X_OFFSET, y, 0.0f, 0.0f,
                ICON_RENDER_SIZE, ICON_RENDER_SIZE,
                ICON_BLIT_SIZE, ICON_BLIT_SIZE, ICON_BLIT_SIZE, ICON_BLIT_SIZE);
    }

    /**
     * Draws the volume label right-aligned at the bottom of the slot, scaled down.
     *
     * @param graphics the GUI graphics context
     * @param font the font renderer
     * @param volume the volume in microblobs
     * @param xOffset the horizontal slot position
     * @param yOffset the vertical slot position
     */
    private void renderVolumeLabel(GuiGraphicsExtractor graphics, Font font,
            int volume, int xOffset, int yOffset) {
        String label = GooTooltipHandler.formatFluidDisplayCompact(volume);
        int textWidth = font.width(label);

        int x = (int) ((xOffset + TEXT_RIGHT_EDGE) / TEXT_SCALE) - textWidth;
        int y = (int) ((yOffset + TEXT_BOTTOM_EDGE) / TEXT_SCALE);

        graphics.pose().pushMatrix();
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
        graphics.text(font, label, x, y, WHITE, true);
        graphics.pose().popMatrix();
    }
}
