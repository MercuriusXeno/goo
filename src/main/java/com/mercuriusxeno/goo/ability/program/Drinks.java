package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.entity.FormingGooBlob;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.DrinkPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCapabilities;
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
 * lands, the melting stand-in takes its place and its chunks stream to the
 * forming goo blob nearest it; when it is drained the stand-in goes; and when
 * its last chunk has had time to reach the blob its goo fills it. Letting go
 * of the use cuts none of it.
 * decision unmake-waves-dissolve-by-crucible-cost
 * unmake-converges-near-the-unmade-blocks-into-a-goo-item
 */
public final class Drinks {

    /** Ticks a drink may go unheld before its hold has ended, past the stream's batching. */
    public static final int HOLD_GRACE_TICKS = 5;

    /**
     * One block picked into the glove.
     *
     * @param value  its exact goo value, credited through the player's ledger when it is paid
     * @param picked the game time it was picked, the square leaving the hand
     * @param start  the game time the square lands and it starts to melt
     * @param end    the game time it is drained and the stand-in goes
     * @param payAt  the game time its last chunk has reached its blob and its goo fills it
     * @param blob   the forming goo blob its chunks converge on
     */
    public record Siphon(GooValue value, long picked, long start, long end, long payAt, FormingGooBlob blob) {
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
         * Ends each siphon whose last chunk has reached its blob.
         *
         * @param now the game time
         * @return each such siphon
         */
        List<Siphon> payDue(long now) {
            List<Siphon> paid = new ArrayList<>();
            Iterator<Map.Entry<BlockPos, Siphon>> due = siphons.entrySet().iterator();
            while (due.hasNext()) {
                Map.Entry<BlockPos, Siphon> siphon = due.next();
                if (siphon.getValue().payAt() <= now) {
                    due.remove();
                    begun.remove(siphon.getKey());
                    drained.remove(siphon.getKey());
                    paid.add(siphon.getValue());
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
                    siphon.start(), siphon.end(), siphon.blob().resolve().getId())));
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
     * reached its blob fills it; shows every open drink's blocks to its
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
            drink.payDue(now).forEach(siphon -> payOut(drink, entry.getKey(), player, siphon));
            if (!drink.heldAt(now) && !drink.streaming()) {
                entries.remove();
            } else if (player != null && drink.streaming()) {
                EntityVisuals.sendToWatchers(player, new DrinkPayload(player.getId(), drink.shown()));
            }
        }
    }

    /**
     * Fills a finished block's blob with its goo, its exact value credited
     * through the drinker's ledger, the part under 1 mB kept on it: the ledger
     * their remainder capability serves, or the one kept for them until they
     * return once they have left.
     * decision every-producer-consumer-and-container-holds-a-remainder-buffer
     * unmake-converges-near-the-unmade-blocks-into-a-goo-item
     *
     * @param drink   the drink
     * @param drinker the drinking player's id
     * @param player  the drinking player, or null once they have left
     * @param siphon  the finished block's siphon
     */
    static void payOut(Drink drink, UUID drinker, @Nullable ServerPlayer player, Siphon siphon) {
        IRemainderLedger ledger = player == null ? OfflineRemainders.get(drink.level()).ledgerOf(drinker)
                : GooCapabilities.remainderLedgerOf(player);
        siphon.blob().feed(payoutThrough(siphon.value(), ledger));
    }

    /**
     * Puts whole goo into a player's inventory, dropping at their feet what has no space.
     *
     * @param level  the level
     * @param player the player
     * @param goo    the whole mB to give
     */
    static void give(ServerLevel level, ServerPlayer player, GooContents goo) {
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : goo.getAll().entrySet()) {
            ItemStack stack = GooStacks.createForOutput(entry.getKey(), entry.getValue());
            if (!stack.isEmpty() && !player.addItem(stack) && !stack.isEmpty()) {
                ItemEntity item = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack, 0, 0, 0);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
    }

    /**
     * What a drunk block lands: its exact value credited through the ledger, the
     * whole mB the credit answers landing and the part under 1 mB staying on the ledger.
     * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
     *
     * @param value  the block's exact goo value
     * @param ledger the drinking player's ledger
     * @return the whole mB that land
     */
    static GooContents payoutThrough(GooValue value, IRemainderLedger ledger) {
        return new GooContents(ledger.creditAllExact(value.exactAll()));
    }

    /** Forgets every drink, as a server stop does. */
    public void clear() {
        drinks.clear();
    }
}
