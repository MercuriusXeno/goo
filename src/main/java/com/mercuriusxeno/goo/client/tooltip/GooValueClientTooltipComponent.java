package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.hud.MixedNumberLabel;
import com.mercuriusxeno.goo.client.hud.MixedNumberSpacing;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.NonNull;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Renders a goo value tooltip line: an 11x11 type icon followed by "1.8 Metal" text.
 */
public class GooValueClientTooltipComponent implements ClientTooltipComponent {

    private static final int ICON_SIZE = 8;
    private static final int ICON_GAP = 2;
    private static final int LINE_HEIGHT = 11;
    private static final int ICON_Y_OFFSET = -1;
    /** Scale factor for text to match smaller icons. */
    private static final float TEXT_SCALE = 0.8f;
    /** Separator between amount and type name in tooltip text. */
    private static final String AMOUNT_TYPE_SEPARATOR = " ";
    /** Fully opaque white in ARGB for tooltip text rendering. */
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /** Where the text starts, past the icon and its gap. */
    static final int TEXT_LEFT = ICON_SIZE + ICON_GAP;

    private final Identifier iconTexture;
    private final GooFraction amount;
    private final Component typeName;

    /**
     * Constructs a client tooltip component from the data model.
     *
     * @param data the goo value data to render
     */
    public GooValueClientTooltipComponent(GooValueTooltipComponent data) {
        this.iconTexture = Identifier.fromNamespaceAndPath(
                "goo", "textures/goo/type/" + GooTypes.id(data.type()) + ".png");
        this.amount = data.amount();
        this.typeName = Component.translatable(GooTypeNames.translationKey(data.type()))
                .withStyle(Style.EMPTY.withColor(ClientGooTypes.color(data.type())));
    }

    /**
     * Lays the line out at the tooltip's text scale: the amount and its separator, its mixed number by the
     * spacing rule, then the type's name.
     * every-mixed-number-takes-the-spacing-rule
     *
     * @param amount    the exact amount
     * @param widthOf   the width in pixels the font gives a string at its own size
     * @param nameWidth the type name's width at the font's own size
     * @return each part's place and the line's width, in screen pixels from the component's left
     */
    static Layout layout(GooFraction amount, ToIntFunction<String> widthOf, int nameWidth) {
        MixedNumberLabel amountLabel = MixedNumberLabel.lay(GooFormat.formatMixed(amount) + AMOUNT_TYPE_SEPARATOR,
                widthOf);
        List<MixedNumberSpacing.Piece> pieces = amountLabel.pieces().stream()
                .map(piece -> new MixedNumberSpacing.Piece(piece.text(), TEXT_LEFT + piece.x() * TEXT_SCALE))
                .toList();
        return new Layout(amountLabel, pieces, TEXT_LEFT + amountLabel.width() * TEXT_SCALE,
                TEXT_LEFT + Math.round((amountLabel.width() + nameWidth) * TEXT_SCALE));
    }

    /**
     * A goo value line laid out.
     *
     * @param amount the amount and its separator as laid at the font's own size
     * @param pieces each of the amount's parts at its screen place from the component's left
     * @param nameX  where the type's name starts, from the component's left
     * @param width  the component's width
     */
    record Layout(MixedNumberLabel amount, List<MixedNumberSpacing.Piece> pieces, float nameX, int width) {
    }

    /**
     * Returns the line height matching standard tooltip text.
     *
     * @param font the font used for measurement
     * @return the line height in pixels
     */
    @Override
    public int getHeight(@NonNull Font font) {
        return LINE_HEIGHT;
    }

    /**
     * Returns total width: icon + gap + text.
     *
     * @param font the font used for text width measurement
     * @return the total width in pixels
     */
    @Override
    public int getWidth(Font font) {
        return layout(amount, font::width, font.width(typeName)).width();
    }

    /**
     * Renders the goo icon and value text.
     *
     * @param font the font for text rendering
     * @param x the left x position of the tooltip area
     * @param y the top y position of the tooltip area
     * @param width the available width for rendering
     * @param height the available height for rendering
     * @param guiGraphics the GUI graphics extractor for drawing
     */
    @Override
    public void extractImage(@NonNull Font font, int x, int y, int width, int height, @NonNull GuiGraphicsExtractor guiGraphics) {
        renderIcon(guiGraphics, x, y);
        renderText(font, x, y, guiGraphics);
    }

    /**
     * Blits the 11x11 bordered goo type icon texture.
     *
     * @param guiGraphics the GUI graphics extractor for drawing
     * @param x the left x position
     * @param y the top y position
     */
    private void renderIcon(GuiGraphicsExtractor guiGraphics, int x, int y) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture,
                x, y + ICON_Y_OFFSET, 0.0f, 0.0f,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    /**
     * Draws the formatted value text to the right of the icon.
     *
     * @param font the font for text rendering
     * @param x the left x position
     * @param y the top y position
     * @param guiGraphics the GUI graphics extractor for drawing
     */
    private void renderText(Font font, int x, int y, GuiGraphicsExtractor guiGraphics) {
        MixedNumberLabel amountLabel = layout(amount, font::width, font.width(typeName)).amount();
        Matrix3x2fStack pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(x + TEXT_LEFT, y);
        pose.scale(TEXT_SCALE, TEXT_SCALE);
        amountLabel.draw(guiGraphics, font, 0, 0, TEXT_COLOR, true);
        pose.translate(amountLabel.width(), 0);
        guiGraphics.text(font, typeName, 0, 0, TEXT_COLOR);
        pose.popMatrix();
    }
}
