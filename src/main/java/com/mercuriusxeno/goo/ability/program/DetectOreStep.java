package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Glitter's and Magnet's ore sense: a press sends a pulse at once and a
 * hold another every second, each front leaving where the caster
 * stood at the held sweeps' steady pace, fronts overlapping. Each tick it
 * sends the caster every live pulse to draw through the held-sweep visual,
 * and walks only the band of blocks a front crossed, once for each spot a
 * pulse left from, to send the ore veins first reached there to show
 * through walls:
 * {@code detect_ore tag=goo:gem_ores goo=crystal radius=64 life=100}.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision glitter-sphere-icons-gem-ore-groups
 *
 * @param tag    the block tag the sense finds
 * @param goo    the goo type whose colors tint the sweep
 * @param radius the radius in blocks each pulse reaches
 * @param life   ticks each vein's ore shows through walls once revealed
 */
public record DetectOreStep(TagKey<Block> tag, ResourceKey<GooTypeDefinition> goo, int radius, int life)
        implements HeldSweepStep, TakesNoEntity {

    private static final String NAME = "detect_ore";

    /** Codec for the step's params. */
    public static final MapCodec<DetectOreStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf("tag").forGetter(DetectOreStep::tag),
            GooTypes.ID_CODEC.fieldOf("goo").forGetter(DetectOreStep::goo),
            Codec.INT.fieldOf("radius").forGetter(DetectOreStep::radius),
            Codec.INT.fieldOf("life").forGetter(DetectOreStep::life)
    ).apply(inst, DetectOreStep::new));

    /** The registered type. */
    public static final StepType<DetectOreStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DetectOreStep> type() {
        return TYPE;
    }

    @Override
    public HeldSweepPace pace() {
        return new HeldSweepPace(radius);
    }

    @Override
    public boolean tick(StepContext context) {
        int held = context.hostAs(ChannelHost.class).channelAim().map(ChannelAim::held)
                .orElse(ChannelAim.FIRST_TICK);
        if (held < ChannelAim.FIRST_TICK) {
            return true;
        }
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        long now = caster.level().getGameTime();
        List<HeldSweepPulses.Pulse> pulses = HeldSweepPulses.SERVER.advance(caster.getUUID(), caster.position(),
                held, now, pace());
        Set<Vec3> walked = new HashSet<>();
        for (HeldSweepPulses.Pulse pulse : pulses) {
            EntityVisuals.sendToSelf(caster, sweepOf(pulse, now));
            // a later front from the spot an earlier one left walks blocks that front already walked
            if (walked.add(pulse.origin())) {
                OreRevealPayload reveal = revealAt(caster.level(), pulse.origin(), pulse.ageAt(now));
                if (!reveal.veins().isEmpty()) {
                    EntityVisuals.sendToSelf(caster, reveal);
                }
            }
        }
        return true;
    }

    /**
     * One tick of a pulse: the veins its front first reached on the tick.
     *
     * @param level     the level the sense walks
     * @param origin    where the pulse began
     * @param pulseTick the pulse's tick, 1 on its first
     * @return the reveal to send the caster
     */
    public OreRevealPayload revealAt(Level level, Vec3 origin, int pulseTick) {
        HeldSweepPace pace = pace();
        List<OreVeins.Vein> veins = OreVeins.firstReachedIn(BlockPos.containing(origin),
                pace.radiusAt(pulseTick - 1), pace.radiusAt(pulseTick), radius, pos -> {
                    BlockState state = level.getBlockState(pos);
                    return state.is(tag) ? BuiltInRegistries.BLOCK.getKey(state.getBlock()) : null;
                });
        return new OreRevealPayload(veins, life);
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
