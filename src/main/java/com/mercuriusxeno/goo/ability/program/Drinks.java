package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.DrinkPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every player's Unmake drink: the blocks picked into the glove while the
 * channel is held, each through its choreography. A picked block is paid for
 * at once and stands as itself while the square flies to it; when the square
 * lands, the melting stand-in takes its place and it streams in; when it is
 * drained the stand-in goes; and when its tail has had time to reach the
 * hand its goo goes into the player's inventory, dropping at their feet only
 * what has no space. Letting go of the use cuts none of it.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class Drinks {

    /** Ticks a drink may go unheld before its hold has ended, past the stream's batching. */
    public static final int HOLD_GRACE_TICKS = 5;

    /**
     * One block picked into the glove.
     *
     * @param goo    the goo it gives
     * @param picked the game time it was picked, the square leaving the hand
     * @param start  the game time the square lands and it starts to melt
     * @param end    the game time it is drained and the stand-in goes
     * @param payAt  the game time its tail has reached the hand and its goo is paid
     */
    public record Siphon(GooContents goo, long picked, long start, long end, long payAt) {
    }

    /**
     * One player's drink.
     */
    public static final class Drink {
        private final ServerLevel level;
        private final Map<BlockPos, Siphon> siphons = new LinkedHashMap<>();
        private final Set<BlockPos> begun = new HashSet<>();
        private final Set<BlockPos> drained = new HashSet<>();
        private long lastHeld;

        private Drink(ServerLevel level, long now) {
            this.level = level;
            this.lastHeld = now;
        }

        /**
         * @param pos a block
         * @return whether it is already picked into this drink
         */
        public boolean siphoning(BlockPos pos) {
            return siphons.containsKey(pos);
        }

        /**
         * Picks a block into the drink.
         *
         * @param pos    the block
         * @param siphon its goo and its times
         */
        public void start(BlockPos pos, Siphon siphon) {
            siphons.put(pos.immutable(), siphon);
        }

        /**
         * Holds the drink this tick.
         *
         * @param now the game time
         */
        void holdAt(long now) {
            lastHeld = now;
        }

        /**
         * @param now the game time
         * @return whether the hold has gone on within the grace
         */
        boolean heldAt(long now) {
            return now - lastHeld <= HOLD_GRACE_TICKS;
        }

        /**
         * @return the level the drink is in
         */
        ServerLevel level() {
            return level;
        }

        /**
         * Starts each block whose square has landed melting: the stand-in takes
         * its place. A block no longer standing when its square lands is dropped
         * from the drink, nothing to melt.
         *
         * @param now the game time
         */
        void beginDue(long now) {
            Iterator<Map.Entry<BlockPos, Siphon>> due = siphons.entrySet().iterator();
            while (due.hasNext()) {
                Map.Entry<BlockPos, Siphon> siphon = due.next();
                BlockPos pos = siphon.getKey();
                if (siphon.getValue().start() > now || !begun.add(pos)) {
                    continue;
                }
                if (level.getBlockState(pos).isAir()) {
                    due.remove();
                } else {
                    BlockMelts.siphon(level, pos, siphon.getValue().end());
                }
            }
        }

        /**
         * Takes the stand-in away from each block that is drained.
         *
         * @param now the game time
         */
        void drainDue(long now) {
            siphons.forEach((pos, siphon) -> {
                if (siphon.end() <= now && drained.add(pos)
                        && level.getBlockState(pos).is(GooBlocks.MELTING_BLOCK.get())) {
                    level.removeBlock(pos, false);
                }
            });
        }

        /**
         * Ends each siphon whose goo has reached the hand.
         *
         * @param now the game time
         * @return each such block with the goo it gives
         */
        Map<BlockPos, GooContents> payDue(long now) {
            Map<BlockPos, GooContents> paid = new LinkedHashMap<>();
            Iterator<Map.Entry<BlockPos, Siphon>> due = siphons.entrySet().iterator();
            while (due.hasNext()) {
                Map.Entry<BlockPos, Siphon> siphon = due.next();
                if (siphon.getValue().payAt() <= now) {
                    due.remove();
                    begun.remove(siphon.getKey());
                    drained.remove(siphon.getKey());
                    paid.put(siphon.getKey(), siphon.getValue().goo());
                }
            }
            return paid;
        }

        /**
         * @return whether a block is still on its way
         */
        boolean streaming() {
            return !siphons.isEmpty();
        }

        /**
         * @return the blocks on their way, as the payload shows them
         */
        List<DrinkPayload.Streaming> shown() {
            List<DrinkPayload.Streaming> streaming = new ArrayList<>();
            siphons.forEach((pos, siphon) -> streaming.add(new DrinkPayload.Streaming(pos, siphon.picked(),
                    siphon.start(), siphon.end())));
            return streaming;
        }
    }

    private final Map<UUID, Drink> drinks = new HashMap<>();

    /**
     * Holds a player's drink this tick, starting it on the hold's first tick.
     *
     * @param player the channeling player
     * @return the drink
     */
    public Drink hold(ServerPlayer player) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Drink drink = drinks.computeIfAbsent(player.getUUID(), ignored -> new Drink(level, now));
        drink.holdAt(now);
        return drink;
    }

    /**
     * @param player a player
     * @return their drink, or null while they hold none
     */
    public @Nullable Drink of(ServerPlayer player) {
        return drinks.get(player.getUUID());
    }

    /**
     * Moves every drink's blocks through their choreography this tick: squares
     * landing start melts, drained blocks lose their stand-ins, goo that has
     * reached the hand is paid out; shows every open drink's blocks to its
     * viewers, and forgets each whose hold has ended with nothing left on its way.
     *
     * @param server the server
     */
    public void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Drink>> entries = drinks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Drink> entry = entries.next();
            Drink drink = entry.getValue();
            long now = drink.level().getGameTime();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            drink.beginDue(now);
            drink.drainDue(now);
            drink.payDue(now).forEach((pos, goo) -> payOut(drink.level(), player, pos, goo));
            if (!drink.heldAt(now) && !drink.streaming()) {
                entries.remove();
            } else if (player != null && drink.streaming()) {
                EntityVisuals.sendToWatchers(player, new DrinkPayload(player.getId(), drink.shown()));
            }
        }
    }

    /**
     * Pays a finished block's goo into the player's inventory, dropping at
     * their feet only what has no space; a player who has left has it drop
     * where the block stood.
     *
     * @param level  the level
     * @param player the drinking player, or null once they have left
     * @param pos    the finished block
     * @param goo    its goo
     */
    static void payOut(ServerLevel level, @Nullable ServerPlayer player, BlockPos pos, GooContents goo) {
        if (player == null) {
            GooStacks.dropAll(goo, level, pos);
            return;
        }
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : goo.getAll().entrySet()) {
            ItemStack stack = GooStacks.createForOutput(entry.getKey(), entry.getValue());
            if (!stack.isEmpty() && !player.addItem(stack) && !stack.isEmpty()) {
                ItemEntity item = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack, 0, 0, 0);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
    }

    /** Forgets every drink, as a server stop does. */
    public void clear() {
        drinks.clear();
    }
}
