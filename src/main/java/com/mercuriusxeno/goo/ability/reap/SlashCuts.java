package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.BlockAfterimagePayload;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Reap's slashes cutting on the server, as the Crissaegrim's hitbox does in
 * Symphony of the Night: a fixed box in front of the slasher stands for the
 * slash's life. On the first tick it cuts it reaps and replants every ripe plant
 * inside, leaving the afterimage of how it stood; each tick of its life it
 * cuts every living body inside but its slasher, once a slash, each cut
 * landing through the body's immunity so every slash connects. The cuts live
 * a few ticks and are never saved.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SlashCuts {

    /** The ticks a reaped plant's afterimage takes to fade. */
    static final int AFTERIMAGE_TICKS = 12;
    private static final double BODY_MIDDLE = 0.5;
    /** Trimmed off a box's far faces so a box ending on a cell's face holds none of that cell. */
    private static final double FACE_TRIM = 1e-6;

    private static final Map<ServerLevel, List<Cut>> CUTTING = new WeakHashMap<>();

    private SlashCuts() {
    }

    /**
     * One slash's box cutting, and the bodies it has cut.
     *
     * @param box        the box
     * @param launchedAt the game tick the slash appeared
     * @param lifeTicks  the ticks it stands
     * @param slasherId  the id of the entity slashing, which the box never cuts
     * @param damage     what its cut deals
     * @param struck     the ids of the bodies it has cut
     * @param reaped     whether it has reaped the plants inside, its one slot set on the first tick it cuts
     */
    private record Cut(AABB box, long launchedAt, int lifeTicks, int slasherId, float damage, Set<Integer> struck,
                       boolean[] reaped) {
    }

    /**
     * Sets a slash's box cutting for its life.
     *
     * @param level      the server level
     * @param box        the box it cuts
     * @param launchedAt the game tick the slash appears
     * @param lifeTicks  the ticks it stands
     * @param slasherId  the id of the entity slashing
     * @param damage     what its cut deals
     */
    public static void launch(ServerLevel level, AABB box, long launchedAt, int lifeTicks, int slasherId,
                              float damage) {
        CUTTING.computeIfAbsent(level, key -> new ArrayList<>())
                .add(new Cut(box, launchedAt, lifeTicks, slasherId, damage, new HashSet<>(), new boolean[1]));
    }

    /**
     * Cuts with each standing box one tick further, and drops each whose slash has wiped away.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Cut> cuts = CUTTING.get(level);
        if (cuts == null || cuts.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        cuts.removeIf(cut -> cutTick(level, cut, now));
    }

    /**
     * Reaps on the first tick a box cuts, and cuts each tick it stands.
     *
     * @param level the server level
     * @param cut   the box
     * @param now   the game tick
     * @return true once the slash has wiped away
     */
    private static boolean cutTick(ServerLevel level, Cut cut, long now) {
        long age = now - cut.launchedAt();
        if (age < 0) {
            return false;
        }
        if (!cut.reaped()[0]) {
            cut.reaped()[0] = true;
            reapInside(level, cut.box());
        }
        for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, cut.box(),
                body -> body.getId() != cut.slasherId() && body.isAlive())) {
            if (cut.struck().add(body.getId())) {
                hurt(level, body, cut.damage());
            }
        }
        return age >= cut.lifeTicks();
    }

    private static void reapInside(ServerLevel level, AABB box) {
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - FACE_TRIM, box.maxY - FACE_TRIM, box.maxZ - FACE_TRIM))) {
            BlockState stood = level.getBlockState(cell);
            if (Reaping.reap(level, cell)) {
                BlockVisuals.sendToWatchers(level, cell, new BlockAfterimagePayload(cell.immutable(),
                        Block.getId(stood), GooTypes.LEAF, AFTERIMAGE_TICKS));
            }
        }
    }

    /**
     * Cuts a body with the magic Leaf's toxin hurts with, clearing its
     * immunity on both sides so every slash's cut lands and flashes, and puffs
     * a sweep where the slash connects.
     *
     * @param level  the server level
     * @param body   the body cut
     * @param damage what the cut deals
     */
    private static void hurt(ServerLevel level, LivingEntity body, float damage) {
        body.invulnerableTime = 0;
        body.hurtServer(level, level.damageSources().magic(), damage);
        body.invulnerableTime = 0;
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, body.getX(), body.getY() + body.getBbHeight() * BODY_MIDDLE,
                body.getZ(), 1, 0, 0, 0, 0);
    }
}
