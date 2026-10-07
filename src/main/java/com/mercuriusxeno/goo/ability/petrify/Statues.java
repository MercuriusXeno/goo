package com.mercuriusxeno.goo.ability.petrify;

import com.mercuriusxeno.goo.block.statue.StatueBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Turns a mob whose petrify gauge filled into a statue block for good, which
 * mines like cobblestone for cobblestone and the mob's experience
 * (decision petrify-stone-encasement-and-calcify-map).
 */
public final class Statues {

    /** The stone settling as the statue forms. */
    private static final float SETTLE_VOLUME = 1f;
    private static final float SETTLE_PITCH = 0.8f;

    private Statues() {
    }

    /**
     * Turns a mob into a statue standing where it stood; a mob standing in a
     * cell a block cannot replace stays a mob.
     *
     * @param level     the server level
     * @param mob       the petrified mob
     * @param petrifier the entity whose petrify filled it, credited with its experience
     */
    public static void encase(ServerLevel level, Mob mob, @Nullable Entity petrifier) {
        BlockPos pos = mob.blockPosition();
        if (!level.getBlockState(pos).canBeReplaced()) {
            return;
        }
        int experience = mob.getExperienceReward(level, petrifier);
        level.setBlock(pos, GooBlocks.STATUE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof StatueBlockEntity statue) {
            statue.hold(mob, BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), experience);
        }
        level.playSound(null, pos, SoundEvents.DEEPSLATE_PLACE, SoundSource.BLOCKS, SETTLE_VOLUME, SETTLE_PITCH);
        mob.discard();
    }
}
