package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooItem;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Every player's Unmake drink: the blocks picked into the glove while the
 * channel is held, each through its choreography. A picked block is paid for
 * at once and stands as itself while the square flies to it; when the square
 * lands, the melting stand-in takes its place and its chunks stream to the
 * drink's convergence point near the blocks it unmakes; when it is drained
 * the stand-in goes; and when its last chunk has had time to reach the point
 * its goo forms there as a goo item. Letting go of the use cuts none of it.
 * decision unmake-waves-dissolve-by-crucible-cost
 * unmake-converges-near-the-unmade-blocks-into-a-goo-item
 */
public final class Drinks {

    /** Ticks a drink may go unheld before its hold has ended, past the stream's batching. */
    public static final int HOLD_GRACE_TICKS = 5;
    private static final String UNCONVERGED = "a drink holds its convergence point from its first block on";

    /**
     * One block picked into the glove.
     *
     * @param value  its exact goo value, credited through the player's ledger when it is paid
     * @param picked the game time it was picked, the square leaving the hand
     * @param start  the game time the square lands and it starts to melt
     * @param end    the game time it is drained and the stand-in goes
     * @param payAt  the game time its last chunk has reached the convergence point and its goo forms
     */
    public record Siphon(GooValue value, long picked, long start, long end, long payAt) {
    }

    /**
     * One player's drink.
     */
    public static final class Drink {
        private final ServerLevel level;
        private final Map<BlockPos, Siphon> siphons = new LinkedHashMap<>();
        private final Set<BlockPos> begun = new HashSet<>();
        private final Set<BlockPos> drained = new HashSet<>();
        /** The goo item of each type this drink formed at its convergence point, grown as more blocks arrive. */
        private final Map<ResourceKey<GooTypeDefinition>, ItemEntity> formed = new HashMap<>();
        private @Nullable Vec3 convergence;
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
            convergeOn(pos);
            siphons.put(pos.immutable(), siphon);
        }

        /**
         * The point this drink's chunks converge on with a block joining it,
         * chosen with its first block and held while more join.
         * unmake-converges-near-the-unmade-blocks-into-a-goo-item
         *
         * @param joining the block joining the drink
         * @return the convergence point
         */
        public Vec3 convergeOn(BlockPos joining) {
            Set<BlockPos> unmaking = new HashSet<>(siphons.keySet());
            unmaking.add(joining.immutable());
            convergence = convergencePoint(convergence, unmaking);
            return convergence;
        }

        /**
         * @return the point this drink's chunks converge on, or null before its first block
         */
        @Nullable Vec3 convergence() {
            return convergence;
        }

        /**
         * Forms a finished block's goo at the convergence point, growing the
         * goo item of its type this drink already formed there, or making one
         * where none stands.
         * unmake-converges-near-the-unmade-blocks-into-a-goo-item
         *
         * @param goo the whole mB the block lands
         */
        void form(GooContents goo) {
            Vec3 at = Objects.requireNonNull(convergence, UNCONVERGED);
            goo.getAll().forEach((type, amount) -> {
                ItemEntity item = formed.get(type);
                if (item != null && item.isAlive()) {
                    ItemStack grown = item.getItem().copy();
                    GooItem.setVolume(grown, GooItem.getVolume(grown) + amount);
                    item.setItem(grown);
                    return;
                }
                ItemStack stack = GooStacks.createForOutput(type, amount);
                if (!stack.isEmpty()) {
                    ItemEntity made = new ItemEntity(level, at.x, at.y, at.z, stack, 0, 0, 0);
                    made.setDefaultPickUpDelay();
                    level.addFreshEntity(made);
                    formed.put(type, made);
                }
            });
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
         * Ends each siphon whose last chunk has reached the convergence point.
         *
         * @param now the game time
         * @return each such block's exact goo value
         */
        List<GooValue> payDue(long now) {
            List<GooValue> paid = new ArrayList<>();
            Iterator<Map.Entry<BlockPos, Siphon>> due = siphons.entrySet().iterator();
            while (due.hasNext()) {
                Map.Entry<BlockPos, Siphon> siphon = due.next();
                if (siphon.getValue().payAt() <= now) {
                    due.remove();
                    begun.remove(siphon.getKey());
                    drained.remove(siphon.getKey());
                    paid.add(siphon.getValue().value());
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
            drink.payDue(now).forEach(value -> payOut(drink, entry.getKey(), player, value));
            if (!drink.heldAt(now) && !drink.streaming()) {
                entries.remove();
            } else if (player != null && drink.streaming()) {
                EntityVisuals.sendToWatchers(player, new DrinkPayload(player.getId(),
                        Objects.requireNonNull(drink.convergence(), UNCONVERGED),
                        drink.shown()));
            }
        }
    }

    /**
     * Forms a finished block's goo as a goo item at the drink's convergence
     * point, its exact value credited through the drinker's ledger, the part
     * under 1 mB kept on it: the ledger their remainder capability serves, or
     * the one kept for them until they return once they have left.
     * decision every-producer-consumer-and-container-holds-a-remainder-buffer
     * unmake-converges-near-the-unmade-blocks-into-a-goo-item
     *
     * @param drink   the drink
     * @param drinker the drinking player's id
     * @param player  the drinking player, or null once they have left
     * @param value   the finished block's exact goo value
     */
    static void payOut(Drink drink, UUID drinker, @Nullable ServerPlayer player, GooValue value) {
        IRemainderLedger ledger = player == null ? OfflineRemainders.get(drink.level()).ledgerOf(drinker)
                : GooCapabilities.remainderLedgerOf(player);
        drink.form(payoutThrough(value, ledger));
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

    /**
     * The point an activation's chunks converge on and its goo item forms at:
     * the one already chosen, held while blocks join, or else the middle of
     * the unmaking block nearest the middle of them all.
     * unmake-converges-near-the-unmade-blocks-into-a-goo-item
     *
     * @param held     the point this activation already chose, or null before its first block
     * @param unmaking the blocks this activation unmakes, at least one
     * @return the convergence point
     */
    static Vec3 convergencePoint(@Nullable Vec3 held, Collection<BlockPos> unmaking) {
        if (held != null) {
            return held;
        }
        Vec3 middle = unmaking.stream().map(Vec3::atCenterOf).reduce(Vec3.ZERO, Vec3::add)
                .scale(1.0 / unmaking.size());
        return unmaking.stream().map(Vec3::atCenterOf)
                .min(Comparator.comparingDouble(center -> center.distanceToSqr(middle))).orElseThrow();
    }

    /** Forgets every drink, as a server stop does. */
    public void clear() {
        drinks.clear();
    }
}
