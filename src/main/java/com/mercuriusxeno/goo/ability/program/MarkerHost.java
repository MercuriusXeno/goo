package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The {@link StepHost} over a chain marker block entity: reads stack
 * count, placed face and blob shape from the block entity, and acts on
 * the server level at the marker position. Built fresh each tick from
 * what the {@link com.mercuriusxeno.goo.ability.ChainBehavior} callbacks
 * hand over, so it holds no state of its own.
 *
 * @param level the server level
 * @param pos   the marker position
 * @param be    the marker block entity
 */
public record MarkerHost(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) implements StepHost {

    @Override
    public HostKind kind() {
        return HostKind.MARKER;
    }

    @Override
    public OptionalDouble read(String name) {
        return switch (name) {
            case HostVariables.STACKS -> OptionalDouble.of(be.getStackCount());
            case HostVariables.MAX_STACKS -> OptionalDouble.of(be.getMaxStacks());
            case HostVariables.FLAT -> OptionalDouble.of(be.isFlatBlob() ? 1 : 0);
            default -> OptionalDouble.empty();
        };
    }

    @Override
    public BlockPos position() {
        return pos;
    }

    @Override
    public Direction placedFace() {
        return be.getPlacedFace();
    }

    @Override
    public int stackCount() {
        return be.getStackCount();
    }

    @Override
    public void decrementStack() {
        be.decrementStack();
    }

    @Override
    public void explode(float power, ExplosionMode mode) {
        Vec3 center = Vec3.atCenterOf(pos);
        Level.ExplosionInteraction interaction = mode == ExplosionMode.TNT
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        level.explode(null, center.x(), center.y(), center.z(), power, interaction);
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level, Vec3.atCenterOf(pos), shape, radius, filters);
    }

    @Override
    public void damageTarget(float amount, DamageKind source) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void applyPotion(Identifier effect, int duration, int amplifier, boolean visible) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public boolean targetPasses(Set<EntityFilter> filters) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void setTargetHealthFraction(float fraction) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void addTargetFreezeTicks(int ticks) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void setTargetAi(boolean enabled) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void setTargetInvulnerable(boolean enabled) {
        throw HostCapability.TARGET.refusedBy(kind());
    }
}
