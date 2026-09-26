package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for BlobStacks pure utility: constants, volume math, the output rule and
 * the legacy blob volume; the omniblob factory is mocked, so no registry is touched.
 */
class BlobStacksTest {

    // -- Constants --

    /**
     * One blob is 1000 mB.
     */
    @Test
    void mbPerBlobIsOneThousand() {
        assertEquals(1000, BlobStacks.MB_PER_BLOB);
    }

    // -- absorbedVolume (sink-into-omniblob math) --

    /**
     * Absorbing into an empty omniblob yields the source volume.
     */
    @Test
    void absorbedVolume_emptyOmniblob() {
        assertEquals(1000, BlobStacks.absorbedVolume(1000, 0));
    }

    /**
     * Absorbing into a non-empty omniblob sums the two volumes.
     */
    @Test
    void absorbedVolume_withExistingVolume() {
        assertEquals(5500, BlobStacks.absorbedVolume(500, 5000));
    }

    /**
     * Absorbing 64,000 mB into a partial omniblob.
     */
    @Test
    void absorbedVolume_maxBlobStackIntoPartial() {
        assertEquals(64_500, BlobStacks.absorbedVolume(64_000, 500));
    }

    /**
     * Zero source leaves the omniblob unchanged.
     */
    @Test
    void absorbedVolume_zeroSource() {
        assertEquals(5000, BlobStacks.absorbedVolume(0, 5000));
    }

    // -- createForOutput (decision blobs-become-omniblobs) --

    /**
     * Every positive volume, a whole blob count or not, a stack's worth or not,
     * comes out as the omniblob carrying exactly that volume.
     *
     * @param volume the volume asked for, in mB
     */
    @ParameterizedTest
    @ValueSource(ints = {1_000, 64_000, 1_500})
    void createForOutputAnswersOmniblobAtEveryVolume(int volume) {
        ItemStack omniblob = mock(ItemStack.class);
        try (MockedStatic<GooOmniblobItem> factory = mockStatic(GooOmniblobItem.class)) {
            factory.when(() -> GooOmniblobItem.createWithVolume(GooTypes.ROCK, volume)).thenReturn(omniblob);
            assertSame(omniblob, BlobStacks.createForOutput(GooTypes.ROCK, volume));
            factory.verify(() -> GooOmniblobItem.createWithVolume(GooTypes.ROCK, volume));
        }
    }

    // -- legacyAwareVolume (a saved goo:goo_blob stack) --

    /**
     * A saved stack of 3 blobs, loaded as an omniblob with no BLOB_VOLUME, reads 3,000 mB.
     */
    @Test
    void legacyBlobStackReadsCountTimesOneBlob() {
        assertEquals(3_000, BlobStacks.legacyAwareVolume(3, null));
    }

    /**
     * A stack arriving with count above 1 reads count x 1,000 mB whatever volume it carries.
     */
    @Test
    void legacyStackAboveOneReadsCountTimesOneBlob() {
        assertEquals(3_000, BlobStacks.legacyAwareVolume(3, 7));
    }

    /**
     * An omniblob of count 1 reads the volume it carries.
     */
    @Test
    void omniblobReadsStoredVolume() {
        assertEquals(1_500, BlobStacks.legacyAwareVolume(1, 1_500));
    }
}
