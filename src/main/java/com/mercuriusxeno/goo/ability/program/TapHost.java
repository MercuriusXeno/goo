package com.mercuriusxeno.goo.ability.program;

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
 * The {@link StepHost} over the block a tap's drip lands on: world actions
 * anchor at the struck face's center, and a placed block goes into the
 * block beyond that face. A tap has no will and no target, and a drip lands
 * in one tick with nothing ticking it afterwards, so this host provides
 * neither {@link HostCapability#TARGET}, {@link HostCapability#STACKS} nor
 * {@link HostCapability#TICKING}; the target and layer-walk methods refuse
 * through the {@link StepHost} defaults (decision tap-ability-tagged-program).
 *
 * @param level   the server level
 * @param landing the block the drip landed on
 * @param face    the landing block's face the drip struck
 */
public record TapHost(ServerLevel level, BlockPos landing, Direction face) implements StepHost {

    private static final String ERR_UNKNOWN_BLOCK = "Place step names block which no registry holds: ";
    private static final double HALF = 0.5;

    /**
     * The center of a block's face, where a tap host anchors its world actions.
     *
     * @param landing the block
     * @param face    the face
     * @return the face center
     */
    public static Vec3 faceCenter(BlockPos landing, Direction face) {
        return Vec3.atCenterOf(landing).add(face.getStepX() * HALF, face.getStepY() * HALF, face.getStepZ() * HALF);
    }

    private Vec3 anchor() {
        return faceCenter(landing, face);
    }

    @Override
    public HostKind kind() {
        return HostKind.TAP;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return landing;
    }

    @Override
    public Direction placedFace() {
        throw HostCapability.PLACED_FACE.refusedBy(kind());
    }

    @Override
    public int stackCount() {
        throw HostCapability.STACKS.refusedBy(kind());
    }

    @Override
    public void decrementStack() {
        throw HostCapability.STACKS.refusedBy(kind());
    }

    @Override
    public void explode(float power, ExplosionMode mode) {
        Vec3 at = anchor();
        Level.ExplosionInteraction interaction = mode == ExplosionMode.TNT
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        level.explode(null, at.x(), at.y(), at.z(), power, interaction);
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level, anchor(), shape, radius, filters, null);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<StepHost> body) {
        BlockAnchoredActions.forEachEntityWithin(level, anchor(), shape, radius, filters, body);
    }

    @Override
    public void forEntity(int entityId, Consumer<StepHost> body) {
        BlockAnchoredActions.forEntity(level, entityId, body);
    }

    @Override
    public FieldEffectState fieldEffect() {
        throw HostCapability.FIELD_EFFECT.refusedBy(kind());
    }

    @Override
    public PhasedState phased() {
        throw HostCapability.PHASED.refusedBy(kind());
    }

    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        EntityPull.pullWithin(level, anchor(), radius, speed, null);
    }

    @Override
    public void consumeValuedBlocks(int radius) {
        throw HostCapability.CONSUMED_GOO.refusedBy(kind());
    }

    @Override
    public void dropConsumedGoo() {
        throw HostCapability.CONSUMED_GOO.refusedBy(kind());
    }

    @Override
    public void spawnParticles(FxAnchor at, ParticleBurst burst) {
        if (at == FxAnchor.TARGET) {
            throw HostCapability.TARGET.refusedBy(kind());
        }
        BlockAnchoredActions.sendBurst(level, anchor(), face.getAxis(), burst);
    }

    @Override
    public void playSound(FxAnchor at, SoundCue cue) {
        if (at == FxAnchor.TARGET) {
            throw HostCapability.TARGET.refusedBy(kind());
        }
        SoundPlays.play(level, anchor(), cue);
    }

    /**
     * Writes the block into the cell beyond the struck face when that cell
     * can be replaced, so a drip never overwrites the tap it fell from or
     * any other standing block.
     */
    @Override
    public void placeBlock(Identifier block, Map<String, String> state) {
        BlockPos cell = landing.relative(face);
        if (!level.getBlockState(cell).canBeReplaced()) {
            return;
        }
        Block found = BuiltInRegistries.BLOCK.getOptional(block)
                .orElseThrow(() -> new IllegalArgumentException(ERR_UNKNOWN_BLOCK + block));
        List<Property.Value<?>> values = StatePropertyWriter.resolve(found.getStateDefinition(), state, block);
        level.setBlock(cell, StatePropertyWriter.write(found.defaultBlockState(), values), Block.UPDATE_ALL);
    }

}
