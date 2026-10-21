package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.AffordedCount;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A nine-slice panel at the bottom right of the screen while a glove with a
 * selection is held (decision fuel-panel-sits-at-bottom-right): the goo
 * type's icon, the ability's name and icon, and the uses or seconds the
 * type's holdings afford (decision panel-and-petals-count-uses-or-seconds),
 * followed by the icon of each item the throw consumes, which bounds the
 * count (decision reagent-count-is-the-smaller-bound).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrosshairFuelPanel {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "crosshair_fuel");
    /** Gap between the panel and the gui's right and bottom edges. */
    private static final int EDGE_MARGIN = 4;
    private static final int BORDER = 3;
    private static final int ITEM_SIZE = 16;
    private static final int TYPE_ICON_SIZE = 10;
    private static final int ICON_GAP = 2;
    private static final int HALF = 2;

    private CrosshairFuelPanel() {
    }

    /** One cell of the panel's row, drawn left to right. */
    public sealed interface FuelCell permits TypeIcon, AbilityName, AbilityIcon, Count, ItemIcon {
    }

    /**
     * The goo type's icon.
     *
     * @param type the selected goo type
     */
    public record TypeIcon(ResourceKey<GooTypeDefinition> type) implements FuelCell {
    }

    /**
     * The ability's name.
     *
     * @param translationKey the ability's display name key
     */
    public record AbilityName(String translationKey) implements FuelCell {
    }

    /**
     * The ability's icon.
     *
     * @param texture the icon's whole texture file
     */
    public record AbilityIcon(Identifier texture) implements FuelCell {
    }

    /**
     * The uses or seconds the holdings afford, or a dynamic ability's live mB at the aim.
     *
     * @param text the count, "x3" or "12s", or the mB
     */
    public record Count(String text) implements FuelCell {
    }

    /**
     * A consumed item's icon, beside the count it bounds.
     *
     * @param item the item id
     */
    public record ItemIcon(Identifier item) implements FuelCell {
    }

    /**
     * Builds the panel's row for the selected ability.
     *
     * @param type           the selected goo type
     * @param ability        the selected ability's synced copy
     * @param holdings       the mB the player holds of the type across the inventory
     * @param reagentSets    the whole sets of the ability's consumed items the inventory holds
     * @param aimedCostLabel the live cost at the aim, which a dynamic ability's row reads
     * @return the row's cells, left to right
     */
    public static List<FuelCell> rowCells(ResourceKey<GooTypeDefinition> type, ClientAbility ability, int holdings,
                                          int reagentSets, String aimedCostLabel) {
        List<FuelCell> cells = new ArrayList<>(List.of(new TypeIcon(type), new AbilityName(ability.displayName()),
                new AbilityIcon(ability.iconTexture())));
        // dynamic-costs-keep-the-mb-quantity: a dynamic ability's row reads the live mB at the aim
        cells.add(new Count(AffordedCount.reckon(ability, holdings, reagentSets).map(AffordedCount::label)
                .orElse(aimedCostLabel)));
        ability.consumes().forEach(item -> cells.add(new ItemIcon(item)));
        return cells;
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
        ClientAbility ability = selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
        Optional<String> cost = GloveThrowSender.aimedCostLabel(player);
        if (type == null || ability == null || cost.isEmpty()) {
            return;
        }
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(type, 0);
        List<FuelCell> cells = rowCells(type, ability, holdings, ReagentScanner.setsHeld(player, ability.consumes()),
                cost.get());
        paint(graphics, font, cells, graphics.guiWidth(), graphics.guiHeight());
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

    /**
     * A cell's width in gui pixels.
     *
     * @param font the font text cells measure in
     * @param cell the cell
     * @return the width
     */
    private static int cellWidth(Font font, FuelCell cell) {
        return switch (cell) {
            case TypeIcon ignored -> TYPE_ICON_SIZE;
            case AbilityName name -> font.width(Component.translatable(name.translationKey()));
            case Count count -> font.width(count.text());
            case AbilityIcon ignored -> ITEM_SIZE;
            case ItemIcon ignored -> ITEM_SIZE;
        };
    }

    private static void paintCell(GuiGraphicsExtractor graphics, Font font, FuelCell cell, int x, int rowTop) {
        int textTop = rowTop + (ITEM_SIZE - font.lineHeight) / HALF + 1;
        switch (cell) {
            case TypeIcon typeIcon -> graphics.blit(RenderPipelines.GUI_TEXTURED, PanelPainter.gooIcon(typeIcon.type()),
                    x, rowTop + (ITEM_SIZE - TYPE_ICON_SIZE) / HALF, 0.0f, 0.0f,
                    TYPE_ICON_SIZE, TYPE_ICON_SIZE, TYPE_ICON_SIZE, TYPE_ICON_SIZE);
            case AbilityName name -> graphics.text(font, Component.translatable(name.translationKey()), x, textTop,
                    PanelPainter.TEXT_COLOR);
            case Count count -> graphics.text(font, count.text(), x, textTop, PanelPainter.TEXT_COLOR);
            case AbilityIcon abilityIcon -> graphics.blit(RenderPipelines.GUI_TEXTURED, abilityIcon.texture(),
                    x, rowTop, 0.0f, 0.0f, ITEM_SIZE, ITEM_SIZE, ITEM_SIZE, ITEM_SIZE);
            case ItemIcon itemIcon -> graphics.item(BuiltInRegistries.ITEM.getValue(itemIcon.item()).getDefaultInstance(),
                    x, rowTop);
        }
    }

    private static void paint(GuiGraphicsExtractor graphics, Font font, List<FuelCell> cells, int guiWidth,
            int guiHeight) {
        int rowWidth = cells.stream().mapToInt(cell -> cellWidth(font, cell)).sum() + (cells.size() - 1) * ICON_GAP;
        PanelRectangle rect = bottomRightAnchor(guiWidth, guiHeight, BORDER + rowWidth + BORDER,
                BORDER + ITEM_SIZE + BORDER);
        NineSlice.blit(graphics, rect);
        int x = Math.round(rect.x()) + BORDER;
        int rowTop = Math.round(rect.y()) + BORDER;
        for (FuelCell cell : cells) {
            paintCell(graphics, font, cell, x, rowTop);
            x += cellWidth(font, cell) + ICON_GAP;
        }
    }
}
