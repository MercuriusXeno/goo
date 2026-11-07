package com.mercuriusxeno.goo.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The tendril vignette a sight draws over the camera: the screen's rim fills
 * with a breathing mist, and small tendrils curl in from the edges,
 * undulating, each sight in its own colors. Fungal Sight draws it in
 * magenta, Sculker in sculk teal.
 * sight-lengthens-shift-and-outlines-fungus
 * sculker-sight-persists-where-echoes-have-touched
 */
public final class TendrilVignette {

    /** Bands the mist is drawn in, rim to inside. */
    private static final int MIST_BANDS = 24;
    /** How far in the mist reaches, as a share of the screen's shorter side. */
    private static final float MIST_DEPTH = 0.16f;
    /** The mist's alpha at the very rim. */
    private static final float MIST_RIM_ALPHA = 0.42f;
    /** How much the mist's alpha breathes, as a share of itself. */
    private static final float MIST_BREATH = 0.15f;
    private static final float MIST_BREATH_SPEED = 0.05f;
    /** Tendrils along each edge. */
    private static final int TENDRILS_PER_EDGE = 5;
    /** How far in a tendril reaches, as a share of the screen's shorter side. */
    private static final float TENDRIL_LENGTH = 0.07f;
    /** How far a tendril sways, as a share of the screen's shorter side. */
    private static final float TENDRIL_SWAY = 0.012f;
    private static final float TENDRIL_SWAY_SPEED = 0.12f;
    /** Waves along one tendril. */
    private static final float TENDRIL_WAVES = 1.5f;
    private static final int TENDRIL_STEPS = 18;
    private static final int TENDRIL_DOT = 2;
    private static final float TENDRIL_ALPHA = 0.55f;
    private static final int EDGES = 4;
    private static final float GOLDEN_TURN = 0.618034f;
    private static final float HALF = 0.5f;
    private static final int EDGE_TOP = 0;
    private static final int EDGE_BOTTOM = 1;
    private static final int EDGE_LEFT = 2;
    private static final int OPAQUE = 255;

    private TendrilVignette() {
    }

    /**
     * A sight's vignette colors.
     *
     * @param mistRgb    the mist's color, 0xRRGGBB
     * @param tendrilRgb the tendrils' color, 0xRRGGBB
     */
    public record Look(int mistRgb, int tendrilRgb) {
    }

    /**
     * Draws the mist and the tendrils at a strength.
     *
     * @param graphics the GUI graphics
     * @param time     the game time with the frame's partial tick
     * @param strength how fully the vignette stands, zero to one
     * @param look     the sight's colors
     */
    public static void draw(GuiGraphicsExtractor graphics, float time, float strength, Look look) {
        if (strength <= 0f) {
            return;
        }
        drawMist(graphics, time, strength, look.mistRgb());
        drawTendrils(graphics, time, strength, look.tendrilRgb());
    }

    private static void drawMist(GuiGraphicsExtractor graphics, float time, float strength, int mistRgb) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float depth = Math.min(width, height) * MIST_DEPTH;
        float breath = 1f + MIST_BREATH * Mth.sin(time * MIST_BREATH_SPEED);
        for (int band = 0; band < MIST_BANDS; band++) {
            int outer = Math.round(depth * band / MIST_BANDS);
            int inner = Math.round(depth * (band + 1) / MIST_BANDS);
            float toward = 1f - (float) band / MIST_BANDS;
            float alpha = MIST_RIM_ALPHA * toward * toward * breath * strength;
            int color = ARGB.color(Math.round(Mth.clamp(alpha, 0f, 1f) * OPAQUE), mistRgb);
            drawRing(graphics, outer, inner, width, height, color);
        }
    }

    private static void drawRing(GuiGraphicsExtractor graphics, int outer, int inner, int width, int height,
                                 int color) {
        if (inner <= outer) {
            return;
        }
        graphics.fill(outer, outer, width - outer, inner, color);
        graphics.fill(outer, height - inner, width - outer, height - outer, color);
        graphics.fill(outer, inner, inner, height - inner, color);
        graphics.fill(width - inner, inner, width - outer, height - inner, color);
    }

    private static void drawTendrils(GuiGraphicsExtractor graphics, float time, float strength, int tendrilRgb) {
        Screen screen = new Screen(graphics.guiWidth(), graphics.guiHeight());
        float shorter = Math.min(screen.width(), screen.height());
        for (int edge = 0; edge < EDGES; edge++) {
            for (int i = 0; i < TENDRILS_PER_EDGE; i++) {
                float along = (i + HALF) / TENDRILS_PER_EDGE;
                float phase = (edge * TENDRILS_PER_EDGE + i) * GOLDEN_TURN * Mth.TWO_PI;
                drawTendril(graphics, screen, new Tendril(edge, along, phase), time, shorter, strength, tendrilRgb);
            }
        }
    }

    private static void drawTendril(GuiGraphicsExtractor graphics, Screen screen, Tendril tendril, float time,
                                    float shorter, float strength, int tendrilRgb) {
        for (int step = 0; step <= TENDRIL_STEPS; step++) {
            float share = (float) step / TENDRIL_STEPS;
            float inward = share * shorter * TENDRIL_LENGTH;
            float side = shorter * TENDRIL_SWAY * share
                    * Mth.sin(time * TENDRIL_SWAY_SPEED + tendril.phase() + share * TENDRIL_WAVES * Mth.TWO_PI);
            int alpha = Math.round(Mth.clamp(TENDRIL_ALPHA * (1f - share) * strength, 0f, 1f) * OPAQUE);
            int[] at = screen.from(tendril.edge(), tendril.along(), inward, side);
            graphics.fill(at[0], at[1], at[0] + TENDRIL_DOT, at[1] + TENDRIL_DOT, ARGB.color(alpha, tendrilRgb));
        }
    }

    /**
     * One tendril: the edge it grows from, top, bottom, left or right, how far
     * along that edge it roots, and the phase of its sway.
     *
     * @param edge  zero top, one bottom, two left, three right
     * @param along where along the edge it roots, zero to one
     * @param phase its sway's phase in radians
     */
    private record Tendril(int edge, float along, float phase) {
    }

    /**
     * The screen's size, and the point a step inward from an edge lands at.
     *
     * @param width  the screen's width
     * @param height the screen's height
     */
    private record Screen(int width, int height) {

        int[] from(int edge, float along, float inward, float side) {
            return switch (edge) {
                case EDGE_TOP -> new int[] {Math.round(along * width + side), Math.round(inward)};
                case EDGE_BOTTOM -> new int[] {Math.round(along * width + side), Math.round(height - inward)};
                case EDGE_LEFT -> new int[] {Math.round(inward), Math.round(along * height + side)};
                default -> new int[] {Math.round(width - inward), Math.round(along * height + side)};
            };
        }
    }
}
