package odinclient.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;

import net.minecraft.client.gl.RenderPipelines;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("TRANSFORMS_AND_PROJECTION_SNIPPET")
    static Snippet odinclient$getMatricesProjectionSnippet() {
        throw new UnsupportedOperationException();
    }
}
