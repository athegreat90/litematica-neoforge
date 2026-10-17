package fi.dy.masa.litematica.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.mixin.client.IMixinActiveProfiler;
import fi.dy.masa.litematica.render.LitematicaRenderer;

@Mixin(value = LevelRenderer.class, priority = 850)
public abstract class MixinLevelRenderer
{
	@Shadow @Final private SubmitNodeStorage submitNodeStorage;
	@Shadow private @Nullable GpuSampler chunkLayerSampler;
	@Shadow @Final private LevelTargetBundle targets;
	@Unique private ProfilerFiller profiler;

    @Unique
    private void litematica$prepareProfiler()
    {
        if (this.profiler == null)
        {
            this.profiler = Profiler.get();
        }
        if (this.profiler instanceof ActiveProfiler ps && !((IMixinActiveProfiler) ps).litematica_isStarted())
        {
            this.profiler.startTick();
        }
    }

    // 26.3: addMainPass(...) is called once in render(), after either prepareChunkRenders or prepareChunkRendersIndirect
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V",
                    shift = At.Shift.BEFORE))
    private void litematica_onPreRenderMain(GraphicsResourceAllocator resourceAllocator, boolean renderOutline,
                                            CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor,
                                            boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci,
                                            @Local(name = "profiler") ProfilerFiller profiler)
    {
        this.profiler = profiler;
//		if (IrisCompat.isShaderActive()) { return; }
        LitematicaRenderer.getInstance().capturePreMainValues(cameraState, terrainFog, profiler);
    }

	// 26.3 picks one of these two depending on GameRenderer#useImprovedTransparency()
	@Inject(method = { "prepareChunkRenders", "prepareChunkRendersIndirect" }, at = @At("TAIL"))
    private void litematica_onPrepareBlockLayersPost(Matrix4fc modelViewMatrix, boolean improvedTransparency, CallbackInfoReturnable<ChunkSectionsToRender> cir)
    {
	    // Why Iris?
//		if (IrisCompat.isShaderActive()) { return; }
	    this.litematica$prepareProfiler();
	    LitematicaRenderer.getInstance().piecewisePrepareBlockLayers(modelViewMatrix, this.profiler);
    }

	// 26.3 hands the RenderPass it is recording to executeSolid() / executeClassicTransparency(); draw into that pass,
	// right after vanilla's own renderGroup() call, like 26.2 did right after its renderGroup() calls.
	@Inject(method = "executeSolid",
	        at = @At(value = "INVOKE",
	                 target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V",
	                 ordinal = 0,
	                 shift = At.Shift.AFTER))
	private void litematica_renderMainSection_Opaque(ChunkSectionsToRender chunkSectionsToRender,
	                                                  FeatureRenderDispatcher.PreparedFrame featureFrame,
	                                                  RenderPass pass, CallbackInfo ci)
	{
		LitematicaRenderer.getInstance().piecewiseDrawBlockLayerGroup(ChunkSectionLayerGroup.OPAQUE, this.chunkLayerSampler, pass);
	}

	// Classic (non-OIT) transparency: same pass as above
	@Inject(method = "executeClassicTransparency",
	        at = @At(value = "INVOKE",
	                 target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V",
	                 ordinal = 0,
	                 shift = At.Shift.AFTER))
	private void litematica_renderMainSection_Translucent(ChunkSectionsToRender chunkSectionsToRender,
	                                                       FeatureRenderDispatcher.PreparedFrame featureFrame,
	                                                       RenderPass pass, CallbackInfo ci)
	{
		LitematicaRenderer.getInstance().piecewiseDrawBlockLayerGroup(ChunkSectionLayerGroup.TRANSLUCENT, this.chunkLayerSampler, pass);
	}

	// OIT transparency (new in 26.3): vanilla's main pass is already closed here, so draw the translucent
	// schematic layer with a pass of our own, as 26.2 effectively did.
	@Inject(method = "lambda$addMainPass$0",
	        at = @At(value = "INVOKE",
	                 target = "Lnet/minecraft/client/renderer/LevelRenderer;executeOit(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V",
	                 shift = At.Shift.AFTER))
	private void litematica_renderMainSection_TranslucentOit(GpuBufferSlice terrainFog, boolean useOit,
	                                                          ChunkSectionsToRender chunkSectionsToRender,
	                                                          FeatureRenderDispatcher.PreparedFrame featureFrame,
	                                                          boolean arg5, boolean arg6, CallbackInfo ci)
	{
		LitematicaRenderer.getInstance().piecewiseDrawBlockLayerGroup(ChunkSectionLayerGroup.TRANSLUCENT, this.chunkLayerSampler, null);
	}

	// Placeholder for the (commented out) Iris main pass handling; 26.3 has no weather pass call any more,
	// so it is anchored before addMainPass() like the capture above.
	@Inject(method = "render",
	        at = @At(value = "INVOKE",
	                 target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V",
	                 shift = At.Shift.BEFORE))
	private void litematica_renderMainPass(GraphicsResourceAllocator resourceAllocator, boolean renderOutline,
	                                       CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor,
	                                       boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci,
	                                       @Local(name = "frame") FrameGraphBuilder frame,
                                           @Local(name = "featureFrame") FeatureRenderDispatcher.PreparedFrame featureFrame,
                                           @Local(name = "profiler") ProfilerFiller profiler)
	{
//		if (IrisCompat.isShaderActive())
//		{
//			IrisRenderingFix.INSTANCE.renderMainPassWithShadersOn(frame, this.targets, featureFrame, profiler);
//		}
	}

	@Inject(method = "submitEntities", at = @At("RETURN"))
	private void litematica_onPostRenderEntities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci)
	{
        this.litematica$prepareProfiler();
		LitematicaRenderer.getInstance().piecewiseRenderEntities(poseStack, levelRenderState, output, this.profiler);
	}

    @Inject(method = "submitBlockEntities", at = @At(value = "RETURN"))
    private void litematica_onPostRenderBlockEntities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector, CallbackInfo ci)
    {
        this.litematica$prepareProfiler();
        LitematicaRenderer.getInstance().piecewiseRenderBlockEntities(poseStack, levelRenderState, this.submitNodeStorage, this.profiler);
    }

	@Inject(method = "endFrame", at = @At("TAIL"))
	private void litematica_onEndFrame(CallbackInfo ci)
	{
		LitematicaRenderer.getInstance().onEndFrame();
	}

	@Inject(method = "close", at = @At("TAIL"))
	private void litematica_onClose(CallbackInfo ci)
	{
		LitematicaRenderer.getInstance().onClose();
	}
}
