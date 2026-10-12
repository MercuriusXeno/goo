package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.unstable.BusterEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.gametest.SurvivalPlayers;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Buster eaten from the glove: a punch hurts the living
 * beside the struck one, and a block broken breaks the 3x3x3 around it
 * where each block is no harder than the one broken
 * (decision buster-makes-tools-and-attacks-aoe).
 */
@RegistersGameTests
public final class BusterTests {

    private static final String PUNCH_HITS_THE_AREA = "buster_punch_hits_the_area";
    private static final String BREAKS_BY_HARDNESS = "buster_breaks_the_3x3x3_by_hardness";
    private static final Identifier UNSTABLE_BUSTER = Identifier.parse("goo:unstable_buster");
    private static final int HELD_UNSTABLE = 2000;
    private static final String BUSTER_REQUIRED = "Buster must be loaded";
    private static final String SHOULD_BUST = "Eating Buster should lay its blast radius, laid %.1f";
    private static final String SHOULD_HURT_BESIDE = "A Buster punch should hurt the zombie at %s beside the struck one, health %.1f of %.1f";
    private static final String SHOULD_BREAK = "Breaking the obsidian should break its obsidian neighbor at %s";
    private static final String SHOULD_STAND = "A neighbor harder than obsidian should stand at %s";

    /** The zombie the player punches, inside the bay. */
    private static final BlockPos STRUCK_POS = new BlockPos(3, 1, 3);
    /** Two zombies a block from the struck one, inside Buster's radius. */
    private static final BlockPos[] BESIDE_POS = {new BlockPos(4, 1, 3), new BlockPos(3, 1, 4)};
    /** The middle of the obsidian 3x3x3. */
    private static final BlockPos CUBE_CENTER = new BlockPos(3, 2, 3);
    /** One neighbor of the middle laid in reinforced deepslate, harder than obsidian. */
    private static final BlockPos HARDER_POS = new BlockPos(4, 3, 4);
    /** Where the miner stands, outside the 3x3x3. */
    private static final BlockPos MINER_POS = new BlockPos(1, 1, 1);

    private BusterTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PUNCH_HITS_THE_AREA, BusterTests::busterPunchHitsTheArea);
        GooTestFunctions.registerTest(registrar, BREAKS_BY_HARDNESS, BusterTests::busterBreaksTheCubeByHardness);
    }

    /**
     * A player under Buster punches a zombie: the two zombies beside it lose health too.
     *
     * @param helper the gametest helper
     */
    public static void busterPunchHitsTheArea(GameTestHelper helper) {
        ServerPlayer player = buster(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Mob struck = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, STRUCK_POS);
        Mob[] beside = new Mob[BESIDE_POS.length];
        for (int i = 0; i < BESIDE_POS.length; i++) {
            beside[i] = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BESIDE_POS[i]);
        }

        player.attack(struck);

        helper.getLevel().getServer().getPlayerList().remove(player);
        for (int i = 0; i < beside.length; i++) {
            helper.assertTrue(beside[i].getHealth() < beside[i].getMaxHealth(), String.format(SHOULD_HURT_BESIDE,
                    BESIDE_POS[i], beside[i].getHealth(), beside[i].getMaxHealth()));
        }
        helper.succeed();
    }

    /**
     * A player under Buster mines the middle of an obsidian 3x3x3 with a
     * diamond pickaxe: every obsidian neighbor breaks, and the one reinforced
     * deepslate neighbor, harder than obsidian, stands.
     *
     * @param helper the gametest helper
     */
    public static void busterBreaksTheCubeByHardness(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(CUBE_CENTER.offset(-1, -1, -1), CUBE_CENTER.offset(1, 1, 1))) {
            helper.setBlock(pos, Blocks.OBSIDIAN);
        }
        helper.setBlock(HARDER_POS, Blocks.REINFORCED_DEEPSLATE);
        ServerPlayer player = buster(helper);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(MINER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));

        player.gameMode.destroyBlock(helper.absolutePos(CUBE_CENTER));

        helper.getLevel().getServer().getPlayerList().remove(player);
        for (BlockPos pos : BlockPos.betweenClosed(CUBE_CENTER.offset(-1, -1, -1), CUBE_CENTER.offset(1, 1, 1))) {
            if (pos.equals(HARDER_POS)) {
                helper.assertBlockPresent(Blocks.REINFORCED_DEEPSLATE, pos);
            } else {
                helper.assertTrue(helper.getBlockState(pos).isAir(), String.format(SHOULD_BREAK, pos.immutable()));
            }
        }
        helper.assertTrue(!helper.getBlockState(HARDER_POS).isAir(), String.format(SHOULD_STAND, HARDER_POS));
        helper.succeed();
    }

    /**
     * A survival player who has eaten Buster from the glove.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer buster(GameTestHelper helper) {
        AbilityDefinition buster = AbilityRegistry.of(helper.getLevel()).getAbility(UNSTABLE_BUSTER);
        helper.assertTrue(buster != null, BUSTER_REQUIRED);
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, HELD_UNSTABLE));
        KnownRecipes.teachRequires(player, buster);
        SelfDeliveryTests.invoke(player, GooTypes.UNSTABLE, UNSTABLE_BUSTER);
        SelfDeliveryTests.eatThrough(player);
        float radius = BusterEvents.radiusOf(player);
        helper.assertTrue(radius > 0f, String.format(SHOULD_BUST, radius));
        return player;
    }
}
