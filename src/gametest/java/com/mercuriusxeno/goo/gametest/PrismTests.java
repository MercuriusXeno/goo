package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for Crystal's Prism through the real throw path: a throw at a
 * floor grows a prism on its top face and takes one nether quartz, and a
 * thrower holding no quartz cannot throw it.
 * decision prism-blob-becomes-a-milky-quartz-crystal
 * decision ability-json-names-its-reagent
 */
public final class PrismTests {

    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final Identifier CRYSTAL_PRISM = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_prism");
    private static final int NO_TARGET_ENTITY = -1;
    private static final int QUARTZ_HELD = 3;
    /** Long past any flight from the thrower's stand to the floor. */
    private static final int AFTER_ANY_FLIGHT = 60;

    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_prism";
    private static final String QUARTZ_TAKEN = "The throw should take one nether quartz, held %d of %d";
    private static final String NOT_ON_THE_FACE = "The prism should face up off the floor, faced %s";
    private static final String PRISM_WITHOUT_QUARTZ = "A thrower without quartz grew a prism";
    private static final String GOO_SPENT_WITHOUT_QUARTZ = "A refused throw spent %d mB of crystal goo";

    private PrismTests() {
    }

    /**
     * A crystal_prism throw at a stone floor's top face grows goo:prism on that
     * face, and the throw takes one nether quartz from the thrower.
     *
     * @param helper the gametest helper
     */
    public static void prismGrowsOnTheFace(GameTestHelper helper) {
        ServerPlayer player = prismThrower(helper);
        player.getInventory().add(new ItemStack(Items.QUARTZ, QUARTZ_HELD));

        throwPrismAtTheFloor(helper, player);

        int quartz = player.getInventory().countItem(Items.QUARTZ);
        helper.assertTrue(quartz == QUARTZ_HELD - 1, String.format(QUARTZ_TAKEN, quartz, QUARTZ_HELD));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(GooBlocks.PRISM.get(), FLOOR_POS.above());
            Direction facing = helper.getBlockState(FLOOR_POS.above()).getValue(PrismBlock.FACING);
            helper.assertTrue(facing == Direction.UP, String.format(NOT_ON_THE_FACE, facing));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A thrower who knows crystal_prism and holds its goo but no nether quartz
     * throws nothing: no goo is spent and no prism grows.
     *
     * @param helper the gametest helper
     */
    public static void prismRefusedWithoutQuartz(GameTestHelper helper) {
        ServerPlayer player = prismThrower(helper);
        int held = crystalHeld(player);

        throwPrismAtTheFloor(helper, player);

        int spent = held - crystalHeld(player);
        helper.assertTrue(spent == 0, String.format(GOO_SPENT_WITHOUT_QUARTZ, spent));
        helper.runAfterDelay(AFTER_ANY_FLIGHT, () -> {
            helper.assertTrue(helper.getBlockState(FLOOR_POS.above()).isAir(), PRISM_WITHOUT_QUARTZ);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static ServerPlayer prismThrower(GameTestHelper helper) {
        AbilityDefinition prism = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_PRISM);
        helper.assertTrue(prism != null, ABILITY_REQUIRED);
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(player, prism);
        return player;
    }

    private static void throwPrismAtTheFloor(GameTestHelper helper, ServerPlayer player) {
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.CRYSTAL), NO_TARGET_ENTITY,
                helper.absolutePos(FLOOR_POS), Direction.UP.ordinal(), false, CRYSTAL_PRISM.toString(),
                player.getEyePosition()));
    }

    private static int crystalHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.CRYSTAL, 0);
    }
}
