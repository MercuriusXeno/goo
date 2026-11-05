package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * The shadow a held drag lays over the screen, cut open over each target the drag may drop on, each target lit by a
 * light highlight (decision drags-mask-everything-but-their-valid-targets). The geometry takes screen bounds and
 * target rectangles alone, so any pane's drag reuses it.
 */
final class DragMask {

    private DragMask() {
    }

    /**
     * A rectangle, its right and bottom edges outside it.
     *
     * @param left   its left edge
     * @param top    its top edge
     * @param right  its right edge
     * @param bottom its bottom edge
     */
    record Rect(int left, int top, int right, int bottom) {
        boolean holds(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        // The part of this rectangle inside the bounds, empty where none is.
        Rect within(Rect bounds) {
            return new Rect(Math.max(left, bounds.left), Math.max(top, bounds.top), Math.min(right, bounds.right),
                    Math.min(bottom, bounds.bottom));
        }

        boolean isEmpty() {
            return right <= left || bottom <= top;
        }
    }

    /**
     * Cuts the screen into horizontal bands at every hole's top and bottom; in each band, the stretches no hole
     * covers are its shadow.
     *
     * @param screen the screen's bounds
     * @param holes  the targets' rectangles, which may overlap or run off the screen
     * @return rectangles covering the screen once, save where a hole stands
     */
    static List<Rect> shadow(Rect screen, List<Rect> holes) {
        List<Rect> inside = holes.stream().map(hole -> hole.within(screen)).filter(hole -> !hole.isEmpty()).toList();
        NavigableSet<Integer> edges = new TreeSet<>(List.of(screen.top(), screen.bottom()));
        inside.forEach(hole -> {
            edges.add(hole.top());
            edges.add(hole.bottom());
        });
        List<Rect> shade = new ArrayList<>();
        Integer top = edges.first();
        for (Integer bottom = edges.higher(top); bottom != null; top = bottom, bottom = edges.higher(bottom)) {
            shadeBand(shade, screen, inside, new Rect(screen.left(), top, screen.right(), bottom));
        }
        return shade;
    }

    // The stretches of one band left of, between and right of the holes spanning it.
    private static void shadeBand(List<Rect> shade, Rect screen, List<Rect> holes, Rect band) {
        int x = screen.left();
        List<Rect> spanning = holes.stream().filter(hole -> hole.top() <= band.top() && hole.bottom() >= band.bottom())
                .sorted(Comparator.comparingInt(Rect::left)).toList();
        for (Rect hole : spanning) {
            if (hole.left() > x) {
                shade.add(new Rect(x, band.top(), hole.left(), band.bottom()));
            }
            x = Math.max(x, hole.right());
        }
        if (x < screen.right()) {
            shade.add(new Rect(x, band.top(), screen.right(), band.bottom()));
        }
    }

    /**
     * Shadows the screen less the holes, then lays the highlight over each hole.
     *
     * @param graphics the frame's graphics
     * @param screen   the screen's bounds
     * @param holes    the targets' rectangles
     */
    static void draw(GuiGraphicsExtractor graphics, Rect screen, List<Rect> holes) {
        for (Rect shade : shadow(screen, holes)) {
            graphics.fill(shade.left(), shade.top(), shade.right(), shade.bottom(), Palette.DRAG_SHADOW);
        }
        for (Rect hole : holes) {
            graphics.fill(hole.left(), hole.top(), hole.right(), hole.bottom(), Palette.DRAG_HIGHLIGHT);
        }
    }
}
