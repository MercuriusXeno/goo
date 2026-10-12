package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Decay converting a bare prism through the stream handler's
 * real path: Decay held on a bare prism for one charge's ticks unbroken
 * converts it into the hive combo, and a mob in the cone takes the swarm
 * before any prism charges.
 * channeled-hosts-convert-prisms-over-the-hold
 */
@RegistersGameTests
public final class DecayPrismTests {
    private static final String DECAY_CONVERTS = "decay_stream_converts_a_prism";
    private static final String DECAY_BITES_FIRST = "decay_bites_a_mob_before_charging_a_prism";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DECAY_CONVERTS, DecayPrismTests::decayHeldConvertsAPrism);
        GooTestFunctions.registerTest(registrar, DECAY_BITES_FIRST, DecayPrismTests::decayBitesAMobBeforeCharging);
    }


    private static final Identifier DECAY = Identifier.parse("goo:nether_decay");
    private static final BlockPos PRISM = new BlockPos(4, 1, 2);
    private static final BlockPos STAND = new BlockPos(1, 1, 2);
    /** On the floor between the player and the prism, in the cone. */
    private static final BlockPos IN_THE_CONE = new BlockPos(2, 1, 2);
    private static final int BITE_TICKS = 10;
    private static final String ABILITY_REQUIRED = "The ability registry must hold nether_decay";
    private static final String SHOULD_CONVERT = "A full Decay hold should make the prism a hive, held '%s'";
    private static final String SHOULD_BITE = "Decay should bite the zombie in its cone";
    private static final String SHOULD_NOT_CHARGE = "A Decay biting a mob should charge no prism, charged %d";

    private DecayPrismTests() {
    }

    /**
     * A player streams Decay at a bare prism for one charge's ticks: the
     * prism holds the hive combo, sourced from Decay.
     *
     * @param helper the gametest helper
     */
    public static void decayHeldConvertsAPrism(GameTestHelper helper) {
        int hold = hold(helper);
        ServerPlayer player = streamer(helper);
        streamFor(helper, player, hold);
        helper.runAfterDelay(hold + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            String combo = helper.getBlockEntity(PRISM, PrismBlockEntity.class).getCombo();
            helper.assertTrue(DECAY.toString().equals(combo), String.format(SHOULD_CONVERT, combo));
            helper.succeed();
        });
    }

    /**
     * A zombie stands between a player and a bare prism the player streams
     * Decay at: the swarm bites the zombie and the prism takes no charge.
     *
     * @param helper the gametest helper
     */
    public static void decayBitesAMobBeforeCharging(GameTestHelper helper) {
        helper.setBlock(IN_THE_CONE.below(), Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, IN_THE_CONE);
        ServerPlayer player = streamer(helper);
        streamFor(helper, player, BITE_TICKS);
        helper.runAfterDelay(BITE_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            PrismBlockEntity prism = helper.getBlockEntity(PRISM, PrismBlockEntity.class);
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), SHOULD_BITE);
            int charged = prism.conversion().charge();
            helper.assertTrue(charged == 0 && !prism.hasCombo(), String.format(SHOULD_NOT_CHARGE, charged));
            helper.succeed();
        });
    }

    /**
     * The hold a Decay conversion takes: one charge's ticks, as nether_decay.json names them.
     */
    private static int hold(GameTestHelper helper) {
        AbilityDefinition decay = AbilityRegistry.of(helper.getLevel()).getAbility(DECAY);
        helper.assertTrue(decay != null, ABILITY_REQUIRED);
        return decay.delivery().ticksPerCharge();
    }

    /**
     * A bare prism on the floor and a player three blocks west of it, holding
     * a glove and nether goo, looking at the prism's middle.
     */
    private static ServerPlayer streamer(GameTestHelper helper) {
        helper.setBlock(PRISM.below(), Blocks.STONE);
        helper.setBlock(PRISM, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.NETHER);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(helper.getLevel()).getAbility(DECAY));
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(PRISM)));
        return player;
    }

    /**
     * Sends one Decay stream tick a game tick, from the first, as a held glove does.
     */
    private static void streamFor(GameTestHelper helper, ServerPlayer player, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.NETHER), DECAY.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int at = 1; at <= ticks; at++) {
            helper.runAfterDelay(at, () -> GooStreamHandler.streamTick(player, tick));
        }
    }
}
