package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;

/**
 * Draws each prepaid brew effect's clock beside the status bars: a heart
 * brew's left of the health bar, any other brew's right of the food bar,
 * one row up for each further clock on that side, pulsing inside its last
 * thirty seconds. A glove effect shows no clock.
 * brew-runs-the-crawl-prepaid-on-a-shown-clock
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BrewClockHud {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "brew_clock");
    /** Vanilla's status bars sit this far above the gui's bottom edge, and span this far either side of centre. */
    private static final int BAR_TOP_FROM_BOTTOM = 39;
    private static final int BAR_HALF_WIDTH = 91;
    private static final int GAP = 3;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_RGB = 0xFFFFFF;
    private static final int WARNING_RGB = 0xFF5555;
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE_ALPHA = 0xFF;
    /** The gui's width halves at its centre. */
    private static final int HALVES = 2;
    private static final String NO_CLOCK = "";

    private BrewClockHud() {
    }

    /**
     * Registers the clocks as a GUI layer drawn above the food bar.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, LAYER_ID, BrewClockHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = clockedPlayer(mc);
        if (player == null) {
            return;
        }
        float guiTime = mc.gui.getGuiTicks() + deltaTracker.getGameTimeDeltaPartialTick(false);
        ClockFrame frame = new ClockFrame(graphics, mc.font, player.level().getGameTime(), guiTime,
                graphics.guiWidth() / HALVES, graphics.guiHeight() - BAR_TOP_FROM_BOTTOM);
        int heartRow = 0;
        int otherRow = 0;
        for (HeldEffects.Held effect : player.getData(GooAttachments.HELD_EFFECTS).held()) {
            if (effect.prepaid()) {
                boolean hearts = effect.changesHearts();
                frame.paint(effect.expiresAt(), hearts, hearts ? heartRow++ : otherRow++);
            }
        }
    }

    /**
     * The local player whose clocks show: one whose status bars show.
     *
     * @param mc the client
     * @return the player, or null when no status bars show
     */
    private static @Nullable LocalPlayer clockedPlayer(Minecraft mc) {
        LocalPlayer player = mc.player;
        boolean barsShow = !mc.options.hideGui && mc.gameMode != null && mc.gameMode.canHurtPlayer();
        return barsShow ? player : null;
    }

    /**
     * What one frame of clocks reads: where they draw and the times they count from.
     *
     * @param graphics the gui graphics
     * @param font     the font
     * @param now      the game time
     * @param guiTime  the gui time, fraction included
     * @param centre   the gui's horizontal centre
     * @param top      the status bars' top edge
     */
    private record ClockFrame(GuiGraphicsExtractor graphics, Font font, long now, float guiTime, int centre, int top) {

        /**
         * Paints one clock, left of the health bar for a heart brew and right
         * of the food bar for any other, stacking upward by row.
         *
         * @param expiresAt the game time the brew ends at
         * @param hearts    whether the brew lays a heart overlay
         * @param row       the clock's row on its side, from the bottom
         */
        void paint(long expiresAt, boolean hearts, int row) {
            String text = BrewClock.remaining(expiresAt, now).orElse(NO_CLOCK);
            boolean warning = BrewClock.warns(expiresAt, now);
            int x = hearts ? centre - BAR_HALF_WIDTH - GAP - font.width(text) : centre + BAR_HALF_WIDTH + GAP;
            graphics.text(font, text, x, top - row * LINE_HEIGHT, color(warning, BrewClock.pulseAlpha(warning, guiTime)));
        }
    }

    private static int color(boolean warning, float alpha) {
        int rgb = warning ? WARNING_RGB : TEXT_RGB;
        return Math.round(alpha * OPAQUE_ALPHA) << ALPHA_SHIFT | rgb;
    }
}
