package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.resources.Identifier;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Kinetic's Remote, held on the glove by a survival mock
 * player: a block and a mob standing well past vanilla's reach are out of
 * it before the hold and are broken and hit after it, through vanilla's own
 * block break and attack handlers, the kinetic held effect the one effect worn
 * (decision telekinesis-enacts-at-extended-reach).
 */
@RegistersGameTests
public final class RemoteTests {
    private static final String REMOTE_BREAKS_BEYOND_REACH = "remote_breaks_beyond_reach";
    private static final String REMOTE_HITS_BEYOND_REACH = "remote_hits_beyond_reach";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, REMOTE_BREAKS_BEYOND_REACH, RemoteTests::remoteBreaksBeyondReach);
        GooTestFunctions.registerTest(registrar, REMOTE_HITS_BEYOND_REACH, RemoteTests::remoteHitsBeyondReach);
    }


    private static final Identifier KINETIC_REMOTE = Identifier.parse("goo:kinetic_remote");
    private static final BlockPos STAND_POS = new BlockPos(0, 1, 0);
    /** Twelve blocks out: past survival's block reach and attack reach, inside them raised by Remote's reach. */
    private static final BlockPos FAR_BLOCK = new BlockPos(0, 1, 12);
    private static final BlockPos FAR_MOB = new BlockPos(1, 1, 12);
    /** The buffer vanilla's server allows past the block reach, which the handler checks with. */
    private static final double BLOCK_BUFFER = 1.0;
    /** The buffer vanilla's server allows past the attack reach, which the handler checks with. */
    private static final double ATTACK_BUFFER = 3.0;
    private static final int HELD_GOO = 3;
    private static final int FIRST_SEQUENCE = 1;
    private static final String SHOULD_START_OUT_OF_REACH = "The %s should stand past vanilla reach before the hold";
    private static final String SHOULD_STAND_IN_THE_STRUCTURE = "The far %s should stand inside this test's structure";
    private static final String SHOULD_WEAR_ONE_EFFECT = "The kinetic held effect should be the one effect worn, wears %s";

    private RemoteTests() {
    }

    /**
     * A slime block past vanilla's block reach breaks through the block
     * break handler once Remote is held on the glove.
     *
     * @param helper the gametest helper
     */
    public static void remoteBreaksBeyondReach(GameTestHelper helper) {
        assertInsideTheStructure(helper, FAR_BLOCK, "block");
        ServerPlayer player = reacher(helper);
        helper.setBlock(FAR_BLOCK, Blocks.SLIME_BLOCK);
        BlockPos far = helper.absolutePos(FAR_BLOCK);
        helper.assertFalse(player.isWithinBlockInteractionRange(far, BLOCK_BUFFER),
                String.format(SHOULD_START_OUT_OF_REACH, "block"));

        HeartOverlayTests.invoke(player, GooTypes.KINETIC, KINETIC_REMOTE);
        String effects = oneKineticHeldEffect(player);
        player.gameMode.handleBlockBreakAction(far, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.SOUTH, helper.getLevel().getMaxY(), FIRST_SEQUENCE);

        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(effects.isEmpty(), effects);
        helper.assertBlockPresent(Blocks.AIR, FAR_BLOCK);
        helper.succeed();
    }

    /**
     * A zombie past vanilla's attack reach takes the hit through the attack
     * handler once Remote is held on the glove.
     *
     * @param helper the gametest helper
     */
    public static void remoteHitsBeyondReach(GameTestHelper helper) {
        assertInsideTheStructure(helper, FAR_MOB, "zombie");
        ServerPlayer player = reacher(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, FAR_MOB);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        float health = zombie.getHealth();
        helper.assertFalse(player.isWithinAttackRange(player.getMainHandItem(), zombie.getBoundingBox(), ATTACK_BUFFER),
                String.format(SHOULD_START_OUT_OF_REACH, "zombie"));

        HeartOverlayTests.invoke(player, GooTypes.KINETIC, KINETIC_REMOTE);
        player.connection.handleAttack(new ServerboundAttackPacket(zombie.getId()));

        float healthAfter = zombie.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(healthAfter < health,
                "A zombie past vanilla reach should take the hit under Remote, health " + health + " to " + healthAfter);
        helper.succeed();
    }

    /**
     * Fails unless a far cell lies inside this test's own structure. A mob
     * spawned past it lands in the next test's padding, whose chunk the level
     * may hold unloaded for entities, and there the attack handler finds no
     * entity by the zombie's id and drops the hit.
     */
    private static void assertInsideTheStructure(GameTestHelper helper, BlockPos far, String what) {
        helper.assertTrue(helper.getRelativeBounds().contains(Vec3.atCenterOf(far)),
                String.format(SHOULD_STAND_IN_THE_STRUCTURE, what));
    }

    private static String oneKineticHeldEffect(ServerPlayer player) {
        MobEffectInstance worn = player.getEffect(GooMobEffects.HELD_EFFECTS.get(GooTypes.KINETIC));
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
