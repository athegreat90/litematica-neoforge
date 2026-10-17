package fi.dy.masa.litematica.render;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;

import fi.dy.masa.litematica.Reference;

/**
 * Litematica's own pipelines for the schematic terrain renderer.
 * MaFgLib 0.6.0 no longer ships the LEGACY_* terrain pipelines, so they are defined here
 * and registered through {@link RegisterRenderPipelinesEvent} (see {@link #registerPipelines}).
 */
public class LitematicaPipelines
{
//	// todo POSITION_COLOR Snippet
//	public static RenderPipeline.Snippet POSITION_COLOR_STAGE;
//	public static RenderPipeline.Snippet POSITION_COLOR_TRANSLUCENT_STAGE;
//	public static RenderPipeline.Snippet POSITION_COLOR_MASA_STAGE;
//
//	// POSITION_COLOR_TRANSLUCENT
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL;
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT_NO_DEPTH;
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT_LEQUAL_DEPTH_OFFSET_1;
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT_LEQUAL_DEPTH_OFFSET_2;
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT_LEQUAL_DEPTH_OFFSET_3;
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT_LEQUAL_DEPTH_NO_CULL;
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT_LEQUAL_DEPTH;
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT_DEPTH_MASK;
//	public static RenderPipeline POSITION_COLOR_TRANSLUCENT;
//
//	// POSITION_COLOR_MASA
//	public static RenderPipeline POSITION_COLOR_MASA_NO_DEPTH_NO_CULL;
//	public static RenderPipeline POSITION_COLOR_MASA_NO_DEPTH;
//	public static RenderPipeline POSITION_COLOR_MASA_LEQUAL_DEPTH_OFFSET_1;
//	public static RenderPipeline POSITION_COLOR_MASA_LEQUAL_DEPTH_OFFSET_2;
//	public static RenderPipeline POSITION_COLOR_MASA_LEQUAL_DEPTH_OFFSET_3;
//	public static RenderPipeline POSITION_COLOR_MASA_LEQUAL_DEPTH_NO_CULL;
//	public static RenderPipeline POSITION_COLOR_MASA_LEQUAL_DEPTH;
//	public static RenderPipeline POSITION_COLOR_MASA_DEPTH_MASK;
//	public static RenderPipeline POSITION_COLOR_MASA;
//
//	// todo DEBUG_LINES Snippet
//	public static RenderPipeline.Snippet DEBUG_LINES_STAGE;
//	public static RenderPipeline.Snippet DEBUG_LINES_TRANSLUCENT_STAGE;
//	public static RenderPipeline.Snippet DEBUG_LINES_MASA_SIMPLE_STAGE;
//
//	// DEBUG_LINES_TRANSLUCENT
//	public static RenderPipeline DEBUG_LINES_TRANSLUCENT_NO_DEPTH_NO_CULL;
//	public static RenderPipeline DEBUG_LINES_TRANSLUCENT_NO_DEPTH;
//	public static RenderPipeline DEBUG_LINES_TRANSLUCENT_NO_CULL;
//	public static RenderPipeline DEBUG_LINES_TRANSLUCENT_LEQUAL_DEPTH;
//	public static RenderPipeline DEBUG_LINES_TRANSLUCENT_OFFSET_1;
//	public static RenderPipeline DEBUG_LINES_TRANSLUCENT_OFFSET_2;
//	public static RenderPipeline DEBUG_LINES_TRANSLUCENT_OFFSET_3;
//	public static RenderPipeline DEBUG_LINES_TRANSLUCENT;
//
//	// DEBUG_LINES_MASA_SIMPLE
//	public static RenderPipeline DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL;
//	public static RenderPipeline DEBUG_LINES_MASA_SIMPLE_NO_DEPTH;
//	public static RenderPipeline DEBUG_LINES_MASA_SIMPLE_NO_CULL;
//	public static RenderPipeline DEBUG_LINES_MASA_SIMPLE_LEQUAL_DEPTH;
//	public static RenderPipeline DEBUG_LINES_MASA_SIMPLE_OFFSET_1;
//	public static RenderPipeline DEBUG_LINES_MASA_SIMPLE_OFFSET_2;
//	public static RenderPipeline DEBUG_LINES_MASA_SIMPLE_OFFSET_3;
//	public static RenderPipeline DEBUG_LINES_MASA_SIMPLE;
//

	// Uniform buffer "ChunkFix", see ChunkFixUniform
	public static final BindGroupLayout LEGACY_TERRAIN_GROUP = BindGroupLayout.builder()
			.withUniform("ChunkFix", UniformType.UNIFORM_BUFFER)
			.build();

	private static final RenderPipeline.Snippet LEGACY_TERRAIN_STAGE =
			RenderPipeline.builder()
			              .withVertexShader(getId("legacy_terrain"))
			              .withFragmentShader(getId("legacy_terrain"))
			              .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			              .withBindGroupLayout(BindGroupLayouts.PROJECTION)
			              .withBindGroupLayout(BindGroupLayouts.FOG)
			              .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
			              .withBindGroupLayout(LEGACY_TERRAIN_GROUP)
			              .withVertexBinding(0, DefaultVertexFormat.BLOCK)
			              .withPrimitiveTopology(PrimitiveTopology.QUADS)
			              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
			              // 26.3 pipelines start with no color target; like vanilla's SOLID_TERRAIN, declare the one the pass has
			              .withColorTargetState(ColorTargetState.DEFAULT)
			              .buildSnippet();

	private static final RenderPipeline.Snippet LEGACY_TERRAIN_TRANSLUCENT_STAGE =
			RenderPipeline.builder(LEGACY_TERRAIN_STAGE)
			              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			              .buildSnippet();

	// LEGACY_TERRAIN
	public static final RenderPipeline LEGACY_SOLID_TERRAIN =
			RenderPipeline.builder(LEGACY_TERRAIN_STAGE)
			              .withLocation(getId("pipeline/legacy/solid"))
			              .build();

	public static final RenderPipeline LEGACY_WIREFRAME =
			RenderPipeline.builder(LEGACY_TERRAIN_STAGE)
			              .withLocation(getId("pipeline/legacy/wireframe"))
			              .withPolygonMode(PolygonMode.WIREFRAME)
			              .build();

	public static final RenderPipeline LEGACY_CUTOUT_TERRAIN =
			RenderPipeline.builder(LEGACY_TERRAIN_STAGE)
			              .withLocation(getId("pipeline/legacy/cutout"))
			              .withShaderDefine("ALPHA_CUTOUT", 0.5F)
			              .build();

	// LEGACY_TERRAIN_OFFSET
	public static final RenderPipeline LEGACY_SOLID_TERRAIN_OFFSET =
			RenderPipeline.builder(LEGACY_TERRAIN_STAGE)
			              .withLocation(getId("pipeline/legacy/solid/masa/offset"))
			              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 0.3f, 0.6f))
			              .build();

	public static final RenderPipeline LEGACY_WIREFRAME_OFFSET =
			RenderPipeline.builder(LEGACY_TERRAIN_STAGE)
			              .withLocation(getId("pipeline/legacy/wireframe/offset"))
			              .withPolygonMode(PolygonMode.WIREFRAME)
			              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 0.3f, 0.6f))
			              .build();

	public static final RenderPipeline LEGACY_CUTOUT_TERRAIN_OFFSET =
			RenderPipeline.builder(LEGACY_TERRAIN_STAGE)
			              .withLocation(getId("pipeline/legacy/cutout/offset"))
			              .withShaderDefine("ALPHA_CUTOUT", 0.5F)
			              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 0.3f, 0.6f))
			              .build();

	// LEGACY_TERRAIN_TRANSLUCENT
	public static final RenderPipeline LEGACY_TRANSLUCENT =
			RenderPipeline.builder(LEGACY_TERRAIN_TRANSLUCENT_STAGE)
			              .withLocation(getId("pipeline/legacy/translucent"))
			              .withShaderDefine("ALPHA_CUTOUT", 0.1F)
			              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
			              .build();

	public static final RenderPipeline LEGACY_TRANSLUCENT_OFFSET =
			RenderPipeline.builder(LEGACY_TERRAIN_TRANSLUCENT_STAGE)
			              .withLocation(getId("pipeline/legacy/translucent/offset"))
			              .withShaderDefine("ALPHA_CUTOUT", 0.1F)
			              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 0.3f, 0.6f))
			              .build();

	private static Identifier getId(String id)
	{
		return Identifier.fromNamespaceAndPath(Reference.MOD_ID, id);
	}

	/**
	 * Register the pipelines so that NeoForge/Minecraft precompile them. Listen to this on the mod event bus.
	 */
	public static void registerPipelines(RegisterRenderPipelinesEvent event)
	{
		event.registerPipeline(LEGACY_SOLID_TERRAIN);
		event.registerPipeline(LEGACY_WIREFRAME);
		event.registerPipeline(LEGACY_CUTOUT_TERRAIN);
		event.registerPipeline(LEGACY_SOLID_TERRAIN_OFFSET);
		event.registerPipeline(LEGACY_WIREFRAME_OFFSET);
		event.registerPipeline(LEGACY_CUTOUT_TERRAIN_OFFSET);
		event.registerPipeline(LEGACY_TRANSLUCENT);
		event.registerPipeline(LEGACY_TRANSLUCENT_OFFSET);
	}
}
