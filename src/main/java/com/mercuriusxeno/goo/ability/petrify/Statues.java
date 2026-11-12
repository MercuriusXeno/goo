package com.mercuriusxeno.goo.ability.petrify;

import com.mercuriusxeno.goo.block.statue.StatueBlockEntity;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Turns a mob whose petrify gauge filled into a statue block, which mines
 * like cobblestone for cobblestone and the mob's experience
 * (decision petrify-stone-encasement-and-calcify-map) and which Heal's
 * stream wakes back into its mob, still nearly stone
 * (decision heal-wakes-a-statue-at-a-nearly-full-gauge).
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

    /**
     * Wakes a statue back into a mob of its type standing in its cell, facing
     * the way it faced and a baby where it was one, its gauge just under full
     * so it stays slowed and stone-covered until a drain takes the stone off.
     * The statue block leaves with no cobblestone and no experience, which stay
     * with the mob; a statue holding no type stays standing.
     *
     * @param level the server level
     * @param pos   the statue
     */
    public static void wake(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StatueBlockEntity statue)) {
            return;
        }
        Mob mob = wokenMob(level, statue.entityType());
        if (mob == null) {
            return;
        }
        Vec3 standing = Vec3.atBottomCenterOf(pos);
        mob.snapTo(standing.x, standing.y, standing.z, statue.yaw(), 0f);
        mob.setYBodyRot(statue.yaw());
        mob.setYHeadRot(statue.yaw());
        mob.setBaby(statue.baby());
        Petrification waking = Petrification.waking(level.getGameTime());
        mob.setData(GooAttachments.PETRIFICATION, waking);
        PetrifyEvents.slowBy(mob, waking);
        level.removeBlock(pos, false);
        level.addFreshEntity(mob);
    }

    /**
     * A fresh mob of a statue's type.
     *
     * @param level  the server level
     * @param typeId the id of the statue's type
     * @return the mob, or null where the statue holds no type or its type makes no mob
     */
    private static @Nullable Mob wokenMob(ServerLevel level, @Nullable Identifier typeId) {
        if (typeId == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(typeId)
                .map(type -> type.create(level, EntitySpawnReason.CONVERSION))
                .filter(Mob.class::isInstance).map(Mob.class::cast).orElse(null);
    }
}
