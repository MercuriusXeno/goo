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
 * place of eating: as the use lands, a tint in the goo type's colors opens
 * at the middle of the view as a rectangle the view's shape and washes past
 * every edge, then empties from the middle out, a clear rectangle opening at
 * the middle until it reaches the edges and the view is clear again. Each
 * moving front fades in softly rather than cutting off, and the tint crests
 * at the front in the goo's bright color, deepening to its wheel color
 * behind it, so the wash reads as a wave rather than a flat sheet. A first-person pass over the camera's overlays and under
 * the HUD. Kept to this one class so a later ruling can swap the look whole.
 * durative-self-abilities-wash-over-the-view-in-place-of-eating
 * operator ruling: "1 until it fills, and then it empties from the middle out"
 * operator ruling: "it is far too monotone, needs a gradient, a fade, something more subtle"
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DurativeWash {

    /**
     * The ticks a wash takes from its opening in the middle to the view clear again, half a second.
     * operator ruling: "that needs to be significantly faster"
     */
    static final int WASH_TICKS = 10;
    /** The share of the wash the fill takes to wash past the edges; the rest is its emptying. */
    static final double SWELL_SHARE = 0.3;
    /**
     * How opaque the tint stands, one even layer, so the world shows through.
     * operator ruling: "Too opaque"
     */
    static final float TINT_ALPHA = 0.3f;
    /** How far past the edges the fill washes, against the view's half size. */
    static final double PAST_THE_EDGES = 1.15;
    /** How far behind each moving front the tint fades in over, against the view's half size. */
    static final double FEATHER = 0.15;
    /** How far from a moving front its crest reaches, against the view's half size. */
    static final double CREST_REACH = 0.35;
    /** The tint's strength far behind a front, against its strength at the crest. */
    static final float CREST_FLOOR = 0.6f;
    /** The rings the wash draws in from the middle to the edges, fine enough to read as a gradient. */
    private static final int BANDS = 24;
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
     * @param deep   the tint behind a front, the goo type's wheel
     * @param bright the tint at a front's crest, the goo type's bright
     * @param began  the game time it began
     */
    private record Washing(int deep, int bright, double began) {
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
            washing = new Washing(definition.wheel(), definition.bright(), gameTime);
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

    /**
     * How opaque the wash stands at a reach from the middle a time into it:
     * nothing past the fill's front or inside the hollow, fading in softly
     * behind each moving front, strongest at the crest.
     *
     * @param reach      how far from the middle, against the view's half size, 1 at the edges
     * @param ticksSince ticks since the wash began
     * @return the opacity, 0 to {@link #TINT_ALPHA}
     */
    static float alphaAt(double reach, double ticksSince) {
        double filled = smooth(Math.clamp((fillScaleAt(ticksSince) - reach) / FEATHER, 0.0, 1.0));
        double hollow = hollowScaleAt(ticksSince);
        double emptied = hollow <= 0 ? 1 : smooth(Math.clamp((reach - hollow) / FEATHER, 0.0, 1.0));
        double crest = CREST_FLOOR + (1 - CREST_FLOOR) * crestAt(reach, ticksSince);
        return (float) (TINT_ALPHA * filled * emptied * crest);
    }

    /**
     * How near a moving front a reach stands a time into the wash, which
     * brightens the tint there: the fill's front while it opens, and the
     * hollow's edge once it empties.
     *
     * @param reach      how far from the middle, against the view's half size
     * @param ticksSince ticks since the wash began
     * @return 1 at a front, falling to 0 a crest's reach behind it
     */
    static float crestAt(double reach, double ticksSince) {
        double hollow = hollowScaleAt(ticksSince);
        double front = hollow > 0 ? hollow : fillScaleAt(ticksSince);
        return (float) (1 - smooth(Math.clamp(Math.abs(reach - front) / CREST_REACH, 0.0, 1.0)));
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
     * Draws a wash a time into it as nested rings from the middle to the
     * edges, each at the opacity and color its reach takes, so the tint
     * reads as a gradient.
     *
     * @param graphics the GUI graphics
     * @param shown    the wash
     * @param since    ticks since it began
     */
    private static void draw(GuiGraphicsExtractor graphics, Washing shown, double since) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        for (int band = 0; band < BANDS; band++) {
            double middle = (band + HALF) / BANDS;
            int alpha = Math.round(alphaAt(middle, since) * OPAQUE);
            if (alpha > 0) {
                int rgb = ARGB.srgbLerp(crestAt(middle, since), shown.deep(), shown.bright());
                fillRing(graphics, Bounds.clipped(centered((double) band / BANDS, width, height), width, height),
                        Bounds.clipped(centered((band + 1.0) / BANDS, width, height), width, height),
                        ARGB.color(alpha, rgb));
            }
        }
    }

    /**
     * Fills the ring between two nested rectangles as four bands, or the
     * whole outer rectangle where the inner has no size.
     *
     * @param graphics the GUI graphics
     * @param inner    the ring's inner edge
     * @param outer    the ring's outer edge
     * @param color    its ARGB color
     */
    private static void fillRing(GuiGraphicsExtractor graphics, Bounds inner, Bounds outer, int color) {
        if (inner.empty()) {
            graphics.fill(outer.left(), outer.top(), outer.right(), outer.bottom(), color);
            return;
        }
        graphics.fill(outer.left(), outer.top(), outer.right(), inner.top(), color);
        graphics.fill(outer.left(), inner.bottom(), outer.right(), outer.bottom(), color);
        graphics.fill(outer.left(), inner.top(), inner.left(), inner.bottom(), color);
        graphics.fill(inner.right(), inner.top(), outer.right(), inner.bottom(), color);
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
