package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.astral.LunarArrival;
import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The white the Lunar trip floods the caster's view with, by LunarTrip's white curve: rising as the moon nears
 * covering the view, standing full over the crossing, with LunarWhiteScreen in place of vanilla's terrain loading
 * screen, until the arrival mark reaches the client, then clearing with the caster standing at the far end.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LunarWhiteout {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_white");
    /** The ticks a crossing waits on its arrival mark, outside a loading screen, before it clears anyway. */
    private static final int ARRIVAL_GIVE_UP_TICKS = 100;
    private static final int OPAQUE = 255;
    private static final int WHITE = 0xFFFFFF;

    /** Where the white stands once no fixation holds the caster. */
    private enum Stage {
        /** No trip: the view is clear. */
        CLEAR,
        /** Carried and awaiting the arrival mark: the white stands full. */
        CROSSING,
        /** The arrival mark has landed: the white clears. */
        ARRIVED
    }

    private static Stage stage = Stage.CLEAR;
    /** Whether a fixation held the caster at the last tick, so its end starts the crossing. */
    private static boolean fixated;
    /** The game time of the last arrival mark taken, so a mark already seen ends no later crossing. */
    private static long markTaken;
    private static int ticksInStage;

    private LunarWhiteout() {
    }

    /**
     * @return true while the white must cover a level change: a fixation held the caster at the last tick, or they
     *         are crossing
     */
    static boolean covering() {
        return fixated || stage == Stage.CROSSING;
    }

    /**
     * Shows the white screen in place of vanilla's terrain loading screen for a change Lunar makes, into lunar
     * by the moon and out of it by the blue star.
     * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
     *
     * @param event the transition screen registration event
     */
    @SubscribeEvent
    public static void onRegisterTransitionScreens(RegisterDimensionTransitionScreenEvent event) {
        event.registerIncomingEffect(Lunar.LEVEL, LunarWhiteout::screenFor);
        event.registerOutgoingEffect(Lunar.LEVEL, LunarWhiteout::screenFor);
    }

    /**
     * Registers the white drawn over the view.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, LunarWhiteout::drawWhite);
    }

    /**
     * Follows the trip: a fixation's end starts the crossing, unless the caster let go of it, a fresh arrival mark
     * outside a loading screen ends the crossing, and the fade after it runs out.
     *
     * @param event the client post-tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        LunarFixation fixation = player.getData(GooAttachments.LUNAR_FIXATION);
        if (fixation.stands()) {
            fixated = true;
            enter(Stage.CLEAR);
            return;
        }
        if (fixated && fixation.released()) {
            // decision holding-right-click-pulls-the-moon-and-early-release-cancels: let go early, the trip runs
            // back with no crossing, and the tone stops.
            fixated = false;
            mc.getSoundManager().stop(GooSoundIds.LUNAR_TONE, SoundSource.PLAYERS);
            enter(Stage.CLEAR);
            return;
        }
        if (fixated) {
            fixated = false;
            enter(Stage.CROSSING);
        }
        boolean loading = mc.screen instanceof LevelLoadingScreen;
        if (!loading) {
            ticksInStage++;
        }
        if (stage == Stage.CROSSING) {
            LunarArrival mark = player.hasData(GooAttachments.LUNAR_ARRIVAL)
                    ? player.getData(GooAttachments.LUNAR_ARRIVAL) : LunarArrival.NONE;
            boolean fresh = mark.stands() && mark.arrivedAt() != markTaken;
            if (!loading && (fresh || ticksInStage > ARRIVAL_GIVE_UP_TICKS)) {
                markTaken = mark.arrivedAt();
                enter(Stage.ARRIVED);
            }
        } else if (stage == Stage.ARRIVED && ticksInStage > LunarTrip.WHITE_FADE_TICKS) {
            enter(Stage.CLEAR);
        }
    }

    /**
     * Lets the white go when the client leaves the server.
     *
     * @param event the logging-out event
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        fixated = false;
        enter(Stage.CLEAR);
    }

    private static void enter(Stage next) {
        stage = next;
        ticksInStage = 0;
    }

    private static LevelLoadingScreen screenFor(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason) {
        return covering() ? new LunarWhiteScreen(tracker, reason) : new LevelLoadingScreen(tracker, reason);
    }

    /**
     * How white the view stands this frame.
     *
     * @param partial the frame's partial tick
     * @return none to full
     */
    private static float white(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0F;
        }
        LunarFixation fixation = mc.player.getData(GooAttachments.LUNAR_FIXATION);
        if (fixation.stands() || fixation.released()) {
            return LunarTrip.whiteBeforeCarry(LunarTrip.progress(fixation, mc.level.getGameTime(), partial));
        }
        return switch (stage) {
            case CROSSING -> LunarTrip.whiteAfterCarry(false, 0F);
            case ARRIVED -> LunarTrip.whiteAfterCarry(true, ticksInStage + partial);
            case CLEAR -> 0F;
        };
    }

    private static void drawWhite(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        float white = white(deltaTracker.getGameTimeDeltaPartialTick(false));
        if (white > 0F) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    ARGB.color(Math.round(white * OPAQUE), WHITE));
        }
    }
}
