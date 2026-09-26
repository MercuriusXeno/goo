package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.BlobInsert;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.GooSink;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Gametest for the shared in-world blob insert over real stacks and a recording sink
 * (decision block-insert-shared).
 */
public final class BlobInsertTests {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final int BLOB_COUNT = 5;
    private static final int BLOBS_TAKEN = 3;
    private static final int OMNIBLOB_VOLUME = 3_000;
    private static final int OMNIBLOB_TAKEN = 1_000;
    private static final String ACCEPTED = "Volume the insert answers";
    private static final String OFFERED_TYPE = "Type offered to the sink";
    private static final String OFFERED_VOLUME = "Volume offered to the sink";
    private static final String BLOBS_LEFT = "Volume left in the five-blob omniblob";
    private static final String OMNIBLOB_LEFT = "Omniblob remainder";
    private static final String REFUSED_WHOLE = "A refused stack stays whole";
    private static final String NON_GOO_WHOLE = "A non-goo stack stays whole";
    private static final String NON_GOO_UNOFFERED = "A non-goo stack is never offered to the sink";

    private BlobInsertTests() {
    }

    /**
     * A sink taking part of an omniblob depletes it by what it took;
     * a refusing sink and a non-goo stack answer 0 and leave the stack whole.
     *
     * @param helper the gametest helper
     */
    public static void pourDepletesByAccepted(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack blobs = BlobStacks.createForOutput(ROCK, BLOB_COUNT * BlobStacks.MB_PER_BLOB);
        RecordingSink partial = new RecordingSink(BLOBS_TAKEN * BlobStacks.MB_PER_BLOB);
        helper.assertValueEqual(BlobInsert.pour(blobs, player, partial),
                BLOBS_TAKEN * BlobStacks.MB_PER_BLOB, ACCEPTED);
        helper.assertValueEqual(partial.offeredType(), ROCK, OFFERED_TYPE);
        helper.assertValueEqual(partial.offeredVolume(), BLOB_COUNT * BlobStacks.MB_PER_BLOB, OFFERED_VOLUME);
        helper.assertValueEqual(BlobStacks.volumeOf(blobs), (BLOB_COUNT - BLOBS_TAKEN) * BlobStacks.MB_PER_BLOB, BLOBS_LEFT);

        ItemStack omniblob = GooOmniblobItem.createWithVolume(ROCK, OMNIBLOB_VOLUME);
        helper.assertValueEqual(BlobInsert.pour(omniblob, player, new RecordingSink(OMNIBLOB_TAKEN)),
                OMNIBLOB_TAKEN, ACCEPTED);
        helper.assertValueEqual(GooOmniblobItem.getVolume(omniblob), OMNIBLOB_VOLUME - OMNIBLOB_TAKEN, OMNIBLOB_LEFT);

        ItemStack refused = BlobStacks.createForOutput(ROCK, BLOB_COUNT * BlobStacks.MB_PER_BLOB);
        helper.assertValueEqual(BlobInsert.pour(refused, player, new RecordingSink(0)), 0, ACCEPTED);
        helper.assertValueEqual(BlobStacks.volumeOf(refused), BLOB_COUNT * BlobStacks.MB_PER_BLOB, REFUSED_WHOLE);

        ItemStack stone = new ItemStack(Items.STONE, BLOB_COUNT);
        RecordingSink unoffered = new RecordingSink(BLOB_COUNT * BlobStacks.MB_PER_BLOB);
        helper.assertValueEqual(BlobInsert.pour(stone, player, unoffered), 0, ACCEPTED);
        helper.assertValueEqual(stone.getCount(), BLOB_COUNT, NON_GOO_WHOLE);
        helper.assertTrue(unoffered.offeredType() == null, NON_GOO_UNOFFERED);
        helper.succeed();
    }

    /**
     * A sink that takes up to a fixed volume and records what it was offered.
     */
    private static final class RecordingSink implements GooSink {
        private final int takes;
        private ResourceKey<GooTypeDefinition> offeredType;
        private int offeredVolume;

        RecordingSink(int takes) {
            this.takes = takes;
        }

        @Override
        public int accept(ResourceKey<GooTypeDefinition> type, int volume) {
            offeredType = type;
            offeredVolume = volume;
            return Math.min(takes, volume);
        }

        ResourceKey<GooTypeDefinition> offeredType() {
            return offeredType;
        }

        int offeredVolume() {
            return offeredVolume;
        }
    }
}
