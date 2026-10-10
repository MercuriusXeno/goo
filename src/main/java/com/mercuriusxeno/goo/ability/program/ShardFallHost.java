package com.mercuriusxeno.goo.ability.program;

import java.util.OptionalInt;

/**
 * A host a glass shard falls through: a tap's spigot, the shard dropping
 * down the column under it onto the first living mob there, or onto the
 * landing where none stands (decision shards-drip-falls-as-a-glass-shard).
 */
public interface ShardFallHost extends EntityScanHost {

    /**
     * Drops one glass shard down the column under the tap, shown to the
     * players watching it fall.
     *
     * @return the id of the mob the shard strikes, or empty where it strikes the landing
     */
    OptionalInt fallShard();
}
