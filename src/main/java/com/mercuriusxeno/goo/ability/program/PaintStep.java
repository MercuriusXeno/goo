package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.ornate.Painting;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ornate's Paint step, run in a held stream's block pass: each tick it
 * paints every dyeable block whose center stands in the cone, as Mycosis
 * tends the fungi in its cone, and every sheep and tamed wolf or cat the
 * cone reaches, the color of the dye in the caster's off hand. The dye is
 * not used up; each thing painted pays the hold's price, and a caster
 * holding no dye paints nothing and pays nothing.
 * paint-recolors-dyeable-things-to-the-off-hand-dye
 */
public record PaintStep() implements TakesNoEntity {

    private static final String NAME = "paint";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<PaintStep> CODEC = MapCodec.unit(PaintStep::new);

    /**
     * The registered type.
     */
    public static final StepType<PaintStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PaintStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        if (!(host.breaker() instanceof LivingEntity caster)) {
            return true;
        }
        DyeColor color = caster.getOffhandItem().get(DataComponents.DYE);
        if (color == null) {
            return true;
        }
        host.channelAim().ifPresent(aim -> paintTheCone(host, caster, aim, color));
        return true;
    }

    private static void paintTheCone(ChannelHost host, LivingEntity caster, ChannelAim aim, DyeColor color) {
        ServerLevel level = host.level();
        Vec3 eye = host.eye();
        PlacementPrice price = host.placementPrice();
        for (BlockPos pos : CalcifyStep.blocksInCone(eye, aim.aimPoint(), aim.shape())) {
            Painting.paintBlock(level, pos, color, price);
        }
        Vec3 reach = aim.aimPoint().subtract(eye);
        double range = reach.length();
        level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
                mob -> mob != caster && mob.isAlive()
                        && StreamCone.containsTheMiddleOf(eye, reach, range, aim.shape(), mob.getBoundingBox()))
                .forEach(mob -> Painting.paintMob(mob, color, price));
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
