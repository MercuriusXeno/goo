package com.mercuriusxeno.goo.gametest;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.entity.CompressedHoard;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * The compression sphere's hoard keeps whole stacks as it fills, and its
 * codec writes and reads back the same stacks (decision
 * black-hole-leaves-a-compression-sphere). An item stack needs the item
 * components a loaded server binds, so these run as gametests.
 */
@RegistersGameTests
public final class CompressedHoardTests {
    private static final String HOARD_KEEPS_WHOLE_STACKS = "hoard_keeps_whole_stacks";
    private static final String HOARD_CODEC_READS_BACK = "hoard_codec_reads_back_its_stacks";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HOARD_KEEPS_WHOLE_STACKS, CompressedHoardTests::hoardKeepsWholeStacks);
        GooTestFunctions.registerTest(registrar, HOARD_CODEC_READS_BACK, CompressedHoardTests::hoardCodecReadsBackItsStacks);
    }


    private static final int OVER_ONE_STACK = 70;
    private static final int FULL_STACK = 64;
    private static final int TOP_UP = 10;
    private static final String SPLIT_WRONG = "70 stone should hold as 64 and 6, held ";
    private static final String TOP_UP_WRONG = "10 more stone should top the 6 up to 16 before the diamond, held ";
    private static final String CODEC_WRONG = "The codec read back other stacks than it wrote: ";

    private CompressedHoardTests() {
    }

    private static List<String> describe(CompressedHoard hoard) {
        return hoard.stacks().stream().map(stack -> entry(stack.getItem(), stack.getCount())).toList();
    }

    private static String entry(Item item, int count) {
        return item + " x" + count;
    }

    /**
     * Stacks past the maximum split into whole stacks, and a later stack
     * tops up the held stack of its item before opening another.
     *
     * @param helper the gametest helper
     */
    public static void hoardKeepsWholeStacks(GameTestHelper helper) {
        CompressedHoard hoard = new CompressedHoard();
        hoard.add(new ItemStack(Items.STONE, OVER_ONE_STACK));
        List<String> split = describe(hoard);
        helper.assertTrue(split.equals(List.of(entry(Items.STONE, FULL_STACK),
                entry(Items.STONE, OVER_ONE_STACK - FULL_STACK))), SPLIT_WRONG + split);

        hoard.add(new ItemStack(Items.DIAMOND));
        hoard.add(new ItemStack(Items.STONE, TOP_UP));
        List<String> topped = describe(hoard);
        helper.assertTrue(topped.equals(List.of(entry(Items.STONE, FULL_STACK),
                entry(Items.STONE, OVER_ONE_STACK - FULL_STACK + TOP_UP), entry(Items.DIAMOND, 1))),
                TOP_UP_WRONG + topped);
        helper.succeed();
    }

    /**
     * The hoard's codec writes the held stacks and reads back the same.
     *
     * @param helper the gametest helper
     */
    public static void hoardCodecReadsBackItsStacks(GameTestHelper helper) {
        CompressedHoard hoard = new CompressedHoard();
        hoard.add(new ItemStack(Items.STONE, OVER_ONE_STACK));
        hoard.add(new ItemStack(Items.DIAMOND));
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

        JsonElement written = CompressedHoard.CODEC.encodeStart(ops, hoard).getOrThrow();
        CompressedHoard read = CompressedHoard.CODEC.parse(ops, written).getOrThrow();

        helper.assertTrue(describe(hoard).equals(describe(read)), CODEC_WRONG + written);
        helper.succeed();
    }
}
