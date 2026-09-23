package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.BlockEffect;
import com.mercuriusxeno.goo.ability.LayerAudio;
import com.mercuriusxeno.goo.ability.LayerVisuals;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.BlobStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

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

    private static final String ERR_UNKNOWN_BLOCK = "No block is registered as ";

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
        return EntityScan.anyEntityWithin(level, Vec3.atCenterOf(pos), shape, radius, filters, null);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<StepHost> body) {
        EntityScan.forEachLivingWithin(level, Vec3.atCenterOf(pos), shape, radius, filters, null,
                living -> body.accept(new EntityHost(level, living, null)));
    }

    @Override
    public void forEntity(int entityId, Consumer<StepHost> body) {
        if (level.getEntity(entityId) instanceof LivingEntity living && living.isAlive()) {
            body.accept(new EntityHost(level, living, null));
        }
    }

    @Override
    public int targetId() {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public Vec3 targetCenter() {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public FieldEffectState fieldEffect() {
        return be.getFieldEffect();
    }

    @Override
    public PhasedState phased() {
        return be.getPhased();
    }

    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        EntityPull.pullWithin(level, Vec3.atCenterOf(pos), radius, speed, null);
    }

    @Override
    public void consumeValuedBlocks(int radius) {
        be.addConsumedGoo(ValuedBlocks.consumeSphere(level, pos, radius));
    }

    @Override
    public void dropConsumedGoo() {
        BlobStacks.dropAll(be.takeConsumedGoo(), level, pos);
    }

    @Override
    public void damageTarget(float amount, DamageKind source, boolean knockback) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void setTargetHurtCooldown(int ticks) {
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

    @Override
    public void cloneTarget(float chancePercent) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void dropItemAtTarget(Identifier item, int count) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void discardTarget() {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void addTargetCounter(Identifier id, double amount) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void igniteTarget(int seconds) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    @Override
    public void spawnParticles(FxAnchor at, ParticleBurst burst) {
        if (at == FxAnchor.TARGET) {
            throw HostCapability.TARGET.refusedBy(kind());
        }
        Vec3 center = Vec3.atCenterOf(pos);
        Direction.Axis along = be.getPlacedFace().getAxis();
        SimpleParticles.resolve(burst.particle()).ifPresent(particle -> level.sendParticles(particle,
                center.x(), center.y() + burst.lift(), center.z(), burst.count(),
                spreadOn(Direction.Axis.X, along, burst), spreadOn(Direction.Axis.Y, along, burst),
                spreadOn(Direction.Axis.Z, along, burst), burst.speed()));
    }

    @Override
    public void playSound(FxAnchor at, SoundCue cue) {
        if (at == FxAnchor.TARGET) {
            throw HostCapability.TARGET.refusedBy(kind());
        }
        SoundPlays.play(level, Vec3.atCenterOf(pos), cue);
    }

    @Override
    public void teleportTarget(TeleportMode mode, double range) {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    /**
     * Picks the burst's spread for one axis: along where the axis is the
     * placed face's, across otherwise.
     *
     * @param axis  the axis to spread on
     * @param along the placed face's axis
     * @param burst the burst
     * @return the spread on the axis
     */
    private static double spreadOn(Direction.Axis axis, Direction.Axis along, ParticleBurst burst) {
        return axis == along ? burst.spreadAlong() : burst.spreadAcross();
    }

    @Override
    public void placeBlock(Identifier block, Map<String, String> state) {
        Block found = BuiltInRegistries.BLOCK.getOptional(block)
                .orElseThrow(() -> new IllegalArgumentException(ERR_UNKNOWN_BLOCK + block));
        List<Property.Value<?>> values = StatePropertyWriter.resolve(found.getStateDefinition(), state, block);
        level.setBlock(pos, StatePropertyWriter.write(found.defaultBlockState(), values), Block.UPDATE_ALL);
    }

    @Override
    public boolean applyBlockEffect(BlockEffect effect, BlockPos cell) {
        return effect.apply(level, cell);
    }

    @Override
    public void previewLayer(LayerVisuals visuals, int layer) {
        visuals.preview(level, pos, be.getPlacedFace(), layer, be.getStackCount());
    }

    @Override
    public void strikeLayerFx(LayerVisuals visuals, LayerAudio audio, int layer, int destroyed) {
        visuals.onLayerStruck(level, pos, be.getPlacedFace(), layer, destroyed);
        audio.onLayerStruck(level, pos, be.getPlacedFace(), layer, destroyed, be.getStackCount());
    }

    @Override
    public void reportMinedLayers(int layers) {
        be.setMinedLayers(layers);
    }
}
