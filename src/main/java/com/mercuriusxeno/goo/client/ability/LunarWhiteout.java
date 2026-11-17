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
import org.jspecify.annotations.Nullable;

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
    enum Stage {
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
        if (player == null || followFixation(mc, player.getData(GooAttachments.LUNAR_FIXATION))) {
            return;
        }
        boolean loading = mc.screen instanceof LevelLoadingScreen;
        if (!loading) {
            ticksInStage++;
        }
        followStage(player, loading);
    }

    /**
     * Moves the white on from where it stands once no fixation holds the caster: the crossing awaits its arrival
     * mark, and the fade after it runs out to clear.
     *
     * @param player  the caster
     * @param loading whether a loading screen stands
     */
    private static void followStage(LocalPlayer player, boolean loading) {
        if (stage == Stage.CROSSING) {
            awaitArrival(player, loading);
        } else if (stage == Stage.ARRIVED && ticksInStage > LunarTrip.WHITE_FADE_TICKS) {
            enter(Stage.CLEAR);
        }
    }

    /**
     * Follows the fixation the caster's client holds this tick into the stage it sets, stopping the tone of one
     * the caster let go of.
     *
     * @param mc       the client
     * @param fixation the fixation the caster's client holds this tick
     * @return true where the fixation cleared the white, which ends the tick
     */
    private static boolean followFixation(Minecraft mc, LunarFixation fixation) {
        boolean wasFixated = fixated;
        fixated = fixation.stands();
        Stage set = stageTheFixationSets(fixation, wasFixated);
        if (set == null) {
            return false;
        }
        enter(set);
        if (wasFixated && fixation.released()) {
            // decision holding-right-click-pulls-the-moon-and-early-release-cancels
            // Let go early, the trip runs back with no crossing, and the tone stops.
            mc.getSoundManager().stop(GooSoundIds.LUNAR_TONE, SoundSource.PLAYERS);
        }
        return set == Stage.CLEAR;
    }

    /**
     * Ends the crossing on a fresh arrival mark outside a loading screen, or once the crossing has waited too long.
     *
     * @param player  the caster
     * @param loading whether a loading screen stands
     */
    private static void awaitArrival(LocalPlayer player, boolean loading) {
        LunarArrival mark = player.hasData(GooAttachments.LUNAR_ARRIVAL)
                ? player.getData(GooAttachments.LUNAR_ARRIVAL) : LunarArrival.NONE;
        boolean fresh = mark.stands() && mark.arrivedAt() != markTaken;
        if (!loading && (fresh || ticksInStage > ARRIVAL_GIVE_UP_TICKS)) {
            markTaken = mark.arrivedAt();
            enter(Stage.ARRIVED);
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

    /**
     * The stage a client tick's fixation sets: clear while it stands, clear once the caster let go of it, the
     * crossing once a fixation that held the caster at the last tick has ended otherwise, and none where no
     * fixation held them, leaving the stage to the arrival mark and the fade.
     * decision holding-right-click-pulls-the-moon-and-early-release-cancels
     *
     * @param fixation the fixation the caster's client holds this tick
     * @param fixated  whether a fixation held the caster at the last tick
     * @return the stage set, or null where the fixation sets none
     */
    static @Nullable Stage stageTheFixationSets(LunarFixation fixation, boolean fixated) {
        if (fixation.stands()) {
            return Stage.CLEAR;
        }
        if (!fixated) {
            return null;
        }
        return fixation.released() ? Stage.CLEAR : Stage.CROSSING;
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
        return whiteAt(mc.player.getData(GooAttachments.LUNAR_FIXATION), fixated, stage, ticksInStage,
                mc.level.getGameTime(), partial);
    }

    /**
     * How white the view stands at a frame, from the fixation the client holds and where the white stands: the
     * before-carry curve while the fixation stands or runs back, full once a fixation that held the caster at the
     * last tick has gone, and the stage's white otherwise.
     *
     * @param fixation     the fixation the caster's client holds this frame
     * @param fixated      whether a fixation held the caster at the last client tick
     * @param stage        where the white stands
     * @param ticksInStage the client ticks the stage has stood
     * @param gameTime     the client's game time
     * @param partial      the frame's partial tick
     * @return none to full
     */
    static float whiteAt(LunarFixation fixation, boolean fixated, Stage stage, int ticksInStage, long gameTime,
            float partial) {
        if (fixation.stands() || fixation.released()) {
            return LunarTrip.whiteBeforeCarry(LunarTrip.progress(fixation, gameTime, partial));
        }
        // decision diagnose-and-correct-the-whiteout-blink-before-the-carry
        // The server removes the fixation before the carry, and Minecraft draws frames between taking that packet
        // and the next client tick, which alone starts the crossing; the white stands full over that gap.
        return fixated ? LunarTrip.whiteAfterCarry(false, 0F) : stageWhite(stage, ticksInStage, partial);
    }

    private static float stageWhite(Stage stage, int ticksInStage, float partial) {
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
