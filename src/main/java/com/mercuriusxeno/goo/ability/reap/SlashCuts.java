package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.BlockAfterimagePayload;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Reap's slashes cutting on the server. A slash cuts what it is drawn over:
 * each tick of its life, placed from where its slasher stands that tick, it
 * reaps and replants every ripe plant its band's front has got to inside its
 * area, leaving the afterimage of how the plant stood, and cuts every living
 * body there but its slasher, once a slash, as the slasher's own attack, each
 * cut landing through the body's immunity so every slash connects. The cuts
 * live a few ticks and are never saved.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SlashCuts {

    /** The ticks a reaped plant's afterimage takes to fade. */
    static final int AFTERIMAGE_TICKS = 12;
    /** Trimmed off a box's far faces so a box ending on a cell's face holds none of that cell. */
    private static final double FACE_TRIM = 1e-6;
    private static final double HALF = 0.5;

    private static final Map<ServerLevel, List<Cut>> CUTTING = new WeakHashMap<>();

    private SlashCuts() {
    }

    /**
     * A slash as thrown.
     *
     * @param slash      the slash
     * @param thrownFrom where its slasher's feet stood as it was thrown, which it cuts from once no slasher stands
     * @param slasherId  the id of the entity slashing, which the slash moves with and never cuts
     */
    public record Throw(ReapSlash slash, Vec3 thrownFrom, int slasherId) {
    }

    /**
     * One slash cutting, and the bodies it has cut.
     *
     * @param thrown     the slash as thrown
     * @param launchedAt the game tick the slash appeared
     * @param damage     what its cut deals
     * @param struck     the ids of the bodies it has cut
     */
    private record Cut(Throw thrown, long launchedAt, float damage, Set<Integer> struck) {
    }

    /**
     * Sets a slash cutting for its life.
     *
     * @param level      the server level
     * @param thrown     the slash as thrown
     * @param launchedAt the game tick the slash appears
     * @param damage     what its cut deals
     */
    public static void launch(ServerLevel level, Throw thrown, long launchedAt, float damage) {
        CUTTING.computeIfAbsent(level, key -> new ArrayList<>())
                .add(new Cut(thrown, launchedAt, damage, new HashSet<>()));
    }

    /**
     * Cuts with each live slash one tick further, and drops each that has dimmed away.
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
     * Reaps and cuts what a slash has reached this tick, from where its slasher stands now.
     *
     * @param level the server level
     * @param cut   the slash cutting
     * @param now   the game tick
     * @return true once the slash has dimmed away
     */
    private static boolean cutTick(ServerLevel level, Cut cut, long now) {
        long age = now - cut.launchedAt();
        if (age < 0) {
            return false;
        }
        Throw thrown = cut.thrown();
        Entity slasher = level.getEntity(thrown.slasherId());
        Vec3 feet = slasher != null ? slasher.position() : thrown.thrownFrom();
        reapReached(level, thrown.slash(), feet, age);
        cutReached(level, cut, feet, age);
        return age >= thrown.slash().lifeTicks();
    }

    /**
     * Cuts every living body the slash has reached and not yet cut, but its slasher.
     *
     * @param level the server level
     * @param cut   the slash cutting
     * @param feet  where its slasher's feet stand now
     * @param age   ticks since the slash appeared
     */
    private static void cutReached(ServerLevel level, Cut cut, Vec3 feet, long age) {
        Throw thrown = cut.thrown();
        ReapSlash slash = thrown.slash();
        DamageSource attack = cutBy(level.damageSources(), level.getEntity(thrown.slasherId()));
        for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, slash.reachBox(feet),
                body -> body.getId() != thrown.slasherId() && body.isAlive())) {
            if (slash.reaches(feet, body.position(), body.getBbWidth() * HALF, age)
                    && cut.struck().add(body.getId())) {
                hurt(level, body, attack, cut.damage());
            }
        }
    }

    /**
     * Reaps every ripe plant the slash has reached: each cell at the
     * slasher's level whose middle the band's front has got to inside the
     * area. A plant reaped stands unripe, so a later tick leaves it be.
     *
     * @param level the server level
     * @param slash the slash
     * @param feet  where its slasher's feet stand now
     * @param age   ticks since the slash appeared
     */
    private static void reapReached(ServerLevel level, ReapSlash slash, Vec3 feet, long age) {
        AABB box = slash.reachBox(feet);
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - FACE_TRIM, box.maxY - FACE_TRIM, box.maxZ - FACE_TRIM))) {
            Vec3 middle = new Vec3(cell.getX() + HALF, cell.getY(), cell.getZ() + HALF);
            if (!slash.reaches(feet, middle, 0, age)) {
                continue;
            }
            BlockState stood = level.getBlockState(cell);
            if (Reaping.reap(level, cell)) {
                BlockVisuals.sendToWatchers(level, cell, new BlockAfterimagePayload(cell.immutable(),
                        Block.getId(stood), GooTypes.LEAF, AFTERIMAGE_TICKS));
            }
        }
    }

    /**
     * The damage a slash cuts with: its slasher's own attack, a player's
     * where a player slashes and a mob's where a mob does, as any blade's
     * hit is; generic where no slasher stands.
     *
     * @param sources the level's damage sources
     * @param slasher the entity slashing, or null where none stands
     * @return the damage source
     */
    static DamageSource cutBy(DamageSources sources, @Nullable Entity slasher) {
        if (slasher instanceof Player player) {
            return sources.playerAttack(player);
        }
        return slasher instanceof LivingEntity mob ? sources.mobAttack(mob) : sources.generic();
    }

    /**
     * Cuts a body as its slasher's attack, clearing its immunity on both
     * sides so every slash's cut lands and flashes.
     *
     * @param level  the server level
     * @param body   the body cut
     * @param source the slasher's attack
     * @param damage what the cut deals
     */
    private static void hurt(ServerLevel level, LivingEntity body, DamageSource source, float damage) {
        body.invulnerableTime = 0;
        body.hurtServer(level, source, damage);
        body.invulnerableTime = 0;
    }
}
