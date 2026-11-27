package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.alientech.LightBeaconBlock;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for the light beacon block: it registers, emits the max light where it stands, drops itself and is
 * unrestricted from melting.
 * decision bulb-one-model-max-light-beacon-combo
 * decision utility-blocks-found-in-lunar-are-learned-and-recreated-at-will
 */
@RegistersGameTests
public final class LightBeaconBlockTests {
    private static final String LIGHT_BEACON_REGISTERS_LIGHTS_AND_DROPS = "light_beacon_registers_lights_and_drops";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LIGHT_BEACON_REGISTERS_LIGHTS_AND_DROPS,
                LightBeaconBlockTests::registersLightsAndDrops);
    }

    private static final BlockPos BEACON = new BlockPos(2, 1, 2);
    private static final int MAX_LIGHT = 15;
    private static final double DROP_REACH = 2.0;

    private static final String NOT_REGISTERED = "goo:light_beacon should be a LightBeaconBlock with its BlockItem";
    private static final String DIM = "A light beacon should emit light 15, it emits %d";
    private static final String WRONG_DROP = "A broken light beacon should drop one light beacon, but dropped %s";
    private static final String RESTRICTED = "goo:light_beacon should be neither restricted nor denied from melting";

    private LightBeaconBlockTests() {
    }

    /**
     * The registries hold goo:light_beacon and its BlockItem, a placed one emits light 15, a broken one drops one
     * light beacon, and the live goo value table reads it neither restricted nor denied.
     *
     * @param helper the gametest helper
     */
    public static void registersLightsAndDrops(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getValue(GooBlocks.LIGHT_BEACON.getId());
        helper.assertTrue(block instanceof LightBeaconBlock
                && BuiltInRegistries.ITEM.getValue(GooItems.LIGHT_BEACON.getId()) instanceof BlockItem item
                && item.getBlock() == block, NOT_REGISTERED);
        helper.setBlock(BEACON.below(), Blocks.STONE);
        helper.setBlock(BEACON, GooBlocks.LIGHT_BEACON.get());
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BEACON);
        int emitted = level.getBlockState(pos).getLightEmission(level, pos);
        helper.assertTrue(emitted == MAX_LIGHT, String.format(DIM, emitted));
        Block.dropResources(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), null, ItemStack.EMPTY);
        List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, BEACON, DROP_REACH);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(GooItems.LIGHT_BEACON.get())
                && drops.getFirst().getItem().getCount() == 1,
                String.format(WRONG_DROP, drops.stream().map(ItemEntity::getItem).toList()));
        drops.forEach(ItemEntity::discard);
        IGooValueLookup values = GooValues.of(level);
        helper.assertFalse(values.isRestricted(GooItems.LIGHT_BEACON.getId())
                || values.isDenied(GooItems.LIGHT_BEACON.getId()), RESTRICTED);
        helper.succeed();
    }
}
