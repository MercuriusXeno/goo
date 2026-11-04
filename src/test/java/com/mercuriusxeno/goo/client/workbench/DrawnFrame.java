package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One frame a pane drew onto mocked graphics with a mocked font six pixels a character wide: the texts it drew, as
 * strings or as styled Components, where each stood, at what scale, in which color, whether bold and whether with a
 * drop shadow, and its click areas, so a test clicks a control by its label.
 */
final class DrawnFrame {
    final GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
    final ClickTargets targets = new ClickTargets();
    private final List<Placed> placed = new ArrayList<>();
    // A pose stack deep enough for every nesting a pane draws, and pose-free graphics a test swaps in reads as none.
    private static final int POSE_DEPTH = 16;

    DrawnFrame() {
        when(graphics.guiWidth()).thenReturn(SCREEN_WIDTH);
        when(graphics.guiHeight()).thenReturn(SCREEN_HEIGHT);
        when(graphics.pose()).thenReturn(new Matrix3x2fStack(POSE_DEPTH));
        doAnswer(call -> {
            place(call.getArgument(1), false, call.getArgument(2), call.getArgument(3), call.getArgument(4));
            return null;
        }).when(graphics).text(any(Font.class), anyString(), anyInt(), anyInt(), anyInt());
        doAnswer(call -> {
            place(call.getArgument(1), false, call.getArgument(2), call.getArgument(3), call.getArgument(4),
                    call.getArgument(5));
            return null;
        }).when(graphics).text(any(Font.class), anyString(), anyInt(), anyInt(), anyInt(), anyBoolean());
        doAnswer(call -> {
            Component styled = call.getArgument(1);
            place(styled.getString(), styled.getStyle().isBold(), call.getArgument(2), call.getArgument(3),
                    call.getArgument(4));
            return null;
        }).when(graphics).text(any(Font.class), any(Component.class), anyInt(), anyInt(), anyInt());
    }

    // Records a text where it landed on the screen, the pose it drew under applied.
    private void place(String text, boolean bold, int x, int y, int color) {
        place(text, bold, x, y, color, true);
    }

    // The font's plain text call draws a drop shadow; the call naming the shadow draws one only where asked.
    private void place(String text, boolean bold, int x, int y, int color, boolean shadow) {
        Matrix3x2fStack pose = graphics.pose();
        // A mocked pose a test swaps in answers zeros, which reads as no pose at all.
        boolean posed = pose != null && pose.m00() != 0;
        float scale = posed ? pose.m00() : 1;
        float left = posed ? pose.m20() : 0;
        float top = posed ? pose.m21() : 0;
        placed.add(new Placed(text, scale, bold, Math.round(left + scale * x), Math.round(top + scale * y), color,
                shadow));
    }

    /**
     * @param label a drawn string
     * @return whether it last drew as a Component styled bold
     */
    boolean bold(String label) {
        return placed(label).bold();
    }

    /**
     * One text as it landed on the screen, the pose it drew under applied.
     *
     * @param text   the string
     * @param scale  the pose's scale
     * @param bold   whether it drew as a Component styled bold
     * @param x      its left edge on the screen
     * @param y      its top edge on the screen
     * @param color  its color
     * @param shadow whether it drew with the font's drop shadow
     */
    record Placed(String text, float scale, boolean bold, int x, int y, int color, boolean shadow) {
    }

    /**
     * @param label a drawn string
     * @return where it last landed on the screen and at what scale
     */
    Placed placed(String label) {
        List<Placed> matches = placed.stream().filter(text -> text.text().equals(label)).toList();
        assertTrue(!matches.isEmpty(), label + " not drawn in " + texts());
        return matches.getLast();
    }

    /**
     * @return every text as it landed on the screen, in drawing order
     */
    List<Placed> placements() {
        return List.copyOf(placed);
    }

    /** The mocked screen's width. */
    static final int SCREEN_WIDTH = 600;
    /** The mocked screen's height. */
    static final int SCREEN_HEIGHT = 400;

    /**
     * One text drawn.
     *
     * @param text  the string
     * @param x     its left edge
     * @param y     its top edge
     * @param color its color
     */
    record Text(String text, int x, int y, int color) {
    }

    /**
     * @param label a text drawn inside a button
     * @return the smallest button border around the text's top-left, as left, top, right and bottom, since a lettered
     *     label centers in its button rather than standing a fixed padding in
     */
    int[] buttonAround(Text label) {
        return buttonAround(label.x(), label.y());
    }

    /**
     * @param x a point's x
     * @param y a point's y
     * @return the smallest button border around the point, as left, top, right and bottom, as a segment holding an
     *     icon stands around the icon
     */
    int[] buttonAround(int x, int y) {
        // A button's border, or a button's fill grown by the pixel a border takes, as a term's segment fills between
        // its seams with no border of its own (decision term-draws-a-two-pixel-brighter-border-with-two-tone-seams).
        List<int[]> boxes = new ArrayList<>(fillsOf(ButtonPainter.BORDER));
        fillsOf(ButtonPainter.FILL).forEach(fill -> boxes.add(new int[] {fill[0] - 1, fill[1] - 1, fill[2] + 1,
            fill[3] + 1}));
        int[] around = null;
        for (int[] box : boxes) {
            boolean holds = box[0] <= x && x < box[2] && box[1] <= y && y < box[3];
            if (holds && (around == null || box[2] - box[0] < around[2] - around[0])) {
                around = box;
            }
        }
        assertTrue(around != null, "no button around " + x + "," + y);
        return around;
    }

    /**
     * @param color a fill's color
     * @return each fill drawn in it, as left, top, right and bottom
     */
    List<int[]> fillsOf(int color) {
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        verify(graphics, atLeast(0)).fill(lefts.capture(), tops.capture(), rights.capture(), bottoms.capture(),
                org.mockito.ArgumentMatchers.eq(color));
        List<int[]> fills = new ArrayList<>();
        for (int index = 0; index < lefts.getAllValues().size(); index++) {
            fills.add(new int[] {lefts.getAllValues().get(index), tops.getAllValues().get(index),
                rights.getAllValues().get(index), bottoms.getAllValues().get(index)});
        }
        return fills;
    }

    /**
     * @return a font six pixels a character wide that cuts nothing
     */
    static Font font() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.width(any(FormattedText.class))).thenAnswer(call -> call.<FormattedText>getArgument(0).getString()
                .length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    /**
     * @return every text drawn, in order, where it landed on the screen
     */
    List<Text> drawn() {
        return placed.stream().map(text -> new Text(text.text(), text.x(), text.y(), text.color())).toList();
    }

    /**
     * @return every string drawn, in order
     */
    List<String> texts() {
        return drawn().stream().map(Text::text).toList();
    }

    /**
     * @param label a drawn string
     * @return the last place it was drawn
     */
    Text find(String label) {
        List<Text> matches = drawn().stream().filter(text -> text.text().equals(label)).toList();
        assertTrue(!matches.isEmpty(), label + " not drawn in " + texts());
        return matches.getLast();
    }

    /**
     * @param label a drawn string
     * @return the first place it was drawn, as a name line's rename pencil draws before its value's pencil
     */
    Text findFirst(String label) {
        return drawn().stream().filter(text -> text.text().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError(label + " not drawn in " + texts()));
    }

    /**
     * Clicks inside the text first drawn as the label.
     *
     * @param label a drawn string
     */
    void clickFirst(String label) {
        Text text = findFirst(label);
        assertTrue(targets.click(text.x() + 1, text.y() + 1), label + " took no click");
    }

    /**
     * @param label a drawn string
     * @return how many times it was drawn
     */
    long count(String label) {
        return texts().stream().filter(label::equals).count();
    }

    /**
     * @param label a drawn string
     * @return whether a fill one pixel tall in the link color spans the text last drawn as the label, under its top
     *     and within its line
     */
    boolean underlined(String label) {
        Text text = find(label);
        int right = text.x() + label.length() * 6;
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> colors = ArgumentCaptor.forClass(Integer.class);
        verify(graphics, atLeast(0)).fill(lefts.capture(), tops.capture(), rights.capture(), bottoms.capture(),
                colors.capture());
        for (int index = 0; index < lefts.getAllValues().size(); index++) {
            int top = tops.getAllValues().get(index);
            if (lefts.getAllValues().get(index) == text.x() && rights.getAllValues().get(index) == right
                    && bottoms.getAllValues().get(index) == top + 1 && top > text.y()
                    && top < text.y() + ExpressionLines.TEXT_LINE_HEIGHT
                    && colors.getAllValues().get(index) == ButtonPainter.LINK) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the first scissor the frame set, as left, top, right and bottom, which a pane sets over its list
     */
    int[] firstScissor() {
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        verify(graphics, atLeast(1)).enableScissor(lefts.capture(), tops.capture(), rights.capture(),
                bottoms.capture());
        return new int[] {lefts.getAllValues().getFirst(), tops.getAllValues().getFirst(),
            rights.getAllValues().getFirst(), bottoms.getAllValues().getFirst()};
    }

    /**
     * @param labels strings a list draws, in the order they stand down it
     * @return the y each was first drawn at, which is the list's drawing where a detail pane draws it again after
     */
    List<Integer> topsOf(List<String> labels) {
        List<Text> drawn = drawn();
        return labels.stream().map(label -> drawn.stream().filter(text -> text.text().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError(label + " not drawn in " + texts())).y()).toList();
    }

    /**
     * Draws with shift held, as the screen marks the frame while shift is down.
     *
     * @return this frame
     */
    DrawnFrame holdingShift() {
        targets.holdShift(true);
        return this;
    }

    /**
     * Clicks inside the text last drawn as the label.
     *
     * @param label a drawn string
     */
    void click(String label) {
        Text text = find(label);
        assertTrue(targets.click(text.x() + 1, text.y() + 1), label + " took no click");
    }

    /**
     * Shift + left clicks inside the text last drawn as the label.
     *
     * @param label a drawn string
     */
    void shiftClick(String label) {
        Text text = find(label);
        assertTrue(targets.click(text.x() + 1, text.y() + 1, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT),
                label + " took no shift click");
    }

    /**
     * Right-clicks inside the text last drawn as the label.
     *
     * @param label a drawn string
     * @return whether an area took the right click
     */
    boolean rightClick(String label) {
        Text text = find(label);
        return targets.click(text.x() + 1, text.y() + 1, GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0);
    }
}
