package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.OptionalInt;

/**
 * A nine-slice panel to the right of the crosshair while a glove with a
 * selection is held: the icon of the stack the throw deducts from first,
 * the goo type and the mB it holds, and "- N mB", the throw's cost at the
 * aimed target (decision crosshair-panel-shows-source-and-cost).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrosshairFuelPanel {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "crosshair_fuel");
    /** The vanilla effect background sprite, a nine-slice the in-world panels back with too. */
    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/effect_background");
    private static final String MB_SUFFIX = " mB";
    private static final String COST_PREFIX = "- ";
    private static final String GAP = " ";
    private static final int COST_COLOR = 0xFFFF5555;
    /** Gap between the crosshair's center and the panel's left edge. */
    private static final int CROSSHAIR_OFFSET = 10;
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
     * @param heldText the mB the source holds of the type
     * @param costText the throw's cost at the aimed target
     */
    public record FuelRow(ItemStack source, ResourceKey<GooTypeDefinition> type, String heldText, String costText) {
    }

    /**
     * Builds the panel's row from the first source and the aimed cost.
     *
     * @param source the stack the throw deducts from first, or empty
     * @param type   the selected goo type
     * @param held   the mB the source holds of the type
     * @param cost   the throw's cost at the aimed target in mB
     * @return the row
     */
    public static FuelRow fuelRow(ItemStack source, ResourceKey<GooTypeDefinition> type, int held, int cost) {
        return new FuelRow(source, type, held + MB_SUFFIX, COST_PREFIX + cost + MB_SUFFIX);
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
        OptionalInt cost = GloveThrowSender.aimedThrowCost(player);
        if (type == null || cost.isEmpty()) {
            return;
        }
        ItemStack source = GooSourceScanner.firstSource(player, type);
        FuelRow row = fuelRow(source, type, GooSourceScanner.volumeIn(source, type), cost.getAsInt());
        paint(graphics, font, row, graphics.guiWidth() / HALF + CROSSHAIR_OFFSET, graphics.guiHeight() / HALF);
    }

    private static void paint(GuiGraphicsExtractor graphics, Font font, FuelRow row, int left, int centerY) {
        int textWidth = font.width(row.heldText() + GAP + row.costText());
        int width = BORDER + ITEM_SIZE + ICON_GAP + TYPE_ICON_SIZE + ICON_GAP + textWidth + BORDER;
        int height = BORDER + ITEM_SIZE + BORDER;
        int top = centerY - height / HALF;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, left, top, width, height);
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
    }
}
