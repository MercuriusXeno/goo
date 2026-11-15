package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.BlastShardsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;

/**
 * A Blast's broken blocks on every watching client: each cell the budget
 * picks throws its own block's break shards where the block stood, flung as
 * a mined block's shards are, from a point inside the cell outward.
 * blast-broken-blocks-throw-their-own-break-shards
 */
public final class BlastShards {

    /** A shard's offset from the cell's center becomes its velocity, as vanilla's break effect does. */
    private static final double CELL_CENTER = 0.5;

    private BlastShards() {
    }

    /**
     * Throws the shards of a Blast's broken cells on the client thread.
     *
     * @param payload the broken cells and their blocks
     * @param context the network context
     */
    public static void onPayload(BlastShardsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                throwShards(level, payload.cells());
            }
        });
    }

    private static void throwShards(ClientLevel level, List<BlastShardsPayload.Shattered> cells) {
        ShardBudget budget = ShardBudget.forCells(cells.size());
        for (int index = 0; index < cells.size(); index++) {
            if (budget.throwsAt(index)) {
                BlastShardsPayload.Shattered cell = cells.get(index);
                throwCell(level, cell.pos(), cell.state(), budget.shardsPerCell());
            }
        }
    }

    private static void throwCell(ClientLevel level, BlockPos pos, BlockState state, int shards) {
        RandomSource random = level.getRandom();
        for (int shard = 0; shard < shards; shard++) {
            double relX = random.nextDouble();
            double relY = random.nextDouble();
            double relZ = random.nextDouble();
            Minecraft.getInstance().particleEngine.add(new TerrainParticle(level,
                    pos.getX() + relX, pos.getY() + relY, pos.getZ() + relZ,
                    relX - CELL_CENTER, relY - CELL_CENTER, relZ - CELL_CENTER, state, pos)
                    .updateSprite(state, pos));
        }
    }
}
