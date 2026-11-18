package com.mercuriusxeno.goo.ability.tartarus;

import com.mercuriusxeno.goo.ability.program.ChannelAim;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's Tartarus hold, resolved on its first tick before the hold
 * drains or consumes anything and kept through it: where the gate leads and
 * the safe spot the search found there, or a refusal the player is told of.
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 */
public final class TartarusHolds {

    /** The action bar line a refused hold shows. */
    public static final String NO_SAFE_FLOOR = "goo.ability.nether.tartarus.no_safe_floor";

    private static final Map<UUID, TartarusCast> CASTS = new ConcurrentHashMap<>();

    private TartarusHolds() {
    }

    /**
     * The player's hold this tick: resolved on its first tick, kept after.
     *
     * @param player the player channeling Tartarus
     * @param reach  how many blocks around the scaled spot's column the search looks
     * @param held   the hold's tick count
     * @return the hold
     */
    public static TartarusCast castFor(ServerPlayer player, int reach, int held) {
        TartarusCast kept = CASTS.get(player.getUUID());
        if (held != ChannelAim.FIRST_TICK && kept != null) {
            return kept;
        }
        TartarusCast cast = resolve(player, reach);
        CASTS.put(player.getUUID(), cast);
        if (cast.refused()) {
            player.sendSystemMessage(Component.translatable(NO_SAFE_FLOOR), true);
        }
        return cast;
    }

    /**
     * Where the player's hold carries them.
     *
     * @param player the player channeling
     * @return the arrival, empty where no hold stands or it is refused
     */
    public static Optional<TartarusArrival> arrivalOf(ServerPlayer player) {
        return Optional.ofNullable(CASTS.get(player.getUUID())).flatMap(TartarusCast::arrival);
    }

    /**
     * Drops the player's hold, its gate having carried them.
     *
     * @param player the player carried
     */
    static void forget(ServerPlayer player) {
        CASTS.remove(player.getUUID());
    }

    private static TartarusCast resolve(ServerPlayer player, int reach) {
        ServerLevel here = player.level();
        Optional<ServerLevel> there = TartarusDestination.targetOf(here.dimension())
                .map(target -> here.getServer().getLevel(target));
        Optional<TartarusDestination> destination = there.flatMap(level -> TartarusDestination.from(
                here.dimension(), player.blockPosition(), TartarusLevelCells.spanOf(here),
                TartarusLevelCells.spanOf(level)));
        return TartarusCast.resolve(destination, to -> there.flatMap(level -> TartarusPlacement.nearest(
                new TartarusLevelCells(level), to.start(), reach, TartarusLevelCells.spanOf(level))),
                player.getYRot());
    }
}
