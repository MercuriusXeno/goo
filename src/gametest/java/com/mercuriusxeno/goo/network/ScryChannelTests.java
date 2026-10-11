package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for Scry's sweep through the real channel path: a mock player
 * holds the scry probe, whose sweep runs a damage step where glow's Scry
 * runs its glisten, and the front reaches a zombie standing behind a stone
 * wall the player cannot see through, striking it once as it crosses
 * (decision scry-sphere-reveals-faces-and-glistens-mobs).
 */
public final class ScryChannelTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** The wall's column, between the player and the zombie, three blocks high and the bay's width. */
    private static final int WALL_X = 3;
    private static final int WALL_HEIGHT = 3;
    private static final int BAY_WIDTH = 7;
    private static final BlockPos ZOMBIE_POS = new BlockPos(5, 1, 3);
    /** The front grows a block a tick: ten ticks carry it well past the zombie four blocks off. */
    private static final int HOLD_TICKS = 10;
    private static final int HELD_GOO = 2;
    private static final float PROBE_DAMAGE = 3f;
    private static final float TOLERANCE = 0.01f;
    private static final String TEST_PACK = "gootest";
    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH =
            ResourceKey.create(GooTypes.REGISTRY, Identifier.fromNamespaceAndPath(TEST_PACK, "seventeenth"));
    private static final Identifier SCRY_PROBE = Identifier.fromNamespaceAndPath(TEST_PACK, "seventeenth_scry_probe");
    private static final String ABILITY_REQUIRED = "Ability registry must hold the scry probe";
    private static final String NOT_SEEN = "The wall should hide the zombie from the player";
    private static final String STRUCK_ONCE = "The front should strike the hidden zombie once, for %s, it lost %s";

    private ScryChannelTests() {
    }

    /**
     * A mock player holds the scry probe for ten ticks with a stone wall
     * between it and a zombie four blocks off: the zombie, unseen, loses the
     * probe's damage once, as the front crosses it and never again.
     *
     * @param helper the gametest helper
     */
    public static void scryGlistensTheHiddenZombie(GameTestHelper helper) {
        AbilityDefinition probe = AbilityRegistry.of(helper.getLevel()).getAbility(SCRY_PROBE);
        helper.assertTrue(probe != null, ABILITY_REQUIRED);
        for (int z = 0; z < BAY_WIDTH; z++) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
        }
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float startHealth = zombie.getHealth();
        ServerPlayer player = scryer(helper);
        KnownRecipes.teachRequires(player, probe);
        helper.assertFalse(player.hasLineOfSight(zombie), NOT_SEEN);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(SEVENTEENTH), SCRY_PROBE.toString(),
                player.getEyePosition(), player.getEyePosition(), helper.absolutePos(STAND_POS.below()),
                Direction.UP.get3DDataValue());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = startHealth - zombie.getHealth();
            helper.assertTrue(Math.abs(lost - PROBE_DAMAGE) < TOLERANCE, String.format(STRUCK_ONCE, PROBE_DAMAGE, lost));
            helper.succeed();
        });
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer scryer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(SEVENTEENTH, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
