package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
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
 * Draws the air Pneuma banks as a row of reserve bubbles behind vanilla's air
 * bubbles, before vanilla draws, raised so each peeks out from behind the
 * bubble in front of it, the way reserve hearts sit behind the health bar.
 * Each reserve bubble holds what a vanilla bubble does, the one being spent
 * draws popping, and the row fills from the right as vanilla's does. Where
 * vanilla draws no bubbles, the reserve row stands in their place and takes
 * their row (decision pneuma-overfills-the-air-meter-with-a-brief-airless-immunity).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PneumaAirHud {

    static final Identifier RESERVE_FULL = Identifier.fromNamespaceAndPath(Goo.MODID, "hud/air/reserve_air");
    static final Identifier RESERVE_POPPING = Identifier.fromNamespaceAndPath(Goo.MODID,
            "hud/air/reserve_air_bursting");
    /** Pixels a reserve bubble sits above, and left of, the bubble in front of it. */
    static final int RESERVE_RISE = 2;
    static final int RESERVE_SHIFT = 1;
    private static final int BUBBLES_PER_ROW = 10;
    private static final int BUBBLE_SIZE = 9;
    private static final int BUBBLE_SPACING = 8;
    private static final int ROW_HEIGHT = 10;
    private static final int BAR_HALF_WIDTH = 91;
    private static final int HALF = 2;

    private PneumaAirHud() {
    }

    /**
     * Wraps the vanilla air layer so the reserve row draws behind its bubbles.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.AIR_LEVEL, PneumaAirHud::behindAir);
    }

    private static GuiLayer behindAir(GuiLayer vanilla) {
        return (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            int rightHeightBefore = mc.gui.rightHeight;
            int reserve = 0;
            if (player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer() && player.getVehicle() == null) {
                reserve = player.getData(GooAttachments.BREATH).reserve();
                paintReserve(graphics, reserve, player.getMaxAirSupply() / BUBBLES_PER_ROW, rightHeightBefore);
            }
            vanilla.render(graphics, deltaTracker);
            if (reserve > 0 && mc.gui.rightHeight == rightHeightBefore) {
                mc.gui.rightHeight += ROW_HEIGHT;
            }
        };
    }

    private static void paintReserve(GuiGraphicsExtractor graphics, int reserve, int bubbleTicks,
                                     int rightHeightBefore) {
        int xRight = graphics.guiWidth() / HALF + BAR_HALF_WIDTH;
        int y = graphics.guiHeight() - rightHeightBefore - RESERVE_RISE;
        int slots = reserveSlots(reserve, bubbleTicks);
        for (int slot = 0; slot < slots; slot++) {
            int x = xRight - slot * BUBBLE_SPACING - BUBBLE_SIZE - RESERVE_SHIFT;
            reserveSprite(reserve, slot, bubbleTicks).ifPresent(sprite ->
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, BUBBLE_SIZE, BUBBLE_SIZE));
        }
    }

    /**
     * The reserve slots a reserve reaches, a part-spent bubble counting, never past a row.
     *
     * @param reserve     the reserve, in ticks of breath
     * @param bubbleTicks the air one vanilla bubble holds, in ticks
     * @return the slots, 0 to 10
     */
    static int reserveSlots(int reserve, int bubbleTicks) {
        if (reserve <= 0 || bubbleTicks <= 0) {
            return 0;
        }
        return Math.min(Mth.positiveCeilDiv(reserve, bubbleTicks), BUBBLES_PER_ROW);
    }

    /**
     * The sprite a reserve slot draws: whole while the reserve fills it, popping while it is being spent.
     *
     * @param reserve     the reserve, in ticks of breath
     * @param slot        the slot, from the right
     * @param bubbleTicks the air one vanilla bubble holds, in ticks
     * @return the sprite, or empty for a slot past the reserve
     */
    static Optional<Identifier> reserveSprite(int reserve, int slot, int bubbleTicks) {
        int inSlot = reserve - slot * bubbleTicks;
        if (inSlot >= bubbleTicks) {
            return Optional.of(RESERVE_FULL);
        }
        return inSlot > 0 ? Optional.of(RESERVE_POPPING) : Optional.empty();
    }
}
