package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.blockmap.BlockMap;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nether decay's block step, run each held tick of the stream: the gnat
 * swarm paints every block it reaches, and every block the hold has painted
 * builds exposure toward its degraded block in the map the JSON names while
 * the hold lasts, whether or not the aim is still on it, stepping once it
 * has built the JSON's ticks. A painted block past nine tenths of its step is left to
 * finish alone, even after the hold ends; a position the hold has stepped
 * once it passes over, so stone becomes cobblestone and goes no further in
 * one activation (decision decay-gnats-degrade-each-block-once).
 *
 * @param map   the id of the block map the blocks step along
 * @param ticks the ticks of swarm a block takes to step
 */
public record DegradeStep(Identifier map, int ticks) implements Step {

    private static final String NAME = "degrade";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_TICKS = "ticks";
    /**
     * The tint Decay's mingled block wears, nether goo's #C03434, so the
     * block's own grays multiply down to a dark maroon.
     */
    public static final int NETHER_MAROON = 0xC03434;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DegradeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_MAP).forGetter(DegradeStep::map),
            Codec.INT.fieldOf(FIELD_TICKS).forGetter(DegradeStep::ticks)
    ).apply(inst, DegradeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DegradeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DegradeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        Optional<BlockMap> steps = BlockMaps.get(map);
        host.channelAim().ifPresent(aim -> steps.ifPresent(found -> {
            HoldMarks marks = host.holdMarks();
            if (aim.reaching()) {
                PaintedHold.paintReach(host, aim, found::next, marks);
            }
            // the operator's ruling on Decay: its overlay wears the nether family's dark maroon
            PaintedHold.buildPainted(host, found::next, marks, 1f / ticks, OptionalInt.of(NETHER_MAROON));
        }));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
