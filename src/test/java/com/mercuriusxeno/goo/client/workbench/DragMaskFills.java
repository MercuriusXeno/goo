package com.mercuriusxeno.goo.client.workbench;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The fills a frame drew in one color, each as left, top, right and bottom, as a drag mask's shadow and highlights
 * read.
 */
final class DragMaskFills {
    private DragMaskFills() {
    }

    /**
     * @param frame the frame drawn
     * @param color the fill color
     * @return each fill in that color, in the order drawn
     */
    static List<int[]> of(DrawnFrame frame, int color) {
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(frame.graphics, Mockito.atLeast(0)).fill(lefts.capture(), tops.capture(), rights.capture(),
                bottoms.capture(), ArgumentMatchers.eq(color));
        return IntStream.range(0, lefts.getAllValues().size()).mapToObj(index -> new int[] {
            lefts.getAllValues().get(index), tops.getAllValues().get(index), rights.getAllValues().get(index),
            bottoms.getAllValues().get(index)}).toList();
    }
}
