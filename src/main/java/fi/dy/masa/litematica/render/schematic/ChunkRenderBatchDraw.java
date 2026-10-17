package fi.dy.masa.litematica.render.schematic;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.annotation.Nullable;

import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.util.profiling.ProfilerFiller;

import fi.dy.masa.malilib.compat.iris.IrisCompat;
import fi.dy.masa.litematica.Litematica;

public record ChunkRenderBatchDraw(
		GpuTextureView atlasTexture,
		EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> drawData,
        boolean renderCollidingBlocks,
		boolean renderTranslucent,
        int maxIndicesRequired,
		GpuBufferSlice[] dynamicTransforms,
		GpuBufferSlice chunkFixUBO)
{
    private static boolean errorLogged = false;

    /**
     * Draw one block layer group of the schematic.
     *
     * @param existingPass The {@link RenderPass} vanilla is currently recording into (26.3 executeSolid / executeClassicTransparency),
     *                     or null to record into a pass of our own on the main render target. An existing pass is never closed here.
     */
    public void draw(final ChunkSectionLayerGroup group, final GpuSampler sampler, ProfilerFiller profiler, @Nullable RenderPass existingPass)
    {
//	    Litematica.LOGGER.error("ChunkRenderBatchDraw::draw({})", group.label());
        RenderSystem.AutoStorageIndexBuffer defaultIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer defaultIBO = this.maxIndicesRequired() == 0 ? null : defaultIndices.getBuffer(this.maxIndicesRequired());
        IndexType indexType = this.maxIndicesRequired() == 0 ? null : defaultIndices.type();
        Minecraft mc = Minecraft.getInstance();

        profiler.push("draw_group");

        try
        {
            if (existingPass != null)
            {
                // The caller swapped the shader fog; bind the default uniforms again so this pass uses it
                RenderSystem.bindDefaultUniforms(existingPass);
                this.drawLayers(existingPass, group, sampler, defaultIBO, indexType, mc, profiler);
            }
            else
            {
                // 26.3 has no separate translucent target any more (26.2 fell back to the main target when there was none)
                RenderTarget fb = mc.gameRenderer.mainRenderTarget();

                try (RenderPass pass = RenderSystem.getDevice()
                                                   .createCommandEncoder()
                                                   .createRenderPass(
                                                           () -> "litematica:schematic_chunk/" + group.label(),
                                                           fb.getColorTextureView(),
                                                           Optional.empty(),
                                                           fb.getDepthTextureView(),
                                                           OptionalDouble.empty()
                                                   ))
                {
                    RenderSystem.bindDefaultUniforms(pass);
                    this.drawLayers(pass, group, sampler, defaultIBO, indexType, mc, profiler);
                }
            }
        }
        catch (Exception e)
        {
            // Keep the frame going, but do not hide the failure (only the first one is logged to avoid spamming every frame)
            if (!errorLogged)
            {
                errorLogged = true;
                Litematica.LOGGER.error("ChunkRenderBatchDraw#draw(): failed to draw schematic group [{}]", group.label(), e);
            }
        }

        profiler.pop();
    }

    private void drawLayers(final RenderPass pass, final ChunkSectionLayerGroup group, final GpuSampler sampler,
                            @Nullable final GpuBuffer defaultIBO, @Nullable final IndexType indexType,
                            final Minecraft mc, final ProfilerFiller profiler)
    {
        boolean wf = SharedConstants.DEBUG_HOTKEYS && mc.wireframe;

//		if (renderTranslucent() && this.dynamicTransform() != null)
//		{
//			pass.setUniform("DynamicTransforms", this.dynamicTransform());
//		}

        pass.setUniform("ChunkFix", this.chunkFixUBO);
        pass.setUniform("Sampler0", this.atlasTexture, sampler);
        pass.setUniform("Sampler2", mc.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        for (ChunkSectionLayer layer : group.layers())
        {
            List<RenderPass.Draw<GpuBufferSlice[]>> draws = this.drawData().get(layer);

            profiler.popPush("draw_group_" + layer.label());
            if (!draws.isEmpty())
            {
                if (layer == ChunkSectionLayer.TRANSLUCENT)
                {
                    draws = draws.reversed();
                }

                if (wf)
                {
                    pass.setPipeline(RenderSystem.getCompiledPipeline(this.renderCollidingBlocks()
                                     ? ChunkRenderLayers.getWireframe().getRight()
                                     : ChunkRenderLayers.getWireframe().getLeft()));
                }
                else
                {
                    if (this.renderTranslucent())
                    {
                        pass.setPipeline(RenderSystem.getCompiledPipeline(this.renderCollidingBlocks()
                                         ? ChunkRenderLayers.PIPELINE_MAP.get(ChunkSectionLayer.TRANSLUCENT).getRight()
                                         : ChunkRenderLayers.PIPELINE_MAP.get(ChunkSectionLayer.TRANSLUCENT).getLeft()
                        ));
                    }
                    else
                    {
                        pass.setPipeline(RenderSystem.getCompiledPipeline(this.renderCollidingBlocks()
                                         ? ChunkRenderLayers.PIPELINE_MAP.get(layer).getRight()
                                         : ChunkRenderLayers.PIPELINE_MAP.get(layer).getLeft()
                        ));
                    }

                    pass.drawMultipleIndexed(draws, defaultIBO, indexType, List.of("DynamicTransforms"), this.dynamicTransforms());
                }
            }
        }
    }
}
