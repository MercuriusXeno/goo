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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's Unmake drink: the blocks streaming into the glove while
 * the channel is held. Each tick a block's siphon ends, the block goes and its
 * goo goes into the player's inventory, dropping at their feet only what has
 * no space.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class Drinks {

    /** Ticks a drink may go unheld before its hold has ended, past the stream's batching. */
    public static final int HOLD_GRACE_TICKS = 5;

    /**
     * One block streaming into the glove.
     *
     * @param goo   the goo it gives
     * @param start the game time it started
     * @param end   the game time it is done
     */
    public record Siphon(GooContents goo, long start, long end) {
    }

    /**
     * One player's drink.
     */
    public static final class Drink {
        private final ServerLevel level;
        private final Map<BlockPos, Siphon> siphons = new LinkedHashMap<>();
        private long lastHeld;

        private Drink(ServerLevel level, long now) {
            this.level = level;
            this.lastHeld = now;
        }

        /**
         * @param pos a block
         * @return whether it is already streaming into this drink
         */
        public boolean siphoning(BlockPos pos) {
            return siphons.containsKey(pos);
        }

        /**
         * Starts a block streaming.
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
         * Ends each siphon that is done, its block gone.
         *
         * @param now the game time
         * @return each finished block with the goo it gives
         */
        Map<BlockPos, GooContents> finishDone(long now) {
            Map<BlockPos, GooContents> finished = new LinkedHashMap<>();
            Iterator<Map.Entry<BlockPos, Siphon>> done = siphons.entrySet().iterator();
            while (done.hasNext()) {
                Map.Entry<BlockPos, Siphon> siphon = done.next();
                if (siphon.getValue().end() <= now) {
                    done.remove();
                    if (level.getBlockState(siphon.getKey()).is(GooBlocks.MELTING_BLOCK.get())) {
                        level.removeBlock(siphon.getKey(), false);
                    }
                    finished.put(siphon.getKey(), siphon.getValue().goo());
                }
            }
            return finished;
        }

        /**
         * @return whether a block is still streaming
         */
        boolean streaming() {
            return !siphons.isEmpty();
        }

        /**
         * @return the blocks streaming, as the payload shows them
         */
        List<DrinkPayload.Streaming> shown() {
            List<DrinkPayload.Streaming> streaming = new ArrayList<>();
            siphons.forEach((pos, siphon) -> streaming.add(new DrinkPayload.Streaming(pos, siphon.start(),
                    siphon.end())));
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
     * Ends each siphon that is done, paying its goo out, shows every open
     * drink's streaming blocks to its viewers, and forgets each whose hold
     * has ended with nothing left streaming.
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
            drink.finishDone(now).forEach((pos, goo) -> payOut(drink.level(), player, pos, goo));
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
