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
 * place of eating: as the use lands, the lifted blob expands from where it
 * stands by the glove until it covers the viewport, washes the view in the
 * goo type's colors, and fades. A first-person pass over the camera's
 * overlays and under the HUD. Kept to this one class so a later ruling can
 * swap the look whole.
 * durative-self-abilities-wash-over-the-view-in-place-of-eating
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DurativeWash {

    /** The ticks a wash takes from the blob's first swell to its last fade. */
    static final int WASH_TICKS = 24;
    /** The share of the wash the blob takes to expand over the view; the rest is its fade. */
    static final double SWELL_SHARE = 0.3;
    /** How opaque the wash stands at its fullest, so the world still shows through. */
    static final float FULLEST_ALPHA = 0.7f;
    /** How opaque the brighter core inside the blob's rim stands against the rim. */
    private static final float CORE_ALPHA = 0.55f;
    /** The core's radius against the rim's. */
    private static final double CORE_RADIUS = 0.85;
    /** Where the blob stands on the screen as the wash begins, by the glove: across and down. */
    private static final double BLOB_ACROSS = 0.62;
    private static final double BLOB_DOWN = 0.62;
    /** The blob's radius as the wash begins, against the screen's shorter side. */
    private static final double BLOB_RADIUS = 0.04;
    private static final int OPAQUE = 255;
    /** A row's middle, half a pixel down it. */
    private static final double ROW_MIDDLE = 0.5;
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
     * @param rim    the blob's rim color, the goo type's wheel
     * @param core   the blob's core color, the goo type's bright
     * @param began  the game time it began
     * @param right  whether the glove is in the right hand
     */
    private record Washing(int rim, int core, double began, boolean right) {
    }

    /**
     * Begins a wash in a goo type's colors as a durative use lands.
     *
     * @param type     the goo type the use spent
     * @param gameTime the game time it landed
     * @param right    whether the glove is in the right hand
     */
    public static void wash(ResourceKey<GooTypeDefinition> type, double gameTime, boolean right) {
        GooTypeDefinition definition = ClientGooTypes.definition(type);
        if (definition != null) {
            washing = new Washing(definition.wheel(), definition.bright(), gameTime, right);
        }
    }

    /**
     * How strongly the wash shows a time into it: nothing as it begins,
     * rising to full as the blob covers the view, then falling back to
     * nothing by the wash's end.
     *
     * @param ticksSince ticks since the wash began, with the frame's partial tick
     * @return the strength, 0 to 1
     */
    static float strengthAt(double ticksSince) {
        if (ticksSince <= 0 || ticksSince >= WASH_TICKS) {
            return 0f;
        }
        double share = ticksSince / WASH_TICKS;
        return (float) (share < SWELL_SHARE ? smooth(share / SWELL_SHARE)
                : 1 - smooth((share - SWELL_SHARE) / (1 - SWELL_SHARE)));
    }

    /**
     * How far the blob has expanded a time into the wash: from its own size
     * to past the screen's corners once it covers the view.
     *
     * @param ticksSince ticks since the wash began
     * @return the share of the way from the blob's size to covering the view, 0 to 1
     */
    static double expandedAt(double ticksSince) {
        return smooth(Math.clamp(ticksSince / (WASH_TICKS * SWELL_SHARE), 0.0, 1.0));
    }

    private static double smooth(double share) {
        return share * share * (SMOOTH_SQUARE - SMOOTH_CUBE * share);
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
     * Draws a wash a time into it: the blob's rim and its brighter core.
     *
     * @param graphics the GUI graphics
     * @param shown    the wash
     * @param since    ticks since it began
     */
    private static void draw(GuiGraphicsExtractor graphics, Washing shown, double since) {
        float strength = strengthAt(since);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        double across = shown.right() ? BLOB_ACROSS : 1 - BLOB_ACROSS;
        Disc rim = new Disc(width * across, height * BLOB_DOWN,
                radiusAt(expandedAt(since), width, height, across));
        int alpha = Math.round(FULLEST_ALPHA * strength * OPAQUE);
        fillDisc(graphics, rim, ARGB.color(alpha, shown.rim()));
        fillDisc(graphics, new Disc(rim.x(), rim.y(), rim.radius() * CORE_RADIUS),
                ARGB.color(Math.round(alpha * CORE_ALPHA), shown.core()));
    }

    /**
     * The blob's radius on the screen: its own size as the wash begins, out
     * to the farthest corner from where it stands once it covers the view.
     *
     * @param expanded the share expanded, 0 to 1
     * @param width    the screen's width
     * @param height   the screen's height
     * @param across   the share across the screen the blob stands at
     * @return the radius in screen pixels
     */
    private static double radiusAt(double expanded, int width, int height, double across) {
        double start = Math.min(width, height) * BLOB_RADIUS;
        double corner = Math.hypot(width * Math.max(across, 1 - across), height * BLOB_DOWN);
        return start + (corner - start) * expanded;
    }

    /**
     * A disc on the screen.
     *
     * @param x      its middle across
     * @param y      its middle down
     * @param radius its radius
     */
    private record Disc(double x, double y, double radius) {
    }

    /**
     * Fills a disc row by row, clipped to the screen.
     *
     * @param graphics the GUI graphics
     * @param disc     the disc
     * @param color    its ARGB color
     */
    private static void fillDisc(GuiGraphicsExtractor graphics, Disc disc, int color) {
        int top = Math.max(0, (int) Math.floor(disc.y() - disc.radius()));
        int bottom = Math.min(graphics.guiHeight(), (int) Math.ceil(disc.y() + disc.radius()));
        for (int row = top; row < bottom; row++) {
            double offset = row + ROW_MIDDLE - disc.y();
            double half = Math.sqrt(Math.max(0, disc.radius() * disc.radius() - offset * offset));
            int left = Math.max(0, (int) Math.round(disc.x() - half));
            int right = Math.min(graphics.guiWidth(), (int) Math.round(disc.x() + half));
            if (right > left) {
                graphics.fill(left, row, right, row + 1, color);
            }
        }
    }
}
