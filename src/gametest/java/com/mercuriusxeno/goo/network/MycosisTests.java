package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Mycosis: its stream poisons what it reaches with Goo's spore
 * poison, a spored zombie bursts spores from its corpse that poison a second
 * zombie and lay no Spores goo, and the spray buds the floor it reaches
 * (decision mycosis-spore-stream-buds-and-poisons).
 */
@RegistersGameTests
public final class MycosisTests {
    private static final String MYCOSIS_SPREADS_ON_DEATH = "mycosis_spreads_on_death";
    private static final String MYCOSIS_BUDS_AT_THE_FEET = "mycosis_buds_at_the_feet";
    private static final String MYCOSIS_FIZZLES_ON_STONE = "mycosis_fizzles_on_stone_in_daylight";
    private static final String BUD_ON_STONE = "Mycosis sprayed on stone in daylight should plant no bud";
    /** Ticks the fizzle test sprays for, past many buds' worth of the stream's chance. */
    private static final int FIZZLE_SPRAY_TICKS = 200;

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MYCOSIS_SPREADS_ON_DEATH, MycosisTests::mycosisSpreadsOnDeath);
        GooTestFunctions.registerTest(registrar, MYCOSIS_BUDS_AT_THE_FEET, MycosisTests::mycosisBudsAtTheFeet);
        GooTestFunctions.registerTest(registrar, MYCOSIS_FIZZLES_ON_STONE, MycosisTests::mycosisFizzlesOnStone);
    }


    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Four blocks east of the player, inside the cone. */
    private static final BlockPos SPRAYED_POS = STAND_POS.east(4);
    /** Two blocks south of the sprayed zombie: within its burst, outside the cone. */
    private static final BlockPos BYSTANDER_POS = SPRAYED_POS.south(2);
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward a zombie's middle four blocks off. */
    private static final float LOOKING_AT_ZOMBIE = 9f;
    private static final Identifier MYCOSIS = Identifier.parse("goo:shroom_mycosis");
    private static final int SPRAY_TICKS = 5;
    /** Straight down at the floor under the player. */
    private static final float LOOKING_AT_FEET = 90f;
    /** How far from the player's own cell a bud sprayed at its feet may stand. */
    private static final int AT_FEET_REACH = 1;
    private static final String NO_BUD_AT_FEET = "Mycosis sprayed straight down should bud the floor at the player's feet";
    /** Ticks of hold the bud test sprays for at most, enough that a bud is all but certain. */
    private static final int BUD_SPRAY_TICKS = 400;
    /** Shroom goo the sprayer holds, thousands: enough to pay for the longest spray with room for its cost's tuning. */
    private static final int HELD_GOO = 25;
    private static final String ABILITY_REQUIRED = "Ability registry must hold shroom_mycosis";
    private static final String SPRAYED_POISONED = "The sprayed zombie should carry Goo's spore poison";
    private static final String SPRAYED_SPORED = "The sprayed zombie should carry spores";
    private static final String BYSTANDER_CLEAN = "The bystander outside the cone should start unpoisoned";
    private static final String BYSTANDER_POISONED = "The burst from the corpse should poison the bystander";
    private static final String NO_GOO_WHERE_IT_FELL = "The burst from the corpse should lay no Spores goo where it fell";

    private MycosisTests() {
    }

    /**
     * A mock player sprays Mycosis at a zombie for five ticks: the zombie
     * carries the spore poison and spores while a second zombie outside the
     * cone carries neither; the sprayed zombie dies and its corpse's burst
     * poisons the second.
     *
     * @param helper the gametest helper
     */
    public static void mycosisSpreadsOnDeath(GameTestHelper helper) {
        AbilityDefinition mycosis = mycosis(helper);
        Mob sprayed = helmetedZombie(helper, SPRAYED_POS);
        Mob bystander = helmetedZombie(helper, BYSTANDER_POS);
        ServerPlayer player = sprayer(helper, LOOKING_AT_ZOMBIE);
        KnownRecipes.teachRequires(player, mycosis);
        spray(helper, player, SPRAY_TICKS);
        helper.runAfterDelay(SPRAY_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(sprayed.hasEffect(GooMobEffects.MYCOSIS), SPRAYED_POISONED);
            helper.assertTrue(sprayed.hasData(GooAttachments.SPORED), SPRAYED_SPORED);
            helper.assertFalse(bystander.hasEffect(GooMobEffects.MYCOSIS), BYSTANDER_CLEAN);
            BlockPos fell = sprayed.blockPosition();
            sprayed.kill(helper.getLevel());
            helper.assertTrue(bystander.hasEffect(GooMobEffects.MYCOSIS), BYSTANDER_POISONED);
            // mycosis-is-put-back-as-the-held-spore-stream: the stream's burst lays no Spores goo
            helper.assertFalse(BlockPos.betweenClosedStream(fell.offset(-1, -1, -1), fell.offset(1, 1, 1))
                    .anyMatch(cell -> helper.getLevel().getBlockState(cell).is(GooBlocks.SHROOM_SPORES.get())),
                    NO_GOO_WHERE_IT_FELL);
            helper.succeed();
        });
    }

    /**
     * A mock player standing on mycelium sprays Mycosis straight down at its
     * own feet: a fungal bud stands within a block of where it stands, the
     * spray landing where it is aimed rather than at a distance. The floor is
     * mycelium since a bud is planted only where its mushroom could grow.
     * operator ruling 2026-11-18: Mycosis should fizzle instead of plant buds where mushrooms aren't allowed to grow
     *
     * @param helper the gametest helper
     */
    public static void mycosisBudsAtTheFeet(GameTestHelper helper) {
        BlockPos.betweenClosedStream(STAND_POS.offset(-AT_FEET_REACH, -1, -AT_FEET_REACH),
                STAND_POS.offset(AT_FEET_REACH, -1, AT_FEET_REACH)).forEach(floor -> helper.setBlock(floor,
                Blocks.MYCELIUM));
        AbilityDefinition mycosis = mycosis(helper);
        ServerPlayer player = sprayer(helper, LOOKING_AT_FEET);
        KnownRecipes.teachRequires(player, mycosis);
        spray(helper, player, BUD_SPRAY_TICKS);
        helper.succeedWhen(() -> {
            boolean budded = BlockPos.betweenClosedStream(STAND_POS.offset(-AT_FEET_REACH, -1, -AT_FEET_REACH),
                    STAND_POS.offset(AT_FEET_REACH, 0, AT_FEET_REACH))
                    .anyMatch(cell -> helper.getBlockState(cell).is(GooBlocks.FUNGAL_BUD.get()));
            helper.assertTrue(budded, NO_BUD_AT_FEET);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A mock player standing on stone in daylight sprays Mycosis straight
     * down at its own feet: where no mushroom could grow, the spray fizzles,
     * and no fungal bud stands within a block of it.
     * operator ruling 2026-11-18: Mycosis should fizzle instead of plant buds where mushrooms aren't allowed to grow
     *
     * @param helper the gametest helper
     */
    public static void mycosisFizzlesOnStone(GameTestHelper helper) {
        BlockPos.betweenClosedStream(STAND_POS.offset(-AT_FEET_REACH, -1, -AT_FEET_REACH),
                STAND_POS.offset(AT_FEET_REACH, -1, AT_FEET_REACH)).forEach(floor -> helper.setBlock(floor,
                Blocks.STONE));
        AbilityDefinition mycosis = mycosis(helper);
        ServerPlayer player = sprayer(helper, LOOKING_AT_FEET);
        KnownRecipes.teachRequires(player, mycosis);
        spray(helper, player, FIZZLE_SPRAY_TICKS);
        helper.runAfterDelay(FIZZLE_SPRAY_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            boolean budded = BlockPos.betweenClosedStream(STAND_POS.offset(-AT_FEET_REACH, 0, -AT_FEET_REACH),
                    STAND_POS.offset(AT_FEET_REACH, 0, AT_FEET_REACH))
                    .anyMatch(cell -> helper.getBlockState(cell).is(GooBlocks.FUNGAL_BUD.get()));
            helper.assertFalse(budded, BUD_ON_STONE);
            helper.succeed();
        });
    }

    private static AbilityDefinition mycosis(GameTestHelper helper) {
        AbilityDefinition mycosis = AbilityRegistry.of(helper.getLevel()).getAbility(MYCOSIS);
        helper.assertTrue(mycosis != null, ABILITY_REQUIRED);
        return mycosis;
    }

    private static Mob helmetedZombie(GameTestHelper helper, BlockPos at) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    private static void spray(GameTestHelper helper, ServerPlayer player, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.SHROOM), MYCOSIS.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer sprayer(GameTestHelper helper, float pitch) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(pitch);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.SHROOM, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
