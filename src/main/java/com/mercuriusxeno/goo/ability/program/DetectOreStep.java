package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Glitter's ore sense: while the channel is held its front grows from where
 * the hold began, a little further each held tick up to its reach, and
 * stops where it stands once the hold ends. Each tick it walks only the
 * band of blocks the front crossed, finds the gem ore veins first reached
 * there, and sends the caster the front's radius with those veins, for the
 * client to draw the front as its sparkle shell:
 * {@code detect_ore tag=goo:gem_ores radius=64 growth=1 life=100}.
 * decision glitter-sphere-icons-gem-ore-groups
 *
 * @param tag    the block tag the sense finds
 * @param radius the radius in blocks the front reaches at most
 * @param growth blocks the front grows each held tick
 * @param life   ticks each vein's ore shows through walls once revealed
 */
public record DetectOreStep(TagKey<Block> tag, int radius, double growth, int life) implements Step {

    private static final String NAME = "detect_ore";

    /** Codec for the step's params. */
    public static final MapCodec<DetectOreStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf("tag").forGetter(DetectOreStep::tag),
            Codec.INT.fieldOf("radius").forGetter(DetectOreStep::radius),
            Codec.DOUBLE.fieldOf("growth").forGetter(DetectOreStep::growth),
            Codec.INT.fieldOf("life").forGetter(DetectOreStep::life)
    ).apply(inst, DetectOreStep::new));

    /** The registered type. */
    public static final StepType<DetectOreStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DetectOreStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost channel = context.hostAs(ChannelHost.class);
        int held = channel.channelAim().map(ChannelAim::held).orElse(ChannelAim.FIRST_TICK);
        if (held < ChannelAim.FIRST_TICK) {
            return true;
        }
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        Vec3 origin = channel.holdMarks().anchorAt(caster.position());
        EntityVisuals.sendToSelf(caster, revealAt(caster.level(), origin, held));
        return true;
    }

    /**
     * One held tick of the sense: the front's radius after the tick and the
     * veins it first reached on it.
     *
     * @param level  the level the sense walks
     * @param origin where the hold began
     * @param held   the hold's tick count, 1 on its first tick
     * @return the reveal to send the caster
     */
    public OreRevealPayload revealAt(Level level, Vec3 origin, int held) {
        double inner = frontAt(held - 1);
        double outer = frontAt(held);
        List<OreVeins.Vein> veins = OreVeins.firstReachedIn(BlockPos.containing(origin), inner, outer, radius,
                pos -> {
                    BlockState state = level.getBlockState(pos);
                    return state.is(tag) ? BuiltInRegistries.BLOCK.getKey(state.getBlock()) : null;
                });
        return new OreRevealPayload(origin, growth, outer, radius, veins, life);
    }

    /**
     * The front's radius after a number of held ticks.
     *
     * @param held the hold's tick count, 0 before it begins
     * @return the radius, held at the reach once reached
     */
    double frontAt(int held) {
        return Math.clamp(held * growth, 0, radius);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL, HostCapability.TARGET);
    }
}
