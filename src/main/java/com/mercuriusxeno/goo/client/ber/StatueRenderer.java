package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.statue.StatueBlockEntity;
import com.mercuriusxeno.goo.client.ability.PetrifyStoneLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws a statue: the mob it was, standing in the block as it faced, every
 * inch of it in Petrify's stone, its limbs and idle motion still
 * (decision petrify-stone-encasement-and-calcify-map). One client-side mob of
 * each type and age stands in for every statue of it, posed per statue.
 */
public class StatueRenderer implements BlockEntityRenderer<StatueBlockEntity, StatueRenderState> {

    private static final double BLOCK_CENTER = 0.5;

    /** The stand-in mob of each type, baby and adult apart. */
    private final Map<StandIn, LivingEntity> standIns = new HashMap<>();

    /**
     * A stand-in's identity: the mob's type and age.
     *
     * @param type the mob's type
     * @param baby whether it is a baby
     */
    private record StandIn(EntityType<?> type, boolean baby) {
    }

    /**
     * Creates the statue renderer.
     *
     * @param context the renderer context
     */
    public StatueRenderer(BlockEntityRendererProvider.Context context) {
        // the stand-ins are built from the client level on first draw
    }

    @Override
    public StatueRenderState createRenderState() {
        return new StatueRenderState();
    }

    @Override
    public void extractRenderState(StatueBlockEntity statue, StatueRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(statue, state, breakProgress);
        LivingEntity mob = standIn(statue);
        if (mob == null) {
            state.mob = null;
            return;
        }
        mob.setYRot(statue.yaw());
        mob.setYBodyRot(statue.yaw());
        mob.setYHeadRot(statue.yaw());
        mob.yRotO = statue.yaw();
        mob.yBodyRotO = statue.yaw();
        mob.yHeadRotO = statue.yaw();
        EntityRenderState posed = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(mob, 0f);
        posed.setRenderData(PetrifyStoneLayer.PETRIFIED, PetrifyStoneLayer.WHOLE);
        state.mob = posed;
    }

    /**
     * The stand-in mob for a statue, built on first use.
     *
     * @param statue the statue
     * @return the stand-in, or null where the statue holds no type the client can build
     */
    private @Nullable LivingEntity standIn(StatueBlockEntity statue) {
        Identifier typeId = statue.entityType();
        EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        Level level = statue.getLevel();
        if (type == null || level == null) {
            return null;
        }
        return standIns.computeIfAbsent(new StandIn(type, statue.baby()), key -> build(key, level));
    }

    private static @Nullable LivingEntity build(StandIn key, Level level) {
        if (!(key.type().create(level, EntitySpawnReason.LOAD) instanceof LivingEntity living)) {
            return null;
        }
        if (living instanceof Mob mob) {
            mob.setBaby(key.baby());
        }
        return living;
    }

    @Override
    public void submit(StatueRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        if (state.mob != null) {
            Minecraft.getInstance().getEntityRenderDispatcher().submit(state.mob, cameraState, BLOCK_CENTER, 0,
                    BLOCK_CENTER, poseStack, nodeCollector);
        }
    }
}
