package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.aging.Aging;
import com.mercuriusxeno.goo.ability.aging.AgingBlocks;
import com.mercuriusxeno.goo.ability.aging.AgingEntry;
import com.mercuriusxeno.goo.ability.aging.AgingTable;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Gametests for Yore's aging blob: a blob landing on a coal block marks it
 * in its chunk, and the block becomes a diamond block once its row's ticks
 * have aged, not before; the mark saves with the chunk and resumes once the
 * chunk loads it back. The aging passes run through
 * {@link Aging#ageLoaded}, the level tick's own path, by the row's ticks at
 * once rather than over days of game time.
 * old-blob-ages-valuables-slowly
 */
public final class AgingTests {

    private static final BlockPos COAL_POS = new BlockPos(2, 1, 2);
    private static final Identifier YORE_AGE = Identifier.parse("goo:yore_age");
    private static final Identifier COAL_BLOCK = Identifier.parse("minecraft:coal_block");
    private static final String ABILITY_REQUIRED = "Ability registry must hold yore_age";
    private static final String ROW_REQUIRED = "The aging table must hold coal_block";
    private static final String SHOULD_STAY_COAL = "The coal block should stay coal short of its ticks";
    private static final String SHOULD_BE_DIAMOND = "The aged coal block should be a diamond block";
    private static final String SHOULD_SAVE_MARK = "The chunk should save the coal block's mark";

    private AgingTests() {
    }

    /**
     * An aged coal block stays coal one tick short of its row's ticks and
     * becomes a diamond block at them.
     *
     * @param helper the gametest helper
     */
    public static void agingCoalBecomesDiamond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int ticks = coalTicks(helper);
        land(helper);
        Aging.ageLoaded(level, ticks - 1);
        helper.assertBlockPresent(Blocks.COAL_BLOCK, COAL_POS);
        Aging.ageLoaded(level, 1);
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, COAL_POS);
        helper.succeed();
    }

    /**
     * An aging coal block's mark saves with its chunk: with the chunk's
     * marks taken out, as an unload saves them, no aging pass reaches it;
     * loaded back from the saved form, the block ages on into a diamond
     * block.
     *
     * @param helper the gametest helper
     */
    public static void agingSurvivesReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int ticks = coalTicks(helper);
        land(helper);
        LevelChunk chunk = level.getChunkAt(helper.absolutePos(COAL_POS));
        Tag saved = AgingBlocks.CODEC.codec().encodeStart(NbtOps.INSTANCE, chunk.getData(GooAttachments.AGING_BLOCKS))
                .getOrThrow();
        chunk.removeData(GooAttachments.AGING_BLOCKS);
        Aging.ageLoaded(level, ticks);
        helper.assertBlockPresent(Blocks.COAL_BLOCK, COAL_POS);
        AgingBlocks loaded = AgingBlocks.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertFalse(loaded.isEmpty(), SHOULD_SAVE_MARK);
        chunk.setData(GooAttachments.AGING_BLOCKS, loaded);
        Aging.noteLoaded(level, chunk);
        Aging.ageLoaded(level, ticks);
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, COAL_POS);
        helper.succeed();
    }

    private static int coalTicks(GameTestHelper helper) {
        AgingEntry coal = AgingTable.entryFor(COAL_BLOCK).orElse(null);
        helper.assertTrue(coal != null, ROW_REQUIRED);
        return coal.ticks();
    }

    /**
     * Sets a coal block and lands an aging blob on its top face, as an
     * arrived throw lands.
     */
    private static void land(GameTestHelper helper) {
        helper.setBlock(COAL_POS, Blocks.COAL_BLOCK);
        AbilityDefinition age = AbilityRegistry.of(helper.getLevel()).getAbility(YORE_AGE);
        helper.assertTrue(age != null, ABILITY_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(COAL_POS), age.gooType(), Direction.UP, age);
        helper.assertBlockPresent(Blocks.COAL_BLOCK, COAL_POS);
    }
}
