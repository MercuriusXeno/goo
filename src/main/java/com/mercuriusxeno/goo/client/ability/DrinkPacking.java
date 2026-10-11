package com.mercuriusxeno.goo.client.ability;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * One direct buffer the regions' uniform blocks are packed into each frame,
 * kept across frames and grown only to the largest pass, so a frame allocates
 * no direct memory of its own: a direct buffer is freed only when the heap
 * collects its handle, and the renderer makes too little heap garbage for the
 * collector to run between frames, so a drink of two hundred regions a frame
 * filled the direct memory limit in under an hour. The device copies the
 * bytes into its own buffer at creation, so one pass reusing the buffer after
 * another is safe.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
final class DrinkPacking {

    private ByteBuffer kept = ByteBuffer.allocateDirect(0);

    /**
     * @param blocks the uploads
     * @param stride bytes from one block to the next, the device's alignment
     * @return every block's bytes laid end to end at the stride, in the kept buffer, positioned at its start and
     *     limited to the blocks
     */
    ByteBuffer pack(List<DrinkUpload.Block> blocks, int stride) {
        int size = stride * blocks.size();
        if (kept.capacity() < size) {
            kept = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        }
        kept.clear().limit(size);
        for (int index = 0; index < blocks.size(); index++) {
            kept.put(index * stride, blocks.get(index).bytes(), 0, DrinkUpload.BYTES);
        }
        return kept;
    }
}
