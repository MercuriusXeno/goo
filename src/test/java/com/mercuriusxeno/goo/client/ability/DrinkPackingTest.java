package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regions' uniform blocks pack into one direct buffer kept across frames,
 * grown only to the largest pass, so a frame allocates no direct memory of its
 * own (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkPackingTest {

    private static final int STRIDE = 15872;
    private static final int FEW = 3;
    private static final int MORE = 5;
    private static final int FEWER = 2;

    private static List<DrinkUpload.Block> regions(int count, byte fill) {
        List<DrinkUpload.Block> blocks = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ByteBuffer bytes = ByteBuffer.allocate(DrinkUpload.BYTES);
            for (int at = 0; at < DrinkUpload.BYTES; at++) {
                bytes.put(at, fill);
            }
            blocks.add(new DrinkUpload.Block(bytes, List.of(new DrinkUpload.Proxy(Vec3.ZERO, new Vec3(1, 1, 1))), 0));
        }
        return blocks;
    }

    @Test
    void aFramePacksIntoTheBufferTheLastFrameKept() {
        DrinkPacking packing = new DrinkPacking();
        ByteBuffer first = packing.pack(regions(FEW, (byte) 1), STRIDE);
        ByteBuffer second = packing.pack(regions(FEW, (byte) 2), STRIDE);
        assertSame(first, second);
        assertTrue(second.isDirect());
        assertEquals(0, second.position());
        assertEquals(FEW * STRIDE, second.remaining());
        assertEquals(2, second.get(0));
        assertEquals(2, second.get((FEW - 1) * STRIDE + DrinkUpload.BYTES - 1));
    }

    @Test
    void aLargerPassGrowsTheBufferAndASmallerOneKeepsIt() {
        DrinkPacking packing = new DrinkPacking();
        ByteBuffer few = packing.pack(regions(FEW, (byte) 1), STRIDE);
        ByteBuffer more = packing.pack(regions(MORE, (byte) 3), STRIDE);
        assertNotSame(few, more);
        assertEquals(MORE * STRIDE, more.remaining());
        assertEquals(3, more.get((MORE - 1) * STRIDE));
        ByteBuffer fewer = packing.pack(regions(FEWER, (byte) 4), STRIDE);
        assertSame(more, fewer);
        assertEquals(FEWER * STRIDE, fewer.remaining());
        assertEquals(4, fewer.get((FEWER - 1) * STRIDE));
    }
}
