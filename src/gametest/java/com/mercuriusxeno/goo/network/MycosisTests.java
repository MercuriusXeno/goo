package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapDripGrade;
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

/**
 * Gametests for Mycosis: its stream poisons what it reaches with Goo's spore
 * poison, a spored zombie bursts spores from its corpse that poison a second
 * zombie, and the spray buds the floor it reaches; a shroom tap's drip
 * poisons the zombie it lands under (decisions mycosis-spore-stream-buds-and-poisons
 * and mycosis-drip-sprays-spores-below).
 */
public final class MycosisTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Four blocks east of the player, inside the cone. */
    private static final BlockPos SPRAYED_POS = STAND_POS.east(4);
    /** Two blocks south of the sprayed zombie: within its burst, outside the cone. */
    private static final BlockPos BYSTANDER_POS = SPRAYED_POS.south(2);
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward a zombie's middle four blocks off. */
    private static final float LOOKING_AT_ZOMBIE = 9f;
    /** Pitch down from the eye onto the floor one to four blocks ahead. */
    private static final float LOOKING_AT_FLOOR = 45f;
    private static final Identifier MYCOSIS = Identifier.parse("goo:shroom_mycosis");
    private static final int SPRAY_TICKS = 5;
    /** Straight down at the floor under the player. */
    private static final float LOOKING_AT_FEET = 90f;
    /** How far from the player's own cell a bud sprayed at its feet may stand. */
    private static final int AT_FEET_REACH = 1;
    private static final String NO_BUD_AT_FEET = "Mycosis sprayed straight down should bud the floor at the player's feet";
    /** Ticks of hold the bud test sprays for at most, enough that a bud is all but certain. */
    private static final int BUD_SPRAY_TICKS = 400;
    private static final int HELD_GOO = 22;
    private static final int FLOOR_SCAN_RADIUS = 5;
    private static final String ABILITY_REQUIRED = "Ability registry must hold shroom_mycosis";
    private static final String SPRAYED_POISONED = "The sprayed zombie should carry Goo's spore poison";
    private static final String SPRAYED_SPORED = "The sprayed zombie should carry spores";
    private static final String BYSTANDER_CLEAN = "The bystander outside the cone should start unpoisoned";
    private static final String BYSTANDER_POISONED = "The burst from the corpse should poison the bystander";
    private static final String NO_BUD = "Mycosis sprayed at the floor should bud it";
    private static final String TAP_ABILITY_REQUIRED = "Shroom should carry a tap ability";
    private static final String TAPPED_POISONED = "The zombie under a shroom tap should carry Goo's spore poison";
    /** The tap stands over the zombie's head, its drip falling to the stone under the zombie's feet. */
    private static final BlockPos TAP_POS = new BlockPos(1, 3, 1);
    private static final BlockPos UNDER_TAP_POS = new BlockPos(1, 1, 1);
    private static final int TAP_VOLUME = 1000;

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
            sprayed.kill(helper.getLevel());
            helper.assertTrue(bystander.hasEffect(GooMobEffects.MYCOSIS), BYSTANDER_POISONED);
            helper.succeed();
        });
    }

    /**
     * A mock player sprays Mycosis at the floor: before the hold runs out a
     * fungal bud stands on the floor it sprays.
     *
     * @param helper the gametest helper
     */
    public static void mycosisPlacesBuds(GameTestHelper helper) {
        AbilityDefinition mycosis = mycosis(helper);
        ServerPlayer player = sprayer(helper, LOOKING_AT_FLOOR);
        KnownRecipes.teachRequires(player, mycosis);
        spray(helper, player, BUD_SPRAY_TICKS);
        helper.succeedWhen(() -> {
            boolean budded = BlockPos.betweenClosedStream(STAND_POS.offset(0, -1, -FLOOR_SCAN_RADIUS),
                    STAND_POS.offset(FLOOR_SCAN_RADIUS, 0, FLOOR_SCAN_RADIUS))
                    .anyMatch(cell -> helper.getBlockState(cell).is(GooBlocks.FUNGAL_BUD.get()));
            helper.assertTrue(budded, NO_BUD);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A shroom tap drips over a zombie: once a drip lands on the stone under
     * it, the zombie carries the spore poison.
     *
     * @param helper the gametest helper
     */
    public static void mycosisTapPoisonsBelow(GameTestHelper helper) {
        helper.assertTrue(AbilityRegistry.of(helper.getLevel()).tapAbilityFor(GooTypes.SHROOM) != null,
                TAP_ABILITY_REQUIRED);
        helper.setBlock(UNDER_TAP_POS.below(), Blocks.STONE);
        helper.setBlock(UNDER_TAP_POS.above(), Blocks.AIR);
        Mob zombie = helmetedZombie(helper, UNDER_TAP_POS);
        helper.setBlock(TAP_POS, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.OPEN, true));
        TapBlockEntity tap = helper.getBlockEntity(TAP_POS, TapBlockEntity.class);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        tap.insertGoo(GooTypes.SHROOM, TAP_VOLUME);
        tap.setDripGrade(TapDripGrade.ONE_PER_16_TICKS);
        helper.succeedWhen(() -> helper.assertTrue(zombie.hasEffect(GooMobEffects.MYCOSIS), TAPPED_POISONED));
    }

    /**
     * A mock player sprays Mycosis straight down at its own feet: a fungal
     * bud stands within a block of where it stands, the spray landing where
     * it is aimed rather than at a distance.
     *
     * @param helper the gametest helper
     */
    public static void mycosisBudsAtTheFeet(GameTestHelper helper) {
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
