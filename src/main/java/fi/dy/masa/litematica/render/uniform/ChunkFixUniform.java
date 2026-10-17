package fi.dy.masa.litematica.render.uniform;

import java.nio.ByteBuffer;
import javax.annotation.Nonnull;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.MappableRingBuffer;

import fi.dy.masa.malilib.compat.iris.IrisCompat;
import fi.dy.masa.litematica.Reference;

/**
 * Uniform buffer ("ChunkFix") read by the {@code legacy_terrain} shaders.
 * Layout (std140): ivec2 TextureSize, float ChunkVisibility, int UseRgss, int hasShadersOn.
 * This used to live in MaFgLib; MaFgLib 0.6.0 no longer ships it.
 */
public class ChunkFixUniform implements AutoCloseable
{
    private static final int UBO_SIZE = new Std140SizeCalculator().putIVec2().putFloat().putInt().putInt().get();
    private static final int UBO_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE;
    private final MappableRingBuffer ubo;

    public ChunkFixUniform()
    {
        this.ubo = new MappableRingBuffer(() -> Reference.MOD_NAME + " ChunkFix UBO", UBO_USAGE, UBO_SIZE);

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer buffer = stack.malloc(UBO_SIZE);
            this.fillBuffer(buffer, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, Float.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
    }

    /**
     * Update the UBO Buffer
     *
     * @param atlasWidth      ()
     * @param atlasHeight     ()
     * @param chunkVisibility ()
     */
    public void updateBuffer(int atlasWidth, int atlasHeight, float chunkVisibility)
            throws IllegalArgumentException
    {
        if (atlasWidth <= 0 || atlasHeight <= 0)
        {
            throw new IllegalArgumentException("atlasWidth and atlasHeight must be positive");
        }

        final int useRGSS = Minecraft.getInstance().options.textureFiltering().get() == TextureFilteringMethod.RGSS ? 1 : 0;
        final int hasShadersOn = IrisCompat.isShaderActive() ? 1 : 0;

        try (GpuBufferSlice.MappedView mappedView = this.ubo.currentBuffer().map(false, true))
        {
            this.fillBuffer(mappedView.data(), 0, atlasWidth, atlasHeight, chunkVisibility, useRGSS, hasShadersOn);
        }
    }

    public void fillBuffer(final ByteBuffer buffer, final int offset, int atlasWidth, int atlasHeight, float chunkVisibility, int useRGSS, int hasShadersOn)
    {
        buffer.position(offset);
        Std140Builder.intoBuffer(buffer).putIVec2(atlasWidth, atlasHeight).putFloat(chunkVisibility).putInt(useRGSS).putInt(hasShadersOn);
    }

    /**
     * Draw the UBO buffer to a render pass
     *
     * @param pass ()
     */
    public void drawPass(@Nonnull RenderPass pass)
    {
        pass.setUniform("ChunkFix", this.getCurrentBufferSlice());
    }

    /**
     * Get the 'currentBuffer' from the Ring Buffer.
     *
     * @return ()
     */
    public GpuBufferSlice getCurrentBufferSlice()
    {
        return this.ubo.currentBuffer().slice(0L, UBO_SIZE);
    }

    /**
     * Call at the end of the frame pass
     */
    public void endFrame()
    {
        this.ubo.rotate();
    }

    @Override
    public void close() throws Exception
    {
        this.ubo.close();
    }
}
