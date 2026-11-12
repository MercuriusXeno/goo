package com.mercuriusxeno.goo.ability.petrify;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.statue.StatueBlockEntity;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

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
     * Wakes a statue back into the mob it was, standing in its cell, facing
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
        Mob mob = wokenMob(level, statue);
        if (mob == null) {
            return;
        }
        Vec3 standing = Vec3.atBottomCenterOf(pos);
        mob.snapTo(standing.x, standing.y, standing.z, statue.yaw(), 0f);
        mob.setYBodyRot(statue.yaw());
        mob.setYHeadRot(statue.yaw());
        if (statue.savedMob() == null) {
            // statue-keeps-the-whole-mob: a loaded mob carries its own age, so only a fresh mob takes the flag
            mob.setBaby(statue.baby());
        }
        Petrification waking = Petrification.waking(level.getGameTime());
        mob.setData(GooAttachments.PETRIFICATION, waking);
        PetrifyEvents.slowBy(mob, waking);
        level.removeBlock(pos, false);
        level.addFreshEntity(mob);
    }

    /**
     * The mob a statue wakes into: the very mob it was, loaded from its save,
     * or a fresh mob of its type for a statue saved before statues kept the mob.
     * decision statue-keeps-the-whole-mob
     *
     * @param level  the server level
     * @param statue the statue
     * @return the mob, or null where the statue holds no type or its type makes no mob
     */
    private static @Nullable Mob wokenMob(ServerLevel level, StatueBlockEntity statue) {
        CompoundTag saved = statue.savedMob();
        Optional<Entity> woken = saved == null ? freshOf(level, statue.entityType()) : loadedFrom(level, saved);
        return woken.filter(Mob.class::isInstance).map(Mob.class::cast).orElse(null);
    }

    private static Optional<Entity> loadedFrom(ServerLevel level, CompoundTag saved) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(Goo.LOGGER)) {
            return EntityType.create(TagValueInput.create(reporter, level.registryAccess(), saved), level,
                    EntitySpawnReason.LOAD);
        }
    }

    private static Optional<Entity> freshOf(ServerLevel level, @Nullable Identifier typeId) {
        return Optional.ofNullable(typeId).flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional)
                .map(type -> type.create(level, EntitySpawnReason.CONVERSION));
    }
}
