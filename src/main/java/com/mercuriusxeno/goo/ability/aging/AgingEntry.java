package com.mercuriusxeno.goo.ability.aging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * One row of the aging table, a file under {@code data/<ns>/goo_aging/}: the
 * block a Yore blob ages, the block it becomes, an item it drops as it
 * does, the in-game ticks the aging takes and the yore the throw costs.
 * old-blob-ages-valuables-slowly
 *
 * @param source the block that ages
 * @param result the block it becomes
 * @param drop   the item dropped where it stood, if any
 * @param ticks  the in-game ticks the aging takes
 * @param price  the yore mB a throw on the source costs
 */
public record AgingEntry(Identifier source, Identifier result, Optional<Identifier> drop, int ticks, int price) {

    /** Codec for one file of the table. */
    public static final Codec<AgingEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("source").forGetter(AgingEntry::source),
            Identifier.CODEC.fieldOf("result").forGetter(AgingEntry::result),
            Identifier.CODEC.optionalFieldOf("drop").forGetter(AgingEntry::drop),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("ticks").forGetter(AgingEntry::ticks),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("price").forGetter(AgingEntry::price)
    ).apply(inst, AgingEntry::new));
}
