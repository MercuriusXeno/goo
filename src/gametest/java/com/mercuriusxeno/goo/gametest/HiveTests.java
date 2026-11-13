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
 * prism into: a hive eats a vindicator standing in its radius (decision
 * hive-prism-pillar-eats-the-living), and leaves a player in its radius
 * whole, its filter taking mobs only.
 */
@RegistersGameTests
public final class HiveTests {
    private static final String HIVE_EATS_THE_APPROACHER = "hive_eats_the_approacher";
    private static final String HIVE_HEALS_UNDEAD = "hive_heals_a_wounded_zombie";
    /** Half a zombie's twenty, room to heal. */
    private static final float WOUNDED_HEALTH = 10f;
    private static final String SHOULD_HEAL_ZOMBIE = "The hive should heal the zombie in its radius from %.1f, stands at %.2f";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HIVE_EATS_THE_APPROACHER, HiveTests::hiveEatsTheApproacher);
        GooTestFunctions.registerTest(registrar, HIVE_HEALS_UNDEAD, HiveTests::hiveHealsAWoundedZombie);
    }

    /**
     * A nether goo lands on a prism beside a wounded zombie: the swarm's
     * strikes heal the zombie rather than eat it.
     * decay-and-hive-heal-an-undead-target
     *
     * @param helper the gametest helper
     */
    public static void hiveHealsAWoundedZombie(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        Mob zombie = standOnStone(helper, EntityType.ZOMBIE, IN_THE_RADIUS);
        // the helmet keeps the noon sun off the zombie, so only the swarm touches its health
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setHealth(WOUNDED_HEALTH);
        PrismBlockEntity prism = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class);
        AbilityDefinition decay = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(HIVE));
        helper.assertTrue(decay != null && prism.runCombo(GooTypes.NETHER, HIVE, PrismCombos.comboSteps(decay)),
                String.format(NOT_A_HIVE, prism.getCombo()));
        helper.succeedWhen(() -> helper.assertTrue(zombie.getHealth() > WOUNDED_HEALTH,
                String.format(SHOULD_HEAL_ZOMBIE, WOUNDED_HEALTH, zombie.getHealth())));
    }


    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos PRISM_POS = FLOOR_POS.above();
    /** Two blocks from the prism, well inside the hive's radius. */
    private static final BlockPos IN_THE_RADIUS = PRISM_POS.east(2);
    /** Decay, whose on_prism reaction is the hive (decision channeled-hosts-convert-prisms-over-the-hold). */
    private static final String HIVE = "goo:nether_decay";
    private static final String NOT_A_HIVE = "The prism should hold the hive combo, held '%s'";
    private static final String VINDICATOR_LIVES = "The hive should have eaten the vindicator in its radius";
    private static final String PLAYER_HURT = "The hive should leave a player in its radius whole";
    /** Two blocks the other side of the prism, inside the radius. */
    private static final BlockPos PLAYER_POS = PRISM_POS.west(1).south(2);

    private HiveTests() {
    }

    /**
     * A nether goo lands on a prism beside a vindicator, with a player in its
     * radius: the vindicator is eaten dead while the player stands whole.
     *
     * @param helper the gametest helper
     */
    public static void hiveEatsTheApproacher(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        Mob vindicator = standOnStone(helper, EntityType.VINDICATOR, IN_THE_RADIUS);
        Player player = standPlayer(helper);

        PrismBlockEntity prism = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class);
        AbilityDefinition decay = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(HIVE));
        helper.assertTrue(decay != null && prism.runCombo(GooTypes.NETHER, HIVE, PrismCombos.comboSteps(decay)),
                String.format(NOT_A_HIVE, prism.getCombo()));
        helper.succeedWhen(() -> {
            helper.assertFalse(vindicator.isAlive(), VINDICATOR_LIVES);
            // mobs only, the operator's ruling on Hive
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), PLAYER_HURT);
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
