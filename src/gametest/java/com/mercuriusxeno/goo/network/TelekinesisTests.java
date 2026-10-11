package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for Kinetic's Telekinesis, drunk as its brew by a survival mock
 * player: a block and a mob standing well past vanilla's reach are out of
 * it before the brew and are broken and hit after it, through vanilla's own
 * block break and attack handlers, the kinetic brew the one effect worn
 * (decision telekinesis-enacts-at-extended-reach).
 */
public final class TelekinesisTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 0);
    /** Twelve blocks out: past survival's block reach and attack reach, inside them raised by Telekinesis's 8. */
    private static final BlockPos FAR_BLOCK = new BlockPos(0, 1, 12);
    private static final BlockPos FAR_MOB = new BlockPos(1, 1, 12);
    /** The buffer vanilla's server allows past the block reach, which the handler checks with. */
    private static final double BLOCK_BUFFER = 1.0;
    /** The buffer vanilla's server allows past the attack reach, which the handler checks with. */
    private static final double ATTACK_BUFFER = 3.0;
    private static final int HELD_GOO = 3;
    private static final int FIRST_SEQUENCE = 1;
    private static final String SHOULD_START_OUT_OF_REACH = "The %s should stand past vanilla reach before the brew";
    private static final String SHOULD_WEAR_ONE_EFFECT = "The kinetic brew should be the one effect worn, wears %s";

    private TelekinesisTests() {
    }

    /**
     * A slime block past vanilla's block reach breaks through the block
     * break handler once the kinetic brew is drunk.
     *
     * @param helper the gametest helper
     */
    public static void telekinesisBreaksBeyondReach(GameTestHelper helper) {
        ServerPlayer player = reacher(helper);
        helper.setBlock(FAR_BLOCK, Blocks.SLIME_BLOCK);
        BlockPos far = helper.absolutePos(FAR_BLOCK);
        helper.assertFalse(player.isWithinBlockInteractionRange(far, BLOCK_BUFFER),
                String.format(SHOULD_START_OUT_OF_REACH, "block"));

        BrewEffectTests.drinkBrew(player, GooTypes.KINETIC);
        String effects = oneKineticBrew(player);
        player.gameMode.handleBlockBreakAction(far, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.SOUTH, helper.getLevel().getMaxY(), FIRST_SEQUENCE);

        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(effects.isEmpty(), effects);
        helper.assertBlockPresent(Blocks.AIR, FAR_BLOCK);
        helper.succeed();
    }

    /**
     * A zombie past vanilla's attack reach takes the hit through the attack
     * handler once the kinetic brew is drunk.
     *
     * @param helper the gametest helper
     */
    public static void telekinesisHitsBeyondReach(GameTestHelper helper) {
        ServerPlayer player = reacher(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, FAR_MOB);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        float health = zombie.getHealth();
        helper.assertFalse(player.isWithinAttackRange(player.getMainHandItem(), zombie.getBoundingBox(), ATTACK_BUFFER),
                String.format(SHOULD_START_OUT_OF_REACH, "zombie"));

        BrewEffectTests.drinkBrew(player, GooTypes.KINETIC);
        player.connection.handleAttack(new ServerboundAttackPacket(zombie.getId()));

        float healthAfter = zombie.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(healthAfter < health,
                "A zombie past vanilla reach should take the hit under Telekinesis, health " + health + " to " + healthAfter);
        helper.succeed();
    }

    private static String oneKineticBrew(ServerPlayer player) {
        MobEffectInstance worn = player.getEffect(GooMobEffects.BREW_EFFECTS.get(GooTypes.KINETIC));
        return worn != null && player.getActiveEffects().size() == 1 ? ""
                : String.format(SHOULD_WEAR_ONE_EFFECT, player.getActiveEffects());
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer reacher(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        // the mock player helper makes a creative player, whose reach runs longer
        player.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded has its attacks dropped, and no mock client reports
        player.connection.markClientLoaded();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.KINETIC, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
