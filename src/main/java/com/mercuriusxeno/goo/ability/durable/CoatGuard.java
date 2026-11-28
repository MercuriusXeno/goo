package com.mercuriusxeno.goo.ability.durable;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.CoatCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Keeps what Coat wraps: a blast breaks no Coat cell and no block whose
 * coated face looks toward it, and no mob breaks a block Coat stands against.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class CoatGuard {

    private CoatGuard() {
    }

    /**
     * Takes each Coat cell, and each block a coated face shields from the blast, off the blocks a blast breaks.
     *
     * @param event the blast's detonation
     */
    @SubscribeEvent
    public static void keepCoatedBlocksFromTheBlast(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        Vec3 blast = event.getExplosion().center();
        event.getAffectedBlocks().removeIf(pos -> level.getBlockState(pos).getBlock() instanceof CoatCellBlock
                || CoatCellBlock.shieldsFrom(Vec3.atCenterOf(pos), blast, CoatCellBlock.coatedFaces(level, pos)));
    }

    /**
     * Refuses a mob a block Coat stands against, as the wither's and the ender dragon's breaking ask.
     *
     * @param event the mob's breaking
     */
    @SubscribeEvent
    public static void keepCoatedBlocksFromMobs(LivingDestroyBlockEvent event) {
        if (wraps(event.getEntity().level(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether a Coat cell stands against any face of a block.
     *
     * @param level the level
     * @param pos   the block
     * @return true where a face is coated
     */
    public static boolean wraps(BlockGetter level, BlockPos pos) {
        return !CoatCellBlock.coatedFaces(level, pos).isEmpty();
    }
}
