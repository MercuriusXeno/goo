package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Glitter's ore sense: on the first tick of each ping of a held channel it
 * walks the sphere around the caster for blocks of a tag, groups touching
 * blocks of one ore into veins, and sends the caster each vein with the
 * tick the ping's front reaches it, for the client to draw the front as
 * its sparkle shell:
 * {@code detect_ore tag=goo:gem_ores radius=24 growth=1 every=32 life=100}.
 * decision glitter-sphere-icons-gem-ore-groups
 *
 * @param tag    the block tag the sense finds
 * @param radius the sphere's radius in blocks
 * @param growth blocks the front grows each tick
 * @param every  ticks between pings
 * @param life   ticks each vein's ore shows through walls once revealed
 */
public record DetectOreStep(TagKey<Block> tag, int radius, double growth, int every, int life) implements Step {

    private static final String NAME = "detect_ore";

    /** Codec for the step's params. */
    public static final MapCodec<DetectOreStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf("tag").forGetter(DetectOreStep::tag),
            Codec.INT.fieldOf("radius").forGetter(DetectOreStep::radius),
            Codec.DOUBLE.fieldOf("growth").forGetter(DetectOreStep::growth),
            Codec.INT.fieldOf("every").forGetter(DetectOreStep::every),
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
        int held = context.hostAs(ChannelHost.class).channelAim().map(ChannelAim::held)
                .orElse(ChannelAim.FIRST_TICK);
        if (!pingsOn(held)) {
            return true;
        }
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        EntityVisuals.sendToSelf(caster, revealAround(caster));
        return true;
    }

    /**
     * The veins one ping around the caster finds, each with the tick the
     * front reaches it: what the ping sends the caster's client.
     *
     * @param caster the channeling caster
     * @return the reveal
     */
    public OreRevealPayload revealAround(LivingEntity caster) {
        Vec3 origin = caster.position();
        List<OreVeins.Vein> veins = OreVeins.group(found(caster.level(), BlockPos.containing(origin)));
        List<Integer> reveal = veins.stream().map(vein -> revealTick(vein.centroid().distanceTo(origin))).toList();
        return new OreRevealPayload(origin, growth, radius, veins, reveal, life);
    }

    /**
     * Whether a tick of the hold starts a ping.
     *
     * @param heldTicks the hold's age, 1 on its first tick
     * @return true on the first tick and every {@code every} ticks after
     */
    boolean pingsOn(int heldTicks) {
        return Math.floorMod(heldTicks - 1, Math.max(1, every)) == 0;
    }

    /**
     * The tick after the ping began that the front reaches a distance.
     *
     * @param distance blocks from the ping's center
     * @return the tick, 0 at the center
     */
    int revealTick(double distance) {
        return (int) Math.ceil(distance / growth);
    }

    private Map<BlockPos, Identifier> found(Level level, BlockPos center) {
        Map<BlockPos, Identifier> found = new HashMap<>();
        AbilityMath.forEachInSphere(center, radius, pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.is(tag)) {
                found.put(pos.immutable(), BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            }
        });
        return found;
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
