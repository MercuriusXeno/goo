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
 * place of eating: as the use lands, the lifted cuboid blob expands as a
 * rectangle from where it stands by the glove until it covers the viewport,
 * washes the view in the goo type's colors, and fades. A first-person pass over the camera's
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
    /**
     * How opaque the tinted face and its brighter core each stand at their
     * fullest, about 0.44 stacked, so the world shows through.
     * operator ruling: "Too opaque"
     */
    static final float FACE_ALPHA = 0.3f;
    static final float CORE_ALPHA = 0.2f;
    /** The core's size against the face's. */
    private static final double CORE_SHARE = 0.85;
    /** Where the blob stands on the screen as the wash begins, by the glove: across and down. */
    private static final double BLOB_ACROSS = 0.62;
    private static final double BLOB_DOWN = 0.62;
    /** The blob's half width as the wash begins, against the screen's shorter side. */
    private static final double BLOB_HALF = 0.04;
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
     * @param rim    the blob's face color, the goo type's wheel
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
     * to the screen's edges once it covers the view.
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
     * A rectangle on the screen, the cuboid blob's face as it washes over the view.
     *
     * @param left   its left edge
     * @param top    its top edge
     * @param right  its right edge
     * @param bottom its bottom edge
     */
    record Box(double left, double top, double right, double bottom) {

        /**
         * This box shrunk toward its own middle.
         *
         * @param share the share of its size kept, 0 to 1
         * @return the shrunk box
         */
        Box shrunk(double share) {
            double across = (right - left) * (1 - share) * HALF;
            double down = (bottom - top) * (1 - share) * HALF;
            return new Box(left + across, top + down, right - across, bottom - down);
        }
    }

    /**
     * The wash's rectangle as the blob expands: the cuboid's square at the
     * blob's size where it stands by the glove, each edge easing out until
     * the rectangle covers the viewport.
     * operator ruling: "It's coming out of a cuboid so the circular flare makes no sense"
     *
     * @param expanded   the share expanded, 0 to 1
     * @param width      the screen's width
     * @param height     the screen's height
     * @param gloveRight whether the glove is in the right hand
     * @return the rectangle in screen pixels
     */
    static Box boxAt(double expanded, int width, int height, boolean gloveRight) {
        double middleAcross = width * (gloveRight ? BLOB_ACROSS : 1 - BLOB_ACROSS);
        double middleDown = height * BLOB_DOWN;
        double half = Math.min(width, height) * BLOB_HALF;
        return new Box(lerp(expanded, middleAcross - half, 0), lerp(expanded, middleDown - half, 0),
                lerp(expanded, middleAcross + half, width), lerp(expanded, middleDown + half, height));
    }

    private static double lerp(double share, double from, double to) {
        return from + (to - from) * share;
    }

    /**
     * How opaque the wash stands at its fullest, its tinted face and its
     * brighter core stacked.
     *
     * @return the stacked opacity, 0 to 1
     */
    static float peakOpacity() {
        return 1 - (1 - FACE_ALPHA) * (1 - CORE_ALPHA);
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
     * Draws a wash a time into it: the blob's tinted face and its brighter core.
     *
     * @param graphics the GUI graphics
     * @param shown    the wash
     * @param since    ticks since it began
     */
    private static void draw(GuiGraphicsExtractor graphics, Washing shown, double since) {
        float strength = strengthAt(since);
        Box face = boxAt(expandedAt(since), graphics.guiWidth(), graphics.guiHeight(), shown.right());
        fillBox(graphics, face, ARGB.color(Math.round(FACE_ALPHA * strength * OPAQUE), shown.rim()));
        fillBox(graphics, face.shrunk(CORE_SHARE), ARGB.color(Math.round(CORE_ALPHA * strength * OPAQUE),
                shown.core()));
    }

    private static void fillBox(GuiGraphicsExtractor graphics, Box box, int color) {
        graphics.fill((int) Math.round(box.left()), (int) Math.round(box.top()), (int) Math.round(box.right()),
                (int) Math.round(box.bottom()), color);
    }
}
