package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;

/**
 * Static helpers for item entity absorption into the crucible basin.
 * Handles blobs, PMIs, containers, and regular meltable items that
 * fall or are thrown into the goocible.
 */
final class CrucibleAbsorption {

    private CrucibleAbsorption() {
    }

    /**
     * Attempts to absorb a single item entity into the crucible's melting pool.
     *
     * @param itemEntity the item entity to absorb
     * @param crucible   the crucible block entity
     */
    static void tryAbsorbItem(ItemEntity itemEntity, CrucibleBlockEntity crucible) {
        ItemStack stack = itemEntity.getItem();
        if (isGooBlob(stack)) {
            absorbBlob(itemEntity, stack, crucible);
        } else {
            absorbNonBlob(itemEntity, stack, crucible);
        }
    }

    /**
     * Routes non-blob items to the correct absorption handler.
     *
     * @param entity   the item entity to absorb
     * @param stack    the item stack from the entity
     * @param crucible the crucible block entity
     */
    private static void absorbNonBlob(ItemEntity entity, ItemStack stack, CrucibleBlockEntity crucible) {
        if (stack.getItem() instanceof PartiallyMeltedItem) {
            absorbPMI(entity, stack, crucible);
        } else if (crucible.containerEvaluator.isContainer(stack)) {
            absorbContainer(entity, stack, crucible);
        } else {
            absorbMeltable(entity, stack, crucible);
        }
    }

    /**
     * Returns true if the stack is any goo blob variant.
     *
     * @param stack the item stack to test
     * @return true if the stack is a blob or omniblob
     */
    private static boolean isGooBlob(ItemStack stack) {
        return stack.getItem() instanceof GooBlobItem
                || stack.getItem() instanceof GooOmniblobItem;
    }

    /**
     * Re-inserts a dropped PMI's remaining goo into the melt pool when it fits whole.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbPMI(ItemEntity entity, ItemStack stack,
                                  CrucibleBlockEntity crucible) {
        GooContents contents = PartiallyMeltedItem.getContents(stack);
        if (contents.isEmpty()) {
            return;
        }
        if (!CrucibleInsertion.mergeStackIntoPool(crucible, stack, contents)) {
            return;
        }
        crucible.syncToClients();
        entity.discard();
        spawnMeltEffects(entity.level(), crucible);
    }

    /**
     * Inserts the whole blobs, or the whole omniblob, that fit the reservoir,
     * bypassing the melt pipeline; what does not fit stays on the ground
     * (decision crucible-refuses-past-two-billion).
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbBlob(ItemEntity entity, ItemStack stack,
                                   CrucibleBlockEntity crucible) {
        ResourceKey<GooTypeDefinition> type = BlobStacks.keyOf(stack);
        int volume = BlobStacks.volumeOf(stack);
        if (type == null || volume <= 0) {
            return;
        }
        int perUnit = volume / stack.getCount();
        int fitting = CrucibleInsertion.reservoirUnitsThatFit(crucible, type, perUnit, stack.getCount());
        if (fitting <= 0) {
            return;
        }
        crucible.insertGoo(type, fitting * perUnit);
        takeUnits(entity, stack, fitting);
        spawnMeltEffects(entity.level(), crucible);
    }

    /**
     * Removes the units the crucible took from the entity's stack, discarding
     * the entity once none remain.
     *
     * @param entity the item entity
     * @param stack  the entity's stack
     * @param taken  the units the crucible took
     */
    private static void takeUnits(ItemEntity entity, ItemStack stack, int taken) {
        if (taken >= stack.getCount()) {
            entity.discard();
        } else {
            entity.setItem(stack.copyWithCount(stack.getCount() - taken));
        }
    }

    /**
     * Inserts the whole items whose goo fits the pool as one pooled merge; the
     * rest stay as the item entity (decision crucible-refuses-past-two-billion).
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbMeltable(ItemEntity entity, ItemStack stack,
                                       CrucibleBlockEntity crucible) {
        int melted = CrucibleInsertion.insertItem(crucible, stack, stack.getCount());
        if (melted > 0) {
            takeUnits(entity, stack, melted);
            spawnMeltEffects(entity.level(), crucible);
        }
    }

    /**
     * Evaluates a container's contents recursively, merges goo values into
     * the melt pool, and ejects items without goo values as item entities.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbContainer(ItemEntity entity, ItemStack stack,
                                        CrucibleBlockEntity crucible) {
        List<ItemStack> ejects = CrucibleInsertion.insertContainer(crucible, stack);
        if (ejects == null) {
            return;
        }
        entity.discard();
        spawnEjectedItems(entity.level(), crucible.getBlockPos(), ejects);
        spawnMeltEffects(entity.level(), crucible);
    }

    /**
     * Spawns ejected items as item entities above the crucible basin.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param ejects the list of items to eject
     */
    static void spawnEjectedItems(Level level, BlockPos pos,
                                  List<ItemStack> ejects) {
        for (ItemStack eject : ejects) {
            Block.popResource(level, pos.above(), eject);
        }
    }

    /**
     * Spawns smoke particles and plays a sizzle sound when an item is absorbed.
     *
     * @param level    the current level
     * @param crucible the crucible block entity
     */
    static void spawnMeltEffects(Level level, CrucibleBlockEntity crucible) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = crucible.getBlockPos();
        CrucibleParticleHelper.spawnMeltSmoke(serverLevel, pos);
        if (crucible.shouldPlaySizzle(level.getGameTime())) {
            CrucibleParticleHelper.playSizzle(serverLevel, pos);
        }
    }
}
