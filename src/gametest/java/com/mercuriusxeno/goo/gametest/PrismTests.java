package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.FaceSupport;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Crystal's Prism through the real throw path: a throw at a
 * floor grows a prism on its top face from a thrower holding no quartz,
 * since Prism takes no item cost (operator ruling 2026-10-10).
 * decision prism-blob-becomes-a-milky-quartz-crystal
 */
@RegistersGameTests
public final class PrismTests {
    private static final String PRISM_GROWS_ON_THE_FACE = "prism_grows_on_the_face";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PRISM_GROWS_ON_THE_FACE, PrismTests::prismGrowsOnTheFace);
        GooTestFunctions.registerTest(registrar, PRISM_FORMS_ON_THE_RELEASE_TICK, PrismTests::prismFormsOnTheReleaseTick);
        GooTestFunctions.registerTest(registrar, PRISM_GROWS_ONLY_FROM_A_STURDY_FACE,
                PrismTests::prismGrowsOnlyFromASturdyFace);
        GooTestFunctions.registerTest(registrar, PRISM_REFUSED_AT_A_WRONG_FACE, PrismTests::prismRefusedAtAWrongFace);
    }

    private static final String PRISM_GROWS_ONLY_FROM_A_STURDY_FACE = "prism_grows_only_from_a_sturdy_face";

    /**
     * A prism grows from a stone block's top face and from no pane's, fence's
     * or prism's, the rule both the aim highlight and the cast read.
     * decision prism-highlight-draws-only-on-a-face-a-prism-grows-from
     *
     * @param helper the gametest helper
     */
    public static void prismGrowsOnlyFromASturdyFace(GameTestHelper helper) {
        BlockPos stone = new BlockPos(1, 1, 1);
        BlockPos pane = new BlockPos(3, 1, 1);
        BlockPos fence = new BlockPos(1, 1, 3);
        BlockPos prism = new BlockPos(3, 1, 3);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(pane, Blocks.GLASS_PANE);
        helper.setBlock(fence, Blocks.OAK_FENCE);
        helper.setBlock(prism, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        helper.assertTrue(growsFromTheTop(helper, stone), "A prism should grow from stone's top face");
        helper.assertFalse(growsFromTheTop(helper, pane), "A prism should not grow from a pane's top face");
        helper.assertFalse(growsFromTheTop(helper, fence), "A prism should not grow from a fence's top face");
        helper.assertFalse(growsFromTheTop(helper, prism), "A prism should not grow from a prism's top face");
        helper.succeed();
    }

    private static final String PRISM_REFUSED_AT_A_WRONG_FACE = "prism_refused_at_a_wrong_face";

    /**
     * Released at a pane's top face or a prism's, Prism grows nothing and
     * spends nothing; released at stone's top face it grows and pays.
     * decision prism-cast-at-a-wrong-face-is-refused-and-pays-nothing
     *
     * @param helper the gametest helper
     */
    public static void prismRefusedAtAWrongFace(GameTestHelper helper) {
        AbilityDefinition prism = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_PRISM);
        helper.assertTrue(prism != null, ABILITY_REQUIRED);
        BlockPos pane = new BlockPos(3, 1, 1);
        BlockPos grown = new BlockPos(3, 1, 3);
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        helper.setBlock(pane, Blocks.GLASS_PANE);
        helper.setBlock(grown, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(player, prism);
        int held = crystalHeld(player);
        releaseAt(helper, player, pane);
        releaseAt(helper, player, grown);
        int afterRefused = crystalHeld(player);
        boolean paneGrew = helper.getBlockState(pane.above()).is(GooBlocks.PRISM.get());
        boolean prismGrew = helper.getBlockState(grown.above()).is(GooBlocks.PRISM.get());
        releaseAt(helper, player, FLOOR_POS);
        int afterGrown = crystalHeld(player);
        boolean stoneGrew = helper.getBlockState(FLOOR_POS.above()).is(GooBlocks.PRISM.get());
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(paneGrew || prismGrew, "No prism should grow on a pane's or a prism's top face");
        helper.assertTrue(afterRefused == held, "Refused releases should spend nothing, spent " + (held - afterRefused));
        helper.assertTrue(stoneGrew, "A prism should grow on stone's top face");
        helper.assertTrue(afterGrown < afterRefused, "The stone release should spend its cost");
        helper.succeed();
    }

    private static void releaseAt(GameTestHelper helper, ServerPlayer player, BlockPos block) {
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.CRYSTAL), NO_TARGET_ENTITY,
                helper.absolutePos(block), Direction.UP.ordinal(), false, CRYSTAL_PRISM.toString(),
                player.getEyePosition()));
    }

    private static int crystalHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.CRYSTAL, 0);
    }

    private static boolean growsFromTheTop(GameTestHelper helper, BlockPos block) {
        return FaceSupport.growsFrom(helper.getLevel(), helper.absolutePos(block), Direction.UP);
    }

    private static final String PRISM_FORMS_ON_THE_RELEASE_TICK = "prism_forms_on_the_release_tick";

    /**
     * Released at a floor's top face, the prism stands on that face the same
     * tick and no goo was thrown to get it there.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @param helper the gametest helper
     */
    public static void prismFormsOnTheReleaseTick(GameTestHelper helper) {
        AbilityDefinition prism = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_PRISM);
        helper.assertTrue(prism != null, ABILITY_REQUIRED);
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(player, prism);
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.CRYSTAL), NO_TARGET_ENTITY,
                helper.absolutePos(FLOOR_POS), Direction.UP.ordinal(), false, CRYSTAL_PRISM.toString(),
                player.getEyePosition()));
        boolean formed = helper.getBlockState(FLOOR_POS.above()).is(GooBlocks.PRISM.get());
        int thrown = ThrownGoo.countAround(helper);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(formed, "The prism should stand on the floor's top face the release tick");
        helper.assertTrue(thrown == 0, "No goo should be thrown, " + thrown + " thrown goo stand");
        helper.succeed();
    }


    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final Identifier CRYSTAL_PRISM = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_prism");
    private static final int NO_TARGET_ENTITY = -1;

    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_prism";
    private static final String NOT_ON_THE_FACE = "The prism should face up off the floor, faced %s";

    private PrismTests() {
    }

    /**
     * A crystal_prism throw at a stone floor's top face, from a thrower
     * holding no nether quartz, grows goo:prism on that face.
     *
     * @param helper the gametest helper
     */
    public static void prismGrowsOnTheFace(GameTestHelper helper) {
        AbilityDefinition prism = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_PRISM);
        helper.assertTrue(prism != null, ABILITY_REQUIRED);
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(player, prism);

        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.CRYSTAL), NO_TARGET_ENTITY,
                helper.absolutePos(FLOOR_POS), Direction.UP.ordinal(), false, CRYSTAL_PRISM.toString(),
                player.getEyePosition()));

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(GooBlocks.PRISM.get(), FLOOR_POS.above());
            Direction facing = helper.getBlockState(FLOOR_POS.above()).getValue(PrismBlock.FACING);
            helper.assertTrue(facing == Direction.UP, String.format(NOT_ON_THE_FACE, facing));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }
}
