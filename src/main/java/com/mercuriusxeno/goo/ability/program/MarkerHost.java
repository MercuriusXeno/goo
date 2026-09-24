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
        BlockAnchoredActions.forEachEntityWithin(level, Vec3.atCenterOf(pos), shape, radius, filters, body);
    }

    @Override
    public void forEntity(int entityId, Consumer<StepHost> body) {
        BlockAnchoredActions.forEntity(level, entityId, body);
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
    public void spawnParticles(FxAnchor at, ParticleBurst burst) {
        if (at == FxAnchor.TARGET) {
            throw HostCapability.TARGET.refusedBy(kind());
        }
        BlockAnchoredActions.sendBurst(level, Vec3.atCenterOf(pos), be.getPlacedFace().getAxis(), burst);
    }

    @Override
    public void playSound(FxAnchor at, SoundCue cue) {
        if (at == FxAnchor.TARGET) {
            throw HostCapability.TARGET.refusedBy(kind());
        }
        SoundPlays.play(level, Vec3.atCenterOf(pos), cue);
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
