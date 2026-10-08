package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.List;
import java.util.Optional;

/**
 * A nine-slice panel at the bottom right of the screen while a glove with a
 * selection is held (decision fuel-panel-sits-at-bottom-right): the icon of
 * the stack the throw deducts from first, the goo type and the goo it
 * holds, and "- N", the throw's cost at the aimed target (decision
 * crosshair-panel-shows-source-and-cost), followed by the icon of each item
 * the throw consumes (decision ability-json-names-its-reagent).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrosshairFuelPanel {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "crosshair_fuel");
    private static final int BACKGROUND_TEXTURE_SIZE = 24;
    private static final String COST_PREFIX = "- ";
    private static final String GAP = " ";
    private static final int COST_COLOR = 0xFFFF5555;
    /** Gap between the panel and the gui's right and bottom edges. */
    private static final int EDGE_MARGIN = 4;
    private static final int BORDER = 3;
    private static final int ITEM_SIZE = 16;
    private static final int TYPE_ICON_SIZE = 10;
    private static final int ICON_GAP = 2;
    private static final int HALF = 2;

    private CrosshairFuelPanel() {
    }

    /**
     * What the panel's row reads.
     *
     * @param source   the stack the throw deducts from first, or empty when the player holds none
     * @param type     the selected goo type
     * @param heldText the source's volume of the type, in goo
     * @param costText the throw's cost at the aimed target
     */
    public record FuelRow(ItemStack source, ResourceKey<GooTypeDefinition> type, String heldText, String costText) {
    }

    /**
     * Builds the panel's row from the first source and the aimed cost.
     *
     * @param source the stack the throw deducts from first, or empty
     * @param type   the selected goo type
     * @param held   the amount the source holds of the type
     * @param cost   the throw's cost at the aimed target
     * @return the row
     */
    public static FuelRow fuelRow(ItemStack source, ResourceKey<GooTypeDefinition> type, int held, int cost) {
        return fuelRow(source, type, held, GooFormat.formatAmount(cost));
    }

    /**
     * Builds the panel's row from the first source and the aimed cost as
     * already formatted, a held effect's reading "20/s".
     *
     * @param source    the stack the throw deducts from first, or empty
     * @param type      the selected goo type
     * @param held      the amount the source holds of the type
     * @param costLabel the throw's cost at the aimed target, formatted
     * @return the row
     */
    public static FuelRow fuelRow(ItemStack source, ResourceKey<GooTypeDefinition> type, int held, String costLabel) {
        // hud-amounts-read-through-goo-format: the machine panels' goo convention
        return new FuelRow(source, type, GooFormat.formatAmount(held), COST_PREFIX + costLabel);
    }

    /**
     * Registers the panel as a GUI layer drawn above the crosshair.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, CrosshairFuelPanel::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && !mc.options.hideGui && mc.screen == null) {
            renderFor(graphics, mc.font, player);
        }
    }

    private static void renderFor(GuiGraphicsExtractor graphics, Font font, LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        ResourceKey<GooTypeDefinition> type = selection == null ? null : selection.getGooType();
        Optional<String> cost = GloveThrowSender.aimedCostLabel(player);
        if (type == null || cost.isEmpty()) {
            return;
        }
        ItemStack source = GooSourceScanner.firstSource(player, type);
        FuelRow row = fuelRow(source, type, GooSourceScanner.volumeIn(source, type), cost.get());
        List<ItemStack> reagents = GloveThrowSender.aimedReagents(player).stream()
                .map(reagent -> BuiltInRegistries.ITEM.getValue(reagent).getDefaultInstance())
                .toList();
        paint(graphics, font, row, reagents, graphics.guiWidth(), graphics.guiHeight());
    }

    /**
     * The panel's rectangle, its right and bottom edges inset from the gui's by the edge margin.
     *
     * @param guiWidth    the gui's width
     * @param guiHeight   the gui's height
     * @param panelWidth  the panel's width
     * @param panelHeight the panel's height
     * @return the panel's rectangle
     */
    static PanelRectangle bottomRightAnchor(int guiWidth, int guiHeight, int panelWidth, int panelHeight) {
        // fuel-panel-sits-at-bottom-right: out of the center of the screen
        return new PanelRectangle(guiWidth - EDGE_MARGIN - panelWidth, guiHeight - EDGE_MARGIN - panelHeight,
                panelWidth, panelHeight);
    }

    /**
     * The quads the panel's background draws over a rectangle.
     *
     * @param rect the panel rectangle
     * @return the slices, each with its screen rect and texture rect
     */
    static List<NineSlice.Slice> backgroundSlices(PanelRectangle rect) {
        // diagnose-then-fix-aiming-panel-stretch: vanilla ships effect_background with no nine_slice metadata, so blitSprite stretched it whole
        return NineSlice.of(rect);
    }

    private static void blitSlice(GuiGraphicsExtractor graphics, NineSlice.Slice slice) {
        int x = Math.round(slice.x0());
        int y = Math.round(slice.y0());
        graphics.blit(RenderPipelines.GUI_TEXTURED, InWorldHud.BG_TEXTURE, x, y,
                slice.u0() * BACKGROUND_TEXTURE_SIZE, slice.v0() * BACKGROUND_TEXTURE_SIZE,
                Math.round(slice.x1()) - x, Math.round(slice.y1()) - y,
                Math.round((slice.u1() - slice.u0()) * BACKGROUND_TEXTURE_SIZE),
                Math.round((slice.v1() - slice.v0()) * BACKGROUND_TEXTURE_SIZE),
                BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE);
    }

    /**
     * Draws the icon of each item the throw consumes, left to right after the cost.
     * decision ability-json-names-its-reagent
     *
     * @param graphics the gui graphics
     * @param reagents the consumed items
     * @param left     the x the first icon's gap starts at
     * @param top      the row's top
     */
    private static void paintReagents(GuiGraphicsExtractor graphics, List<ItemStack> reagents, int left, int top) {
        int x = left;
        for (ItemStack reagent : reagents) {
            x += ICON_GAP;
            graphics.item(reagent, x, top);
            x += ITEM_SIZE;
        }
    }

    /**
     * The panel's width: the source icon, the type icon, the text and an icon per reagent, inside the border.
     *
     * @param textWidth    the width of the held and cost text
     * @param reagentCount how many items the throw consumes
     * @return the width in gui pixels
     */
    private static int panelWidth(int textWidth, int reagentCount) {
        return BORDER + ITEM_SIZE + ICON_GAP + TYPE_ICON_SIZE + ICON_GAP + textWidth
                + reagentCount * (ICON_GAP + ITEM_SIZE) + BORDER;
    }

    private static void paint(GuiGraphicsExtractor graphics, Font font, FuelRow row, List<ItemStack> reagents,
            int guiWidth, int guiHeight) {
        int textWidth = font.width(row.heldText() + GAP + row.costText());
        PanelRectangle rect = bottomRightAnchor(guiWidth, guiHeight, panelWidth(textWidth, reagents.size()),
                BORDER + ITEM_SIZE + BORDER);
        int left = Math.round(rect.x());
        int top = Math.round(rect.y());
        for (NineSlice.Slice slice : backgroundSlices(rect)) {
            blitSlice(graphics, slice);
        }
        int x = left + BORDER;
        int rowTop = top + BORDER;
        if (!row.source().isEmpty()) {
            graphics.item(row.source(), x, rowTop);
        }
        x += ITEM_SIZE + ICON_GAP;
        graphics.blit(RenderPipelines.GUI_TEXTURED, PanelPainter.gooIcon(row.type()),
                x, rowTop + (ITEM_SIZE - TYPE_ICON_SIZE) / HALF, 0.0f, 0.0f,
                TYPE_ICON_SIZE, TYPE_ICON_SIZE, TYPE_ICON_SIZE, TYPE_ICON_SIZE);
        x += TYPE_ICON_SIZE + ICON_GAP;
        int textTop = rowTop + (ITEM_SIZE - font.lineHeight) / HALF + 1;
        graphics.text(font, row.heldText(), x, textTop, PanelPainter.TEXT_COLOR);
        graphics.text(font, row.costText(), x + font.width(row.heldText() + GAP), textTop, COST_COLOR);
        paintReagents(graphics, reagents, x + textWidth, rowTop);
    }
}
