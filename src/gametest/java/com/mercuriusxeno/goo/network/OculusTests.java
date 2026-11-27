package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.block.alientech.FaceMountedBlock;
import com.mercuriusxeno.goo.block.alientech.OculusBlock;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the oculus block: it registers, drops itself and is unrestricted from melting; a player looking at
 * a placed oculus blinks to stand beside it for less goo than the same blink with no oculus; and a blink at a bare
 * block makes no oculus.
 * decision oculus-prism-becomes-a-hovering-eye
 * decision blink-at-a-prism-spends-into-an-oculus
 * decision utility-blocks-found-in-lunar-are-learned-and-recreated-at-will
 */
@RegistersGameTests
public final class OculusTests {
    private static final String OCULUS_REGISTERS_AND_DROPS = "oculus_registers_and_drops";
    private static final String BLINK_TO_AN_OCULUS_COSTS_LESS = "oculus_blink_lands_beside_it_for_less";
    private static final String BLINK_AT_A_BARE_BLOCK = "oculus_blink_at_a_bare_block_makes_none";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, OCULUS_REGISTERS_AND_DROPS, OculusTests::registersAndDrops);
        GooTestFunctions.registerTest(registrar, BLINK_TO_AN_OCULUS_COSTS_LESS, OculusTests::blinkToAnOculusCostsLess);
        GooTestFunctions.registerTest(registrar, BLINK_AT_A_BARE_BLOCK, OculusTests::blinkAtABareBlockMakesNone);
    }

    private static final Identifier ENDER_BLINK = Identifier.parse("goo:ender_blink");
    /** A floor oculus behind the blinker, out of the cone around the look. */
    private static final BlockPos BEHIND = new BlockPos(0, 1, 0);
    /** A bare block on the floor four blocks east of where the blinker stands, in Blink's range. */
    private static final BlockPos BARE_BLOCK = new BlockPos(5, 1, 3);
    /** Straight up over the player, well inside Blink's node range. */
    private static final int NODE_HEIGHT = 24;
    /** Enough ender goo for two long trips. */
    private static final int EXTRA_GOO = 16 * GooStacks.THOUSAND;
    private static final float LOOKING_STRAIGHT_UP = -90f;
    private static final int NO_ENTITY = -1;
    /** A landing beside the oculus stands one cell off its own, give or take. */
    private static final double BESIDE = 1.05;
    private static final double DROP_REACH = 2.0;
    private static final String ABILITY_REQUIRED = "%s must be loaded";
    private static final String NOT_REGISTERED = "goo:oculus should be an OculusBlock with its BlockItem";
    private static final String WRONG_DROP = "A broken oculus should drop one oculus, but dropped %s";
    private static final String RESTRICTED = "goo:oculus should be neither restricted nor denied from melting";
    private static final String SHOULD_SNAP = "The blink should land beside the oculus at %s, landed at %s";
    private static final String SHOULD_COST_LESS = "A blink to the oculus should drain less than one with none: "
            + "%d to the oculus, %d with none";
    private static final String STRAY_OCULI = "Only the placed oculus should stand after a blink at a bare block, "
            + "found oculi at %s";

    private OculusTests() {
    }

    /**
     * The registries hold goo:oculus and its BlockItem, a broken oculus drops one oculus, and the live goo value
     * table reads it neither restricted nor denied.
     *
     * @param helper the gametest helper
     */
    public static void registersAndDrops(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getValue(GooBlocks.OCULUS.getId());
        helper.assertTrue(block instanceof OculusBlock
                && BuiltInRegistries.ITEM.getValue(GooItems.OCULUS.getId()) instanceof BlockItem item
                && item.getBlock() == block, NOT_REGISTERED);
        standOculus(helper.getLevel(), helper.absolutePos(BEHIND));
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BEHIND);
        Block.dropResources(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), null, ItemStack.EMPTY);
        List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, BEHIND, DROP_REACH);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(GooItems.OCULUS.get())
                && drops.getFirst().getItem().getCount() == 1,
                String.format(WRONG_DROP, drops.stream().map(ItemEntity::getItem).toList()));
        drops.forEach(ItemEntity::discard);
        IGooValueLookup values = GooValues.of(level);
        helper.assertFalse(values.isRestricted(GooItems.OCULUS.getId())
                || values.isDenied(GooItems.OCULUS.getId()), RESTRICTED);
        helper.succeed();
    }

    /**
     * A player looking straight up at an oculus placed on a stone block over them blinks to stand beside it, and
     * drains less ender goo than a second player making the same blink once the oculus and its stone are gone.
     *
     * @param helper the gametest helper
     */
    public static void blinkToAnOculusCostsLess(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos oculus = helper.absolutePos(SelfDeliveryTests.STAND_POS).above(NODE_HEIGHT);
        standOculus(level, oculus);
        // the oculus lists itself as its block entity loads, on the level's next tick
        helper.runAfterDelay(1, () -> blinkUpAndCompare(helper, oculus));
    }

    /**
     * Blinks one player up to the oculus, then a second up the same way once the oculus and its stone are gone, and
     * compares what each drained.
     *
     * @param helper the gametest helper
     * @param oculus the oculus's cell
     */
    private static void blinkUpAndCompare(GameTestHelper helper, BlockPos oculus) {
        ServerLevel level = helper.getLevel();
        ServerPlayer toOculus = blinker(helper);
        toOculus.setXRot(LOOKING_STRAIGHT_UP);
        int toOculusDrained = blinkAndMeasure(toOculus);
        Vec3 landed = toOculus.position();
        level.getServer().getPlayerList().remove(toOculus);
        level.setBlockAndUpdate(oculus, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(oculus.below(), Blocks.AIR.defaultBlockState());
        ServerPlayer plain = blinker(helper);
        plain.setXRot(LOOKING_STRAIGHT_UP);
        int plainDrained = blinkAndMeasure(plain);
        level.getServer().getPlayerList().remove(plain);
        helper.assertTrue(landed.distanceTo(Vec3.atBottomCenterOf(oculus)) < BESIDE,
                String.format(SHOULD_SNAP, oculus, landed));
        helper.assertTrue(toOculusDrained > 0 && toOculusDrained < plainDrained,
                String.format(SHOULD_COST_LESS, toOculusDrained, plainDrained));
        helper.succeed();
    }

    /**
     * A player looking at a bare stone block blinks, and no oculus stands in the bay afterward but the one placed
     * behind them.
     *
     * @param helper the gametest helper
     */
    public static void blinkAtABareBlockMakesNone(GameTestHelper helper) {
        standOculus(helper.getLevel(), helper.absolutePos(BEHIND));
        helper.setBlock(BARE_BLOCK, Blocks.STONE);
        ServerPlayer player = blinker(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(BARE_BLOCK)));
        blinkAndMeasure(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        List<BlockPos> oculi = new ArrayList<>();
        helper.forEveryBlockInStructure(pos -> {
            if (helper.getBlockState(pos).is(GooBlocks.OCULUS.get()) && !pos.equals(BEHIND)) {
                oculi.add(pos);
            }
        });
        helper.assertTrue(oculi.isEmpty() && helper.getBlockState(BEHIND).is(GooBlocks.OCULUS.get()),
                String.format(STRAY_OCULI, oculi));
        helper.succeed();
    }

    /** Stands an oculus on a stone block, pointing up. */
    private static void standOculus(ServerLevel level, BlockPos at) {
        level.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(at, GooBlocks.OCULUS.get().defaultBlockState()
                .setValue(FaceMountedBlock.FACING, Direction.UP));
    }

    /**
     * A mock player who knows Blink and holds enough ender goo for two long trips.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer blinker(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.ENDER);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ENDER, EXTRA_GOO));
        KnownRecipes.teachRequires(player, ability(helper, ENDER_BLINK));
        return player;
    }

    /**
     * Throws Blink from the glove, the path a press takes, and answers the ender goo it drained.
     *
     * @param player the player
     * @return the goo drained, in its smallest units
     */
    private static int blinkAndMeasure(ServerPlayer player) {
        int before = held(player);
        GooGloveItem.setSelection(player.getMainHandItem(), GloveSelection.ofAbility(GooTypes.ENDER, ENDER_BLINK));
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.ENDER), NO_ENTITY,
                player.blockPosition(), NO_ENTITY, false, ENDER_BLINK.toString(), player.getEyePosition()));
        return before - held(player);
    }

    private static AbilityDefinition ability(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }

    private static int held(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ENDER, 0);
    }
}
