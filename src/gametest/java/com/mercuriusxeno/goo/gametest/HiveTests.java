package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for nether's hive combo, the one a full Decay hold converts a
 * prism into: a hive eats a zombie standing in its radius and leaves a creeper
 * outside it whole (decision hive-prism-pillar-eats-the-living).
 */
@RegistersGameTests
public final class HiveTests {
    private static final String HIVE_EATS_THE_APPROACHER = "hive_eats_the_approacher";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HIVE_EATS_THE_APPROACHER, HiveTests::hiveEatsTheApproacher);
    }


    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos PRISM_POS = FLOOR_POS.above();
    /** Two blocks from the prism, inside nether_hive.json's radius of five. */
    private static final BlockPos ZOMBIE_POS = PRISM_POS.east(2);
    /** Eight blocks from the prism, outside the radius. */
    private static final BlockPos CREEPER_POS = PRISM_POS.south(8);
    /** A zombie's twenty health at the hive's two every four ticks is forty ticks, and some. */
    private static final int EAT_TICKS = 60;
    /** Decay, whose on_prism reaction is the hive (decision channeled-hosts-convert-prisms-over-the-hold). */
    private static final String HIVE = "goo:nether_decay";
    private static final String NOT_A_HIVE = "The prism should hold the hive combo, held '%s'";
    private static final String ZOMBIE_LIVES = "The hive should have eaten the zombie in its radius";
    private static final String CREEPER_HURT = "The hive should leave the creeper outside its radius whole";
    private static final String PLAYER_HURT = "The hive should leave a player in its radius whole";
    /** Two blocks the other side of the prism, inside the radius. */
    private static final BlockPos PLAYER_POS = PRISM_POS.west(1).south(2);

    private HiveTests() {
    }

    /**
     * A nether goo lands on a prism beside a zombie, with a creeper out of
     * reach: within the bounded ticks the zombie is dead and the creeper whole.
     *
     * @param helper the gametest helper
     */
    public static void hiveEatsTheApproacher(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        Mob zombie = standOnStone(helper, EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Mob creeper = standOnStone(helper, EntityType.CREEPER, CREEPER_POS);
        Player player = standPlayer(helper);

        PrismBlockEntity prism = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class);
        AbilityDefinition decay = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(HIVE));
        helper.assertTrue(decay != null && prism.runCombo(GooTypes.NETHER, HIVE, PrismCombos.comboSteps(decay)),
                String.format(NOT_A_HIVE, prism.getCombo()));
        helper.runAfterDelay(EAT_TICKS, () -> {
            helper.assertFalse(zombie.isAlive(), ZOMBIE_LIVES);
            helper.assertTrue(creeper.getHealth() == creeper.getMaxHealth(), CREEPER_HURT);
            // mobs only, the operator's ruling on Hive
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), PLAYER_HURT);
            helper.succeed();
        });
    }

    /**
     * Stands a survival player on stone inside the hive's radius.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static Player standPlayer(GameTestHelper helper) {
        helper.setBlock(PLAYER_POS.below(), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(PLAYER_POS)));
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    private static Mob standOnStone(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        return helper.spawnWithNoFreeWill(type, pos);
    }
}
