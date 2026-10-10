package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hex.BookFusion;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Fuses the first two identical enchanted books the host's player carries
 * into one a level higher, as an anvil would, and finishes. A player
 * carrying no such pair is refused before the cost drains, hearing the
 * refusal sound the JSON names. Fuse is
 * {@code fuse_books refused={id=minecraft:block.fire.extinguish}}.
 * fuse-two-books-for-hex-goo
 *
 * @param refused the sound a refused press plays, or empty for silence
 */
public record FuseBooksStep(Optional<SoundCue> refused) implements Step {

    private static final String NAME = "fuse_books";
    private static final String FIELD_REFUSED = "refused";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FuseBooksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            SoundCue.CODEC.optionalFieldOf(FIELD_REFUSED).forGetter(FuseBooksStep::refused)
    ).apply(inst, FuseBooksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FuseBooksStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FuseBooksStep> type() {
        return TYPE;
    }

    @Override
    public boolean admits(StepContext context) {
        return context.hostAs(TargetHost.class).target() instanceof Player player
                && BookFusion.holdsPair(player.getInventory());
    }

    @Override
    public Optional<SoundCue> refusal() {
        return refused;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof Player player) {
            BookFusion.fuseIn(player.getInventory());
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
