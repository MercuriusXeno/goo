package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;

/**
 * The wash a durative self ability lays over the local player's view in
 * place of eating: as the use lands, one even tint in the goo type's color
 * opens at the middle of the view as a rectangle the view's shape and
 * washes past every edge, then empties from the middle out, a clear
 * rectangle opening at the middle until it reaches the edges and the view
 * is clear again. A first-person pass over the camera's overlays and under
 * the HUD. Kept to this one class so a later ruling can swap the look whole.
 * durative-self-abilities-wash-over-the-view-in-place-of-eating
 * operator ruling: "1 until it fills, and then it empties from the middle out"
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DurativeWash {

    /** The ticks a wash takes from its opening in the middle to the view clear again. */
    static final int WASH_TICKS = 24;
    /** The share of the wash the fill takes to wash past the edges; the rest is its emptying. */
    static final double SWELL_SHARE = 0.3;
    /**
     * How opaque the tint stands, one even layer, so the world shows through.
     * operator ruling: "Too opaque"
     */
    static final float TINT_ALPHA = 0.3f;
    /** How far past the edges the fill washes, against the view's half size. */
    static final double PAST_THE_EDGES = 1.15;
    private static final int OPAQUE = 255;
    private static final double HALF = 0.5;
    private static final double SMOOTH_SQUARE = 3;
    private static final double SMOOTH_CUBE = 2;
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "durative_wash");

    /** The wash playing, or null for none. */
    private static @Nullable Washing washing;

    private DurativeWash() {
    }

    /**
     * A wash playing.
     *
     * @param tint  the wash's color, the goo type's wheel
     * @param began the game time it began
     */
    private record Washing(int tint, double began) {
    }

    /**
     * Begins a wash in a goo type's color as a durative use lands.
     *
     * @param type     the goo type the use spent
     * @param gameTime the game time it landed
     */
    public static void wash(ResourceKey<GooTypeDefinition> type, double gameTime) {
        GooTypeDefinition definition = ClientGooTypes.definition(type);
        if (definition != null) {
            washing = new Washing(definition.wheel(), gameTime);
        }
    }

    /**
     * A rectangle on the screen.
     *
     * @param left   its left edge
     * @param top    its top edge
     * @param right  its right edge
     * @param bottom its bottom edge
     */
    record Box(double left, double top, double right, double bottom) {
    }

    /**
     * How far the fill has opened a time into the wash, against the view's
     * half size: from nothing to past the edges by the fill's end.
     *
     * @param ticksSince ticks since the wash began
     * @return the fill's scale, 0 to {@link #PAST_THE_EDGES}
     */
    static double fillScaleAt(double ticksSince) {
        return PAST_THE_EDGES * smooth(Math.clamp(ticksSince / (WASH_TICKS * SWELL_SHARE), 0.0, 1.0));
    }

    /**
     * How far the hollow has opened a time into the wash, against the view's
     * half size: nothing through the fill, then out to the edges by the wash's end.
     *
     * @param ticksSince ticks since the wash began
     * @return the hollow's scale, 0 to 1
     */
    static double hollowScaleAt(double ticksSince) {
        double emptying = WASH_TICKS * (1 - SWELL_SHARE);
        return smooth(Math.clamp((ticksSince - WASH_TICKS * SWELL_SHARE) / emptying, 0.0, 1.0));
    }

    /**
     * The tinted rectangle a time into the wash, centered on the view.
     *
     * @param ticksSince ticks since the wash began
     * @param width      the screen's width
     * @param height     the screen's height
     * @return the rectangle in screen pixels
     */
    static Box fillAt(double ticksSince, int width, int height) {
        return centered(fillScaleAt(ticksSince), width, height);
    }

    /**
     * The clear rectangle emptying the tint from the middle out, centered on the view.
     *
     * @param ticksSince ticks since the wash began
     * @param width      the screen's width
     * @param height     the screen's height
     * @return the rectangle in screen pixels, of no size through the fill
     */
    static Box hollowAt(double ticksSince, int width, int height) {
        return centered(hollowScaleAt(ticksSince), width, height);
    }

    private static Box centered(double scale, int width, int height) {
        double across = width * HALF * scale;
        double down = height * HALF * scale;
        return new Box(width * HALF - across, height * HALF - down, width * HALF + across, height * HALF + down);
    }

    /**
     * How much of the view the wash covers a time into it: nothing as it
     * opens, all of it once the fill passes the edges, and nothing again as
     * the hollow reaches them.
     *
     * @param ticksSince ticks since the wash began, with the frame's partial tick
     * @return the share of the view covered, 0 to 1
     */
    static float strengthAt(double ticksSince) {
        if (ticksSince <= 0 || ticksSince >= WASH_TICKS) {
            return 0f;
        }
        double filled = Math.min(1.0, fillScaleAt(ticksSince));
        double hollow = Math.min(1.0, hollowScaleAt(ticksSince));
        return (float) (filled * filled - hollow * hollow);
    }

    private static double smooth(double share) {
        return share * share * (SMOOTH_SQUARE - SMOOTH_CUBE * share);
    }

    /**
     * How opaque the wash stands at its fullest: its one even tint.
     *
     * @return the opacity, 0 to 1
     */
    static float peakOpacity() {
        return TINT_ALPHA;
    }

    /**
     * Registers the layer above the camera's overlays.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER_ID, DurativeWash::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Washing shown = washing;
        if (shown == null || mc.level == null || !drawsInView(mc)) {
            return;
        }
        double since = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false) - shown.began();
        if (since >= WASH_TICKS) {
            washing = null;
        } else {
            draw(graphics, shown, since);
        }
    }

    /**
     * Whether the wash draws this frame: in first person, with the HUD shown.
     *
     * @param mc the client
     * @return true where the view takes the wash
     */
    private static boolean drawsInView(Minecraft mc) {
        return !mc.options.hideGui && mc.options.getCameraType().isFirstPerson();
    }

    /**
     * Draws a wash a time into it: the tint between the fill's edges and the
     * hollow's, clipped to the view, as four bands around the hollow.
     *
     * @param graphics the GUI graphics
     * @param shown    the wash
     * @param since    ticks since it began
     */
    private static void draw(GuiGraphicsExtractor graphics, Washing shown, double since) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Bounds fill = Bounds.clipped(fillAt(since, width, height), width, height);
        Bounds hollow = Bounds.clipped(hollowAt(since, width, height), width, height);
        int color = ARGB.color(Math.round(TINT_ALPHA * OPAQUE), shown.tint());
        if (hollow.empty()) {
            graphics.fill(fill.left(), fill.top(), fill.right(), fill.bottom(), color);
            return;
        }
        graphics.fill(fill.left(), fill.top(), fill.right(), hollow.top(), color);
        graphics.fill(fill.left(), hollow.bottom(), fill.right(), fill.bottom(), color);
        graphics.fill(fill.left(), hollow.top(), hollow.left(), hollow.bottom(), color);
        graphics.fill(hollow.right(), hollow.top(), fill.right(), hollow.bottom(), color);
    }

    /**
     * A rectangle rounded to whole screen pixels and clipped to the view.
     *
     * @param left   its left edge
     * @param top    its top edge
     * @param right  its right edge
     * @param bottom its bottom edge
     */
    private record Bounds(int left, int top, int right, int bottom) {

        static Bounds clipped(Box box, int width, int height) {
            return new Bounds(Math.clamp(Math.round(box.left()), 0, width), Math.clamp(Math.round(box.top()), 0, height),
                    Math.clamp(Math.round(box.right()), 0, width), Math.clamp(Math.round(box.bottom()), 0, height));
        }

        boolean empty() {
            return right <= left || bottom <= top;
        }
    }
}
