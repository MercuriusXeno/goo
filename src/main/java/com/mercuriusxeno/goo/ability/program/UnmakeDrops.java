package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.ChunkWatchers;
import com.mercuriusxeno.goo.network.UnmadePayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The goo unmade blocks and mobs leave, held back while their remains morph
 * into the goo item on the client and then dropped at rest where the morph
 * ends, so the item swaps in for its sprite cleanly.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeDrops {

    /** Ticks the remains take to morph into the goo item before it drops, half a second. */
    public static final int MORPH_TICKS = 10;

    private record Pending(ServerLevel level, Vec3 at, GooContents goo, int dueTick) {
    }

    private final List<Pending> pending = new ArrayList<>();

    /**
     * Starts the remains morphing on every client watching, and holds the goo
     * back until the morph ends.
     *
     * @param level the server level
     * @param at    where the remains stand and the goo drops
     * @param size  how big the remains start, in blocks
     * @param goo   the goo the unmade thing leaves
     */
    public void unmade(ServerLevel level, Vec3 at, float size, GooContents goo) {
        if (goo.isEmpty()) {
            return;
        }
        ChunkWatchers.send(level, BlockPos.containing(at), new UnmadePayload(at, goo.getAll(), size));
        pending.add(new Pending(level, at, goo, level.getServer().getTickCount() + MORPH_TICKS));
    }

    /**
     * Drops the goo of every morph that has ended.
     *
     * @param tick the server tick
     */
    public void dropArrived(int tick) {
        Iterator<Pending> drops = pending.iterator();
        while (drops.hasNext()) {
            Pending drop = drops.next();
            if (drop.dueTick() <= tick) {
                drops.remove();
                dropAtRest(drop.level(), drop.at(), drop.goo());
            }
        }
    }

    /** Forgets every held goo, as a server stop does. */
    public void clear() {
        pending.clear();
    }

    /**
     * Drops one goo item per type, at rest where the morph ended.
     *
     * @param level the server level
     * @param at    where the morph ended
     * @param goo   the goo
     */
    private static void dropAtRest(ServerLevel level, Vec3 at, GooContents goo) {
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : goo.getAll().entrySet()) {
            ItemStack stack = GooStacks.createForOutput(entry.getKey(), entry.getValue());
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack, 0, 0, 0);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

}
