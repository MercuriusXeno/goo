package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.GooInsert;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooSink;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Gametest for the shared in-world goo insert over real stacks and a recording sink
 * (decision block-insert-shared).
 */
public final class GooInsertTests {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final int GOO_COUNT = 5;
    private static final int THOUSANDS_TAKEN = 3;
    private static final int GOO_VOLUME = 3_000;
    private static final int GOO_TAKEN = 1_000;
    private static final String ACCEPTED = "Volume the insert answers";
    private static final String OFFERED_TYPE = "Type offered to the sink";
    private static final String OFFERED_VOLUME = "Volume offered to the sink";
    private static final String THOUSANDS_LEFT = "Volume left in the five-goo";
    private static final String GOO_LEFT = "Goo remainder";
    private static final String REFUSED_WHOLE = "A refused stack stays whole";
    private static final String NON_GOO_WHOLE = "A non-goo stack stays whole";
    private static final String NON_GOO_UNOFFERED = "A non-goo stack is never offered to the sink";

    private GooInsertTests() {
    }

    /**
     * A sink taking part of a goo depletes it by what it took;
     * a refusing sink and a non-goo stack answer 0 and leave the stack whole.
     *
     * @param helper the gametest helper
     */
    public static void pourDepletesByAccepted(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack thousands = GooStacks.createForOutput(ROCK, GOO_COUNT * GooStacks.THOUSAND);
        RecordingSink partial = new RecordingSink(THOUSANDS_TAKEN * GooStacks.THOUSAND);
        helper.assertValueEqual(GooInsert.pour(thousands, player, partial),
                THOUSANDS_TAKEN * GooStacks.THOUSAND, ACCEPTED);
        helper.assertValueEqual(partial.offeredType(), ROCK, OFFERED_TYPE);
        helper.assertValueEqual(partial.offeredVolume(), GOO_COUNT * GooStacks.THOUSAND, OFFERED_VOLUME);
        helper.assertValueEqual(GooStacks.volumeOf(thousands), (GOO_COUNT - THOUSANDS_TAKEN) * GooStacks.THOUSAND, THOUSANDS_LEFT);

        ItemStack goo = GooItem.createWithVolume(ROCK, GOO_VOLUME);
        helper.assertValueEqual(GooInsert.pour(goo, player, new RecordingSink(GOO_TAKEN)),
                GOO_TAKEN, ACCEPTED);
        helper.assertValueEqual(GooItem.getVolume(goo), GOO_VOLUME - GOO_TAKEN, GOO_LEFT);

        ItemStack refused = GooStacks.createForOutput(ROCK, GOO_COUNT * GooStacks.THOUSAND);
        helper.assertValueEqual(GooInsert.pour(refused, player, new RecordingSink(0)), 0, ACCEPTED);
        helper.assertValueEqual(GooStacks.volumeOf(refused), GOO_COUNT * GooStacks.THOUSAND, REFUSED_WHOLE);

        ItemStack stone = new ItemStack(Items.STONE, GOO_COUNT);
        RecordingSink unoffered = new RecordingSink(GOO_COUNT * GooStacks.THOUSAND);
        helper.assertValueEqual(GooInsert.pour(stone, player, unoffered), 0, ACCEPTED);
        helper.assertValueEqual(stone.getCount(), GOO_COUNT, NON_GOO_WHOLE);
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
