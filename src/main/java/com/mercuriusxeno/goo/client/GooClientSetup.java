package com.mercuriusxeno.goo.client;

import com.google.common.reflect.TypeToken;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.client.ability.Afterimages;
import com.mercuriusxeno.goo.client.ability.AilmentOverlayLayer;
import com.mercuriusxeno.goo.client.ability.BlockTransforms;
import com.mercuriusxeno.goo.client.ability.ChainBurnouts;
import com.mercuriusxeno.goo.client.ability.GhostTrails;
import com.mercuriusxeno.goo.client.ability.MobAilments;
import com.mercuriusxeno.goo.client.ability.MobCoatLayer;
import com.mercuriusxeno.goo.client.ability.MobCoats;
import com.mercuriusxeno.goo.client.ability.MobShells;
import com.mercuriusxeno.goo.client.ability.PetrifyStoneLayer;
import com.mercuriusxeno.goo.client.ability.TransformationRenderer;
import com.mercuriusxeno.goo.client.ability.Transformations;
import com.mercuriusxeno.goo.client.ability.ViewportRipples;
import com.mercuriusxeno.goo.client.ability.ZapBolts;
import com.mercuriusxeno.goo.client.ber.*;
import com.mercuriusxeno.goo.client.model.*;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.client.particle.*;
import com.mercuriusxeno.goo.client.radial.CutItemRenderer;
import com.mercuriusxeno.goo.client.throwing.GooFlightManager;
import com.mercuriusxeno.goo.client.throwing.GooSizeProperty;
import com.mercuriusxeno.goo.client.throwing.GooVolumeDecorator;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import com.mercuriusxeno.goo.item.gasket.TunerAwaitState;
import com.mercuriusxeno.goo.registry.*;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeOrderSource;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Client-side setup: entity renderers and network event handling.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooClientSetup {
    /**
     * Property name for goo size range select.
     */
    private static final String PROP_GOO_SIZE = "goo_size";
    /**
     * Special renderer key for canister goo.
     */
    private static final String RENDERER_CANISTER = "canister_goo";
    private static final String RENDERER_CHRYSM = "chrysm_crystal";
    /**
     * Special renderer key for vat goo.
     */
    private static final String RENDERER_VAT = "vat_goo";
    /**
     * Special renderer key for glove goo.
     */
    private static final String RENDERER_GLOVE = "glove_goo";
    /**
     * Property name for the goo texture a stack's type names.
     */
    private static final String PROP_GOO_TEXTURE = "goo_texture";
    /**
     * SuppressWarnings annotation value for unchecked casts.
     */
    private static final String SUPPRESS_UNCHECKED = "unchecked";

    static {
        ISidedProxy.INSTANCE[0] = new ClientProxy();
        AbilityBlockEntity.installClientSteps(GooClientSetup::syncedSteps);
        GooTypes.readConnectionOrderFrom(GooClientSetup::connectionTypeOrder);
    }

    /**
     * Answers a marker's steps from the abilities the server synced
     * (decision diagnose-then-fix-marker-server-gate).
     *
     * @param abilityId the ability resource id string
     * @return the synced ability's steps, or null when none synced under that id
     */
    private static @Nullable List<Step> syncedSteps(String abilityId) {
        ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability != null ? ability.behaviors() : null;
    }

    private GooClientSetup() {
    }

    /**
     * Registers entity and block entity renderers.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        registerMachineRenderers(event);
        registerEffectRenderers(event);
    }

    /**
     * Registers block entity renderers for machine blocks.
     *
     * @param event the renderer registration event
     */
    private static void registerMachineRenderers(EntityRenderersEvent.RegisterRenderers event) {
        registerFluidMachineRenderers(event);
        registerLogisticMachineRenderers(event);
    }

    /**
     * Registers renderers for crucible, hub, and canister block entities.
     *
     * @param event the renderer registration event
     */
    private static void registerFluidMachineRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GooBlockEntities.CRUCIBLE.get(),
                CrucibleBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.HUB.get(),
                HubBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.CANISTER.get(),
                CanisterBlockEntityRenderer::new);
    }

    /**
     * Registers renderers for vat, plexer, and tap block entities.
     *
     * @param event the renderer registration event
     */
    private static void registerLogisticMachineRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GooBlockEntities.VAT.get(),
                VatBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.PLEXER.get(),
                PlexerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.REACTOR.get(),
                ReactorBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.TAP.get(),
                TapBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.CRYSTALLIZER.get(),
                CrystallizerBlockEntityRenderer::new);
    }

    /**
     * Registers block entity renderers for world effect blocks.
     *
     * @param event the renderer registration event
     */
    private static void registerEffectRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GooBlockEntities.ABILITY_BLOCK.get(),
                AbilityBlockRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.PRISM.get(), PrismRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.STATUE.get(), StatueRenderer::new);
    }

    /**
     * Registers the goo type icon decorator on the goo item.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        GooVolumeDecorator decorator = new GooVolumeDecorator();
        event.register(GooItems.GOO.get(), decorator);
    }

    /**
     * Registers the item tint source the generic goo item models name, which
     * colors the grey base by the type the stack carries (decision
     * generic-goo-items).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(Goo.MODID, GooTypeItemTint.PATH), GooTypeItemTint.MAP_CODEC);
    }

    /**
     * Registers range_dispatch properties for goo size and fuel depletion.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerRangeSelectProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, PROP_GOO_SIZE),
                GooSizeProperty.MAP_CODEC
        );
    }

    /**
     * Registers the select property keyed by the GOO_TYPE component that
     * answers the goo texture the stack's type names (decision
     * type-named-textures).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerSelectProperties(RegisterSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(Goo.MODID, PROP_GOO_TEXTURE), GooTextureProperty.TYPE);
    }

    /**
     * Registers the goo bubble particle provider with its sprite set.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GooParticles.GOO_BUBBLE.get(), GooBubbleParticle.Provider::new);
        event.registerSpriteSet(GooParticles.GOO_SPARK.get(), GooSparkParticle.Provider::new);
        event.registerSpriteSet(GooParticles.TRAIL_DRIP.get(), TrailDripParticle.Provider::new);
        event.registerSpriteSet(GooParticles.TRAIL_DRIP_LAND.get(), TrailDripParticle.LandProvider::new);
        event.registerSpriteSet(GooParticles.SPLAT_DRIP.get(), TrailDripParticle.SplatProvider::new);
        event.registerSpriteSet(GooParticles.TAP_DRIP.get(), TapDripParticle.Provider::new);
        event.registerSpriteSet(GooParticles.TAP_DRIP_LAND.get(), TapDripParticle.LandProvider::new);
        event.registerSpriteSet(GooParticles.GOO_FOG.get(), GooFogParticle.Provider::new);
        event.registerSpriteSet(GooParticles.SPORE.get(), SporeParticle.Provider::new);
        event.registerSpriteSet(GooParticles.RESTORE_MOTE.get(), RestoreMoteParticle.Provider::new);
        event.registerSpriteSet(GooParticles.VITAL_MOTE.get(), VitalMoteParticle.Provider::new);
        event.registerSpriteSet(GooParticles.VITAL_FOG.get(), VitalFogParticle.Provider::new);
        event.registerSpriteSet(GooParticles.VITAL_STAR.get(), VitalStarParticle.Provider::new);
        event.registerSpecial(GooParticles.SILENT_BLAST.get(),
                (options, level, x, y, z, dx, dy, dz, random) -> null);
    }

    /**
     * Registers a render state modifier that injects goo-colored outlineColor
     * onto entities targeted by the glove, producing the spectral glow outline.
     *
     * @param event the event instance
     */
    @SuppressWarnings(SUPPRESS_UNCHECKED)
    @SubscribeEvent
    public static void registerRenderStateModifiers(
            RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
                },
                AimTracker::modifyEntityRenderState);
        event.registerEntityModifier(
                new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
                },
                MobCoatLayer::stampCoat);
        event.registerEntityModifier(
                new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
                },
                AilmentOverlayLayer::stampAilments);
        event.registerEntityModifier(
                new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
                },
                PetrifyStoneLayer::stampPetrify);
        event.registerEntityModifier(
                new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
                },
                TransformationRenderer::stampTransformation);
    }

    /**
     * Adds the goo coat layer and the ailment overlay layer to every living
     * entity renderer, both player skins and mannequins among them
     * (decisions shader-coat-on-every-mob-landing, ailment-overlay-shader-per-ailment).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void addMobCoatLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?, ?> renderer = event.getRenderer(type);
            if (renderer != null) {
                MobCoatLayer.addTo(renderer, MobShells.of(type, event.getEntityModels()));
                AilmentOverlayLayer.addTo(renderer);
                PetrifyStoneLayer.addTo(renderer, MobShells.of(type, event.getEntityModels()));
            }
        }
        for (PlayerModelType skin : event.getSkins()) {
            MobCoatLayer.addTo(event.getPlayerRenderer(skin), MobShells.NONE);
            MobCoatLayer.addTo(event.getMannequinRenderer(skin), MobShells.NONE);
            AilmentOverlayLayer.addTo(event.getPlayerRenderer(skin));
            AilmentOverlayLayer.addTo(event.getMannequinRenderer(skin));
            PetrifyStoneLayer.addTo(event.getPlayerRenderer(skin), MobShells.NONE);
        }
    }

    /**
     * Registers custom render pipelines (additive glow lines, etc.).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        GooRenderTypes.registerPipelines(event);
    }

    /**
     * Registers the picture-in-picture renderers the GUI draws custom pictures with.
     * decision icons-slide-in-from-behind-the-tip
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        CutItemRenderer.register(event);
    }

    /**
     * Registers standalone baked models for canister and vat item rendering.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        CanisterBodyModels.register(event);
        VatBodyModels.register(event);
        GloveBodyModels.register(event);
    }

    /**
     * Registers special model renderer types for canister and vat item rendering.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, RENDERER_CANISTER),
                CanisterSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, RENDERER_VAT),
                VatSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, RENDERER_GLOVE),
                GloveSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, RENDERER_CHRYSM),
                ChrysmSpecialRenderer.Unbaked.MAP_CODEC
        );
    }

    /**
     * Registers the FluidModel of the one goo fluid: a grey base texture
     * tinted by the type the stack carries (decision generic-goo-fluids),
     * and a custom renderer that draws a placed fluid on the sprites its
     * stamped type names (decision type-named-textures).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        Material texture = new Material(GooTypeSprites.GREY_FLUID, true);
        GooFluidTintSource tint = new GooFluidTintSource();
        FluidModel.Unbaked model = new FluidModel.Unbaked(texture, texture, null, tint, new GooTypeFluidRenderer(tint));
        event.register(model, GooFluids.SOURCE, GooFluids.FLOWING);
    }

    /**
     * Registers the client-side rendering extensions of the goo fluid type
     * (fog/overlay only in 26.1).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
        }, GooFluidTypes.GOO.get());
    }

    /**
     * Clears tuner await state and client flight state on disconnect; the goo
     * values leave with the connection that held them.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        TunerAwaitState.clear();
        GooFlightManager.clear();
        ZapBolts.clear();
        ChainBurnouts.CLIENT.clear();
        MobCoats.CLIENT.clear();
        MobAilments.CLIENT.clear();
        BlockTransforms.CLIENT.clear();
        Afterimages.CLIENT.clear();
        Transformations.CLIENT.clear();
        GhostTrails.CLIENT.clear();
        ViewportRipples.CLIENT.clear();
        ThrowFreezeState.clear();
    }

    /**
     * Clears tuner await state on login (dimension change).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        TunerAwaitState.clear();
    }

    /**
     * Answers the goo types the current connection holds.
     *
     * @return the types, or null while no connection stands
     */
    private static @Nullable List<ResourceKey<GooTypeDefinition>> connectionTypeOrder() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection instanceof GooTypeOrderSource source ? source.gooTypeOrder() : null;
    }

}
