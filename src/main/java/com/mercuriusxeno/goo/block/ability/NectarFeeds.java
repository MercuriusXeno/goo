package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.nectar.Feeding;
import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;

/**
 * What a Nectar cell does to the mobs about it, a universal food: the
 * animals, slimes and hostiles within the step's reach walk to it; an
 * animal reaching it is fed, an adult set in love or a baby grown a stage,
 * and a slime grows a size step, each eating one layer of the cell, which
 * is gone once its last layer is eaten; hostiles reaching it together turn
 * on each other over it for the step's fight time, then may try again.
 * nectar-goo-keeps-feeds-effects-as-a-universal-food
 * each-feeding-eats-a-layer-of-nectar
 * slimes-grow-on-jelly-feed
 */
public final class NectarFeeds {

    /** Blocks from the goo's top a mob reaches it within. */
    public static final double REACH = 1.5;
    /** Hostiles that must reach the goo together before they fight over it. */
    private static final int FIGHTERS_TO_FIGHT = 2;
    /** How near the cell a lured mob's path may end and still reach it: beside it, inside the feeding reach. */
    private static final int BESIDE_THE_CELL = 1;
    /** The speed a lured mob walks toward the goo at, its navigation's own multiple. */
    private static final double LURE_SPEED = 1.0;

    private NectarFeeds() {
    }

    /**
     * Lures the mobs in reach, ends a fight whose time has run, and, where
     * no fight stands, starts one or feeds the first mob reaching the goo.
     *
     * @param level  the server level
     * @param cell   the Nectar cell
     * @param state  the cell's state
     * @param nectar Nectar's step, naming the reach and the fight time
     */
    static void feedAround(ServerLevel level, BlockPos cell, BlockState state, NectarGooStep nectar) {
        Vec3 top = Vec3.atBottomCenterOf(cell)
                .add(0, (double) VolumeCellBlock.layersOf(state) / VolumeCellBlock.MAX_LAYERS, 0);
        lure(level, cell, top, nectar.reach());
        NectarFights fights = GooServerState.of(level.getServer()).nectarFights();
        Optional<NectarFights.Fight> fight = fights.over(level, cell);
        long now = level.getGameTime();
        if (fight.isPresent() && now < fight.get().endsAt()) {
            return;
        }
        fight.ifPresent(over -> endFight(level, cell, fights, over));
        List<Mob> reaching = level.getEntitiesOfClass(Mob.class, new AABB(top, top).inflate(REACH), Mob::isAlive);
        List<Mob> hostiles = reaching.stream().filter(NectarFeeds::fightsOverIt).toList();
        if (hostiles.size() >= FIGHTERS_TO_FIGHT) {
            startFight(level, cell, fights, hostiles, now + nectar.fightTicks());
            return;
        }
        reaching.stream().filter(mob -> eats(mob, state)).findFirst().ifPresent(eater -> eat(level, cell, eater));
    }

    private static boolean fightsOverIt(Mob mob) {
        return mob instanceof Enemy && !(mob instanceof Slime);
    }

    /**
     * Walks each craving, free mob in reach toward the cell, where its path
     * there reaches the cell; a mob already walking to a Nectar cell it
     * reaches is left on its way, so two cells in reach of one mob never
     * take its path back and forth, and a cell behind a wall never draws it
     * from one it can reach.
     *
     * @param level the server level
     * @param cell  the Nectar cell
     * @param top   the goo's top
     * @param reach the blocks out to which the cell draws mobs
     */
    private static void lure(ServerLevel level, BlockPos cell, Vec3 top, double reach) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(top, top).inflate(reach), Mob::isAlive)) {
            if (craves(mob) && isFree(mob) && !headsToNectar(level, mob)) {
                walkToward(mob, cell);
            }
        }
    }

    /**
     * Sets a mob walking to beside the cell, where its path there reaches it.
     *
     * @param mob  the lured mob
     * @param cell the Nectar cell
     */
    private static void walkToward(Mob mob, BlockPos cell) {
        Path there = mob.getNavigation().createPath(cell, BESIDE_THE_CELL);
        if (there != null && there.canReach()) {
            mob.getNavigation().moveTo(there, LURE_SPEED);
        }
    }

    /**
     * @param mob a mob in reach
     * @return true for a walking animal or hostile, which the goo draws
     */
    private static boolean craves(Mob mob) {
        return mob instanceof PathfinderMob && (mob instanceof Animal || mob instanceof Enemy);
    }

    /**
     * @param mob a mob in reach
     * @return true where the mob thinks for itself and chases nothing
     */
    private static boolean isFree(Mob mob) {
        return mob.getTarget() == null && !mob.isNoAi();
    }

    /**
     * Whether a mob is already walking to a Nectar cell it reaches, so the
     * lure leaves it be; a path another goal laid since is taken back.
     *
     * @param level the server level
     * @param mob   a mob within the reach
     * @return true when its path reaches a Nectar cell
     */
    private static boolean headsToNectar(ServerLevel level, Mob mob) {
        Path path = mob.getNavigation().getPath();
        return path != null && !path.isDone() && path.canReach()
                && level.getBlockState(path.getTarget()).getBlock() instanceof NectarCellBlock;
    }

    private static boolean eats(Mob mob, BlockState state) {
        if (mob instanceof Animal animal) {
            return Feeding.mealOf(VolumeCellBlock.layersOf(state), animal.getAge(), animal.canFallInLove())
                    .isPresent();
        }
        return mob instanceof Slime slime && slime.getSize() < Slime.MAX_NATURAL_SIZE;
    }

    /**
     * Feeds a mob reaching the goo and eats one layer of the cell, the cell
     * gone where that was its last.
     *
     * @param level the server level
     * @param cell  the cell
     * @param eater the mob eating
     */
    private static void eat(ServerLevel level, BlockPos cell, Mob eater) {
        BlockState state = level.getBlockState(cell);
        int left = VolumeCellBlock.layersOf(state) - 1;
        if (eater instanceof Animal animal) {
            Feeding.mealOf(left + 1, animal.getAge(), animal.canFallInLove()).ifPresent(meal -> {
                animal.setAge(meal.ageAfter());
                if (meal.fallsInLove()) {
                    animal.setInLove(null);
                }
            });
        } else if (eater instanceof Slime slime) {
            Feeding.feed(slime);
        }
        Feeding.crumbsAndHearts(level, eater);
        level.setBlock(cell, left > 0 ? state.setValue(VolumeCellBlock.LAYERS, left)
                : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void startFight(ServerLevel level, BlockPos cell, NectarFights fights, List<Mob> hostiles,
                                   long endsAt) {
        for (int index = 0; index < hostiles.size(); index++) {
            hostiles.get(index).setTarget(hostiles.get((index + 1) % hostiles.size()));
        }
        fights.start(level, cell, new NectarFights.Fight(endsAt, hostiles.stream().map(Entity::getId).toList()));
    }

    private static void endFight(ServerLevel level, BlockPos cell, NectarFights fights, NectarFights.Fight fight) {
        for (int id : fight.fighterIds()) {
            if (level.getEntity(id) instanceof Mob fighter && fighter.getTarget() != null
                    && fight.fighterIds().contains(fighter.getTarget().getId())) {
                fighter.setTarget(null);
            }
        }
        fights.end(level, cell);
    }
}
