package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.typhoon.Breath;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
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
 * Each reserve bubble holds what a vanilla bubble does and pops as vanilla's
 * do, through its last two ticks and then to a small empty bubble, and the
 * row fills from the right as vanilla's does. Where
 * vanilla draws no bubbles, the reserve row stands in their place and takes
 * their row (decision pneuma-overfills-the-air-meter-with-a-brief-airless-immunity).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PneumaAirHud {

    static final Identifier RESERVE_FULL = Identifier.fromNamespaceAndPath(Goo.MODID, "hud/air/reserve_air");
    static final Identifier RESERVE_POPPING = Identifier.fromNamespaceAndPath(Goo.MODID,
            "hud/air/reserve_air_bursting");
    static final Identifier RESERVE_EMPTY = Identifier.fromNamespaceAndPath(Goo.MODID, "hud/air/reserve_air_empty");
    /** The last ticks of a bubble's air it draws popping through, as vanilla's bubbles do. */
    static final int POPPING_TICKS = 2;
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
            boolean drawn = false;
            if (player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer() && player.getVehicle() == null) {
                Breath breath = player.getData(GooAttachments.BREATH);
                drawn = showsReserve(breath, player);
                if (drawn) {
                    paintReserve(graphics, breath, player.getMaxAirSupply() / BUBBLES_PER_ROW, rightHeightBefore);
                }
            }
            vanilla.render(graphics, deltaTracker);
            if (drawn && mc.gui.rightHeight == rightHeightBefore) {
                mc.gui.rightHeight += ROW_HEIGHT;
            }
        };
    }

    /**
     * Whether the reserve row draws: while it banks air, and, once spent, while
     * vanilla's own bubbles show, so its emptied bubbles stand behind them.
     *
     * @param breath the player's breath
     * @param player the local player
     * @return true when the row draws
     */
    private static boolean showsReserve(Breath breath, LocalPlayer player) {
        boolean airShows = player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply();
        return breath.reserveCap() > 0 && (breath.reserve() > 0 || airShows);
    }

    private static void paintReserve(GuiGraphicsExtractor graphics, Breath breath, int bubbleTicks,
                                     int rightHeightBefore) {
        int reserve = breath.reserve();
        int xRight = graphics.guiWidth() / HALF + BAR_HALF_WIDTH;
        int y = graphics.guiHeight() - rightHeightBefore - RESERVE_RISE;
        int slots = reserveSlots(breath.reserveCap(), bubbleTicks);
        for (int slot = 0; slot < slots; slot++) {
            int x = xRight - slot * BUBBLE_SPACING - BUBBLE_SIZE - RESERVE_SHIFT;
            reserveSprite(reserve, slot, bubbleTicks).ifPresent(sprite ->
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, BUBBLE_SIZE, BUBBLE_SIZE));
        }
    }

    /**
     * The reserve slots the row holds: as many bubbles as the reserve's cap, never past a row.
     *
     * @param reserveCap  the reserve's cap, in ticks of breath
     * @param bubbleTicks the air one vanilla bubble holds, in ticks
     * @return the slots, 0 to 10
     */
    static int reserveSlots(int reserveCap, int bubbleTicks) {
        if (reserveCap <= 0 || bubbleTicks <= 0) {
            return 0;
        }
        return Math.min(Mth.positiveCeilDiv(reserveCap, bubbleTicks), BUBBLES_PER_ROW);
    }

    /**
     * The sprite a reserve slot draws, as vanilla draws its air bubbles: whole
     * while more than its last two ticks of air stand in it, popping through
     * those two, then a small empty bubble.
     *
     * @param reserve     the reserve, in ticks of breath
     * @param slot        the slot, from the right
     * @param bubbleTicks the air one vanilla bubble holds, in ticks
     * @return the sprite
     */
    static Optional<Identifier> reserveSprite(int reserve, int slot, int bubbleTicks) {
        int inSlot = reserve - slot * bubbleTicks;
        if (inSlot > POPPING_TICKS) {
            return Optional.of(RESERVE_FULL);
        }
        return Optional.of(inSlot > 0 ? RESERVE_POPPING : RESERVE_EMPTY);
    }
}
