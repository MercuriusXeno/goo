package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reserve.Reserve;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.Optional;

/**
 * Draws a standing reserve's banked shanks in jelly's color as a row behind
 * vanilla's hunger bar, before vanilla draws, raised so each peeks out from
 * behind the shank in front of it, the way reserve hearts sit behind the
 * health bar (decision reserve-channels-on-jelly). The bar fills from the
 * right, so the row does too.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReserveShankHud {

    private static final Identifier SHANK_FULL = Identifier.fromNamespaceAndPath(Goo.MODID, "hud/food/reserve_shank_full");
    private static final Identifier SHANK_HALF = Identifier.fromNamespaceAndPath(Goo.MODID, "hud/food/reserve_shank_half");
    /** Pixels a reserve shank sits above, and left of, the shank in front of it. */
    static final int RESERVE_RISE = 2;
    static final int RESERVE_SHIFT = 1;
    private static final int SHANK_SIZE = 9;
    private static final int SLOT_SPACING = 8;
    private static final int BAR_HALF_WIDTH = 91;
    private static final int HALF = 2;

    private ReserveShankHud() {
    }

    /**
     * Wraps the vanilla hunger layer so the reserve row draws behind its shanks.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.FOOD_LEVEL, ReserveShankHud::behindFood);
    }

    private static GuiLayer behindFood(GuiLayer vanilla) {
        return (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer() && player.getVehicle() == null) {
                paintShanks(graphics, player.getData(GooAttachments.RESERVE), mc.gui.rightHeight);
            }
            vanilla.render(graphics, deltaTracker);
        };
    }

    private static void paintShanks(GuiGraphicsExtractor graphics, Reserve reserve, int rightHeightBefore) {
        int slots = Mth.positiveCeilDiv(reserve.shankHalves(), Reserve.HALVES_PER_SLOT);
        int xRight = graphics.guiWidth() / HALF + BAR_HALF_WIDTH;
        int y = graphics.guiHeight() - rightHeightBefore - RESERVE_RISE;
        for (int slot = 0; slot < slots; slot++) {
            int x = slotX(slot, xRight) - RESERVE_SHIFT;
            shankSprite(Reserve.halvesAt(reserve.shankHalves(), slot)).ifPresent(sprite ->
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, SHANK_SIZE, SHANK_SIZE));
        }
    }

    /**
     * The left edge of a hunger slot, counted from the bar's right end as vanilla lays it.
     *
     * @param slot   the slot, from the right
     * @param xRight the bar's right edge
     * @return the slot's left edge
     */
    static int slotX(int slot, int xRight) {
        return xRight - slot * SLOT_SPACING - SHANK_SIZE;
    }

    /**
     * The sprite a reserve shank slot draws: jelly's shank, whole or half by the halves banked there.
     *
     * @param halves the half shanks banked in the slot
     * @return the sprite, or empty for a slot banking none
     */
    static Optional<Identifier> shankSprite(int halves) {
        if (halves >= Reserve.HALVES_PER_SLOT) {
            return Optional.of(SHANK_FULL);
        }
        return halves > 0 ? Optional.of(SHANK_HALF) : Optional.empty();
    }
}
