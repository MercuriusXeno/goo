package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Gametests for the empty spawner recipe (decision
 * spawner-recipe-takes-exorite): eight exorite bars in a ring craft one
 * spawner, and a spawner with no mob takes one from a spawn egg.
 */
public final class SpawnerRecipeTests {

    private static final String REMOVAL = "removal";
    private static final int GRID_SIDE = 3;
    private static final int CENTER_CELL = 4;
    private static final BlockPos SPAWNER_POS = new BlockPos(1, 1, 1);
    private static final String SPAWN_DATA = "SpawnData";
    private static final String ENTITY = "entity";
    private static final String ID = "id";
    private static final String ZOMBIE_ID = "minecraft:zombie";

    private static final String RING_NOT_CRAFTED = "Eight exorite bars in a ring should craft one spawner";
    private static final String IRON_RING_CRAFTED = "Eight iron bars in a ring should craft no spawner";
    private static final String FULL_GRID_CRAFTED = "Nine exorite bars should craft no spawner";
    private static final String NOT_EMPTY = "A placed spawner should start with no mob";
    private static final String NOT_ZOMBIE = "The spawner should spawn zombies after a zombie spawn egg";

    private SpawnerRecipeTests() {
    }

    /**
     * Eight exorite bars on the outer cells with the center empty craft one
     * spawner; the same ring of iron bars and a full grid of exorite bars
     * craft none.
     *
     * @param helper the gametest helper
     */
    public static void craftedFromExoriteBars(GameTestHelper helper) {
        ItemStack exoriteBars = new ItemStack(GooItems.EXORITE_BARS.get());
        ItemStack spawner = craft(helper, ring(exoriteBars, ItemStack.EMPTY));
        helper.assertTrue(spawner.is(Items.SPAWNER) && spawner.getCount() == 1, RING_NOT_CRAFTED);
        helper.assertTrue(!craft(helper, ring(new ItemStack(Items.IRON_BARS), ItemStack.EMPTY)).is(Items.SPAWNER),
                IRON_RING_CRAFTED);
        helper.assertTrue(!craft(helper, ring(exoriteBars, exoriteBars)).is(Items.SPAWNER), FULL_GRID_CRAFTED);
        helper.succeed();
    }

    /**
     * A placed spawner saves no spawn data; a zombie spawn egg used on it
     * through SpawnEggItem.useOn sets zombies as its mob.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void emptySpawnerTakesEgg(GameTestHelper helper) {
        helper.setBlock(SPAWNER_POS, Blocks.SPAWNER);
        SpawnerBlockEntity spawner = helper.getBlockEntity(SPAWNER_POS, SpawnerBlockEntity.class);
        helper.assertTrue(spawnEntityId(helper, spawner).isEmpty(), NOT_EMPTY);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ZOMBIE_SPAWN_EGG));
        BlockPos pos = helper.absolutePos(SPAWNER_POS);
        Items.ZOMBIE_SPAWN_EGG.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        helper.assertTrue(spawnEntityId(helper, spawner).filter(ZOMBIE_ID::equals).isPresent(), NOT_ZOMBIE);
        // A zombie spawner left standing would spawn into later tests near their players.
        helper.setBlock(SPAWNER_POS, Blocks.AIR);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static List<ItemStack> ring(ItemStack edge, ItemStack center) {
        List<ItemStack> cells = new ArrayList<>(Collections.nCopies(GRID_SIDE * GRID_SIDE, edge));
        cells.set(CENTER_CELL, center);
        return cells;
    }

    private static ItemStack craft(GameTestHelper helper, List<ItemStack> cells) {
        CraftingInput grid = CraftingInput.of(GRID_SIDE, GRID_SIDE, cells);
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(holder -> holder.value().assemble(grid))
                .orElse(ItemStack.EMPTY);
    }

    private static Optional<String> spawnEntityId(GameTestHelper helper, SpawnerBlockEntity spawner) {
        CompoundTag saved = spawner.saveWithoutMetadata(helper.getLevel().registryAccess());
        return saved.getCompound(SPAWN_DATA).flatMap(data -> data.getCompound(ENTITY)).flatMap(entity -> entity.getString(ID));
    }
}
