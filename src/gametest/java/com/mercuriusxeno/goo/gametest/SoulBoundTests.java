package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.SoulBoundStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.registries.DeferredItem;
import java.util.List;

/**
 * Gametests for soul-bound items (decision exorite-kept-through-death):
 * every exorite piece and the exo gauntlet stand in goo:soul_bound, and a
 * soul-bound stack skips the death drop and returns to its slot on respawn
 * while an unbound stack drops.
 */
public final class SoulBoundTests {

    private static final String REMOVAL = "removal";
    private static final BlockPos PLAYER_POS = new BlockPos(1, 1, 1);
    private static final double DROP_REACH = 3.0;
    private static final int EXORITE_SLOT = 3;
    private static final int STONE_SLOT = 4;

    private static final String NOT_BOUND = "goo:soul_bound should hold ";
    private static final String STONE_BOUND = "goo:soul_bound should not hold a stone pickaxe";
    private static final String WRONG_DROPS = "Death should drop exactly one item, the stone pickaxe, but dropped ";
    private static final String NOT_RESTORED = "The respawned player should hold the exorite pickaxe in slot " + EXORITE_SLOT;

    private SoulBoundTests() {
    }

    /**
     * Each exorite piece and the exo gauntlet read as goo:soul_bound; a stone
     * pickaxe does not.
     *
     * @param helper the gametest helper
     */
    public static void tagHoldsExorite(GameTestHelper helper) {
        for (DeferredItem<? extends Item> piece : GooItems.EXORITE_SET) {
            helper.assertTrue(new ItemStack(piece.get()).is(SoulBoundStacks.SOUL_BOUND), NOT_BOUND + piece.getId());
        }
        helper.assertTrue(new ItemStack(GooItems.EXO_GAUNTLET.get()).is(SoulBoundStacks.SOUL_BOUND),
                NOT_BOUND + GooItems.EXO_GAUNTLET.getId());
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).is(SoulBoundStacks.SOUL_BOUND), STONE_BOUND);
        helper.succeed();
    }

    /**
     * A mock player holding an exorite pickaxe in hotbar slot 3 and a stone
     * pickaxe in slot 4 dies with keepInventory off: the one item it drops is
     * the stone pickaxe, and after the respawn clone the new player holds the
     * exorite pickaxe in slot 3.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void survivesDeath(GameTestHelper helper) {
        helper.getLevel().getGameRules().set(GameRules.KEEP_INVENTORY, false, helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.setPos(stand.getX(), stand.getY(), stand.getZ());
        player.getInventory().setItem(EXORITE_SLOT, new ItemStack(GooItems.EXORITE_PICKAXE.get()));
        player.getInventory().setItem(STONE_SLOT, new ItemStack(Items.STONE_PICKAXE));

        player.die(helper.getLevel().damageSources().generic());
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, PLAYER_POS, DROP_REACH);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(Items.STONE_PICKAXE),
                WRONG_DROPS + drops.stream().map(ItemEntity::getItem).toList());

        ServerPlayer respawned = helper.makeMockServerPlayerInLevel();
        respawned.restoreFrom(player, false);
        helper.assertTrue(respawned.getInventory().getItem(EXORITE_SLOT).is(GooItems.EXORITE_PICKAXE.get()),
                NOT_RESTORED);

        drops.forEach(ItemEntity::discard);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.getLevel().getServer().getPlayerList().remove(respawned);
        helper.succeed();
    }
}
