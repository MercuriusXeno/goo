package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Gametests for glow's Sunbeam through the real channel path: a mock player
 * holds the ray for one tick, its first tick landing a hit. Struck directly,
 * a zombie takes the whole undead hit and burns; aimed at a prism, the ray
 * refracts to three zombies around it, whose split hits sum past one direct
 * hit (decision sunbeam-splits-at-the-prism-with-a-glisten).
 */
public final class SunbeamChannelTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final BlockPos PRISM_POS = new BlockPos(4, 1, 3);
    private static final List<BlockPos> AROUND_THE_PRISM = List.of(new BlockPos(4, 1, 1), new BlockPos(4, 1, 5),
            new BlockPos(6, 1, 3));
    private static final BlockPos STRUCK_POS = new BlockPos(4, 1, 3);
    /** Sunbeam's direct hit on an undead mob: 4 doubled. */
    private static final float UNDEAD_HIT = 8f;
    private static final float TOLERANCE = 0.01f;
    private static final double BODY_HEIGHT = 1.0;
    private static final int HELD_GOO = 2;
    private static final Identifier SUNBEAM = Identifier.parse("goo:glow_sunbeam");
    private static final String ABILITY_REQUIRED = "Ability registry must hold glow_sunbeam";
    private static final String UNTOUCHED = "Refracted zombie %d should take a share of the hit, it lost %s";
    private static final String SUM_TOO_LOW = "The split hits should sum past one direct hit of %s, they summed %s";
    private static final String WHOLE_HIT = "The struck zombie should lose at least one whole undead hit of %s, it lost %s";
    private static final String NOT_BURNING = "The struck zombie is undead and should burn";

    private SunbeamChannelTests() {
    }

    /**
     * A mock player holds Sunbeam at a prism with three zombies in clear
     * line around it: each zombie loses a share of the hit, and the shares
     * sum past one direct hit.
     *
     * @param helper the gametest helper
     */
    public static void sunbeamRefractsToThree(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        List<Zombie> zombies = AROUND_THE_PRISM.stream().map(pos -> helmeted(helper, pos)).toList();
        List<Float> before = zombies.stream().map(Zombie::getHealth).toList();
        ServerPlayer player = holdAt(helper, Vec3.atCenterOf(helper.absolutePos(PRISM_POS)));
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float sum = 0;
            for (int i = 0; i < zombies.size(); i++) {
                float lost = before.get(i) - zombies.get(i).getHealth();
                helper.assertTrue(lost > 0, String.format(UNTOUCHED, i, lost));
                sum += lost;
            }
            helper.assertTrue(sum > UNDEAD_HIT, String.format(SUM_TOO_LOW, UNDEAD_HIT, sum));
            helper.succeed();
        });
    }

    /**
     * A mock player holds Sunbeam on a zombie: the zombie loses at least one
     * whole undead hit, the burn it starts taking its own share, and burns.
     *
     * @param helper the gametest helper
     */
    public static void sunbeamBurnsTheUndeadItStrikes(GameTestHelper helper) {
        helper.setBlock(STRUCK_POS.below(), Blocks.STONE);
        Zombie zombie = helmeted(helper, STRUCK_POS);
        float before = zombie.getHealth();
        ServerPlayer player = holdAt(helper, zombie.position().add(0, BODY_HEIGHT, 0));
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = before - zombie.getHealth();
            helper.assertTrue(lost >= UNDEAD_HIT - TOLERANCE, String.format(WHOLE_HIT, UNDEAD_HIT, lost));
            helper.assertTrue(zombie.isOnFire(), NOT_BURNING);
            helper.succeed();
        });
    }

    private static Zombie helmeted(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /**
     * Stands a mock player holding glow goo in a glove and holds Sunbeam at a
     * point for one tick, the hold's first, which lands a hit.
     *
     * @param helper the gametest helper
     * @param aim    the point the cursor rests on
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer holdAt(GameTestHelper helper, Vec3 aim) {
        AbilityDefinition sunbeam = AbilityRegistry.of(helper.getLevel()).getAbility(SUNBEAM);
        helper.assertTrue(sunbeam != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.GLOW, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, sunbeam);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.GLOW), SUNBEAM.toString(),
                player.getEyePosition(), aim, helper.absolutePos(STAND_POS.below()), Direction.UP.get3DDataValue());
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, tick));
        return player;
    }
}
