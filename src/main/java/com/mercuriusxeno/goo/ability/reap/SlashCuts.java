package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The slashes of Reap in flight, cutting the living they cross: each tick a
 * slash flies the next stretch of its path and cuts every living body that
 * stretch crosses within the blade's half width, other than its slasher.
 * A slash cuts a body once; a body cut keeps every slash off for its
 * invulnerability, so a flurry stacks cut on cut into a powerful close range
 * attack at the cadence the JSON names. The slashes live a few ticks and are
 * never saved.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SlashCuts {

    /** How far either side of its path a slash's blade reaches, in blocks. */
    static final double BLADE_HALF_WIDTH = 0.6;

    private static final Map<ServerLevel, List<Flying>> FLYING = new WeakHashMap<>();
    private static final Map<ServerLevel, Cooldowns> COOLDOWNS = new WeakHashMap<>();

    private SlashCuts() {
    }

    /**
     * One slash's cut.
     *
     * @param slasherId         the id of the entity slashing, which the slash never cuts
     * @param origin            where it launches
     * @param path              its flight, its heading scaled to its reach
     * @param launchedAt        the game tick it launches
     * @param flightTicks       the ticks it takes to fly its reach
     * @param damage            what each cut deals
     * @param invulnerableTicks the ticks a body cut keeps every slash off
     */
    public record Cut(int slasherId, Vec3 origin, Vec3 path, long launchedAt, int flightTicks, float damage,
                      int invulnerableTicks) {

        /**
         * Where the slash has flown an age into its flight.
         *
         * @param age ticks since it launched
         * @return the point it has reached
         */
        Vec3 reachedAt(long age) {
            return origin.add(path.scale(Math.clamp((double) age / flightTicks, 0, 1)));
        }
    }

    /**
     * The bodies cut in a level, each with the tick it opens to a cut again.
     */
    public static final class Cooldowns {

        private final Map<Integer, Long> openAt = new HashMap<>();

        /**
         * Whether a body may be cut now.
         *
         * @param target the body's entity id
         * @param now    the game tick
         * @return true once its invulnerability has lapsed
         */
        boolean open(int target, long now) {
            return openAt.getOrDefault(target, Long.MIN_VALUE) <= now;
        }

        /**
         * Keeps a body cut now off every slash for its invulnerability.
         *
         * @param target the body's entity id
         * @param now    the game tick
         * @param window the ticks it is kept off
         */
        void strike(int target, long now, int window) {
            openAt.put(target, now + window);
        }

        private void forgetLapsed(long now) {
            openAt.values().removeIf(open -> open <= now);
        }
    }

    /**
     * The bodies one slash has cut.
     */
    public static final class Strikes {

        private final Set<Integer> struck = new HashSet<>();

        /**
         * Claims a cut of a body for this slash: a body it has cut, or one
         * still kept off by another cut, is refused.
         *
         * @param target    the body's entity id
         * @param now       the game tick
         * @param window    the ticks the cut keeps the body off
         * @param cooldowns the level's cut bodies
         * @return true when the cut lands
         */
        public boolean claim(int target, long now, int window, Cooldowns cooldowns) {
            if (struck.contains(target) || !cooldowns.open(target, now)) {
                return false;
            }
            struck.add(target);
            cooldowns.strike(target, now, window);
            return true;
        }
    }

    private record Flying(Cut cut, Strikes strikes) {
    }

    /**
     * Whether a body is cut by a stretch of a slash's path: the stretch
     * passes within the blade's half width of it, or starts inside that reach.
     *
     * @param box  the body's bounds
     * @param from where the stretch starts
     * @param to   where it ends
     * @return true when the body is cut
     */
    public static boolean crosses(AABB box, Vec3 from, Vec3 to) {
        AABB reach = box.inflate(BLADE_HALF_WIDTH);
        return reach.contains(from) || reach.clip(from, to).isPresent();
    }

    /**
     * Sets a slash flying to cut the living its path crosses.
     *
     * @param level the server level
     * @param cut   the slash's cut
     */
    public static void launch(ServerLevel level, Cut cut) {
        FLYING.computeIfAbsent(level, key -> new ArrayList<>()).add(new Flying(cut, new Strikes()));
    }

    /**
     * Flies each slash the next stretch of its path, cutting what it crosses,
     * and drops each that has flown its reach.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Flying> slashes = FLYING.get(level);
        if (slashes == null || slashes.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Cooldowns cooldowns = COOLDOWNS.computeIfAbsent(level, key -> new Cooldowns());
        slashes.removeIf(slash -> flyStretch(level, slash, cooldowns, now));
        cooldowns.forgetLapsed(now);
    }

    /**
     * Cuts the living a slash crosses this tick.
     *
     * @param level     the server level
     * @param slash     the slash
     * @param cooldowns the level's cut bodies
     * @param now       the game tick
     * @return true once the slash has flown its reach
     */
    private static boolean flyStretch(ServerLevel level, Flying slash, Cooldowns cooldowns, long now) {
        Cut cut = slash.cut();
        long age = now - cut.launchedAt();
        if (age < 0) {
            return false;
        }
        Vec3 from = cut.reachedAt(age - 1);
        Vec3 to = cut.reachedAt(age);
        AABB stretch = new AABB(from, to).inflate(BLADE_HALF_WIDTH);
        for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, stretch,
                body -> body.getId() != cut.slasherId() && body.isAlive())) {
            if (crosses(body.getBoundingBox(), from, to)
                    && slash.strikes().claim(body.getId(), now, cut.invulnerableTicks(), cooldowns)) {
                hurt(level, body, cut.damage());
            }
        }
        return age >= cut.flightTicks();
    }

    /**
     * Deals a cut with the magic Leaf's toxin hurts with, clearing vanilla's
     * immunity on both sides so the slashes' own cadence alone decides which
     * cuts land.
     *
     * @param level  the server level
     * @param body   the body cut
     * @param damage what the cut deals
     */
    private static void hurt(ServerLevel level, LivingEntity body, float damage) {
        body.invulnerableTime = 0;
        body.hurtServer(level, level.damageSources().magic(), damage);
        body.invulnerableTime = 0;
    }
}
