package odinclient.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;

import net.minecraft.client.gl.RenderPipelines;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface ShaderPipelineInvoker {
    @Accessor("RENDERTYPE_LINES_SNIPPET")
    static Snippet odinclient$getLinesSnippet() {
        throw new AssertionError();
    }

    @Accessor("POSITION_COLOR_SNIPPET")
    static Snippet odinclient$getPositionColorSnippet() {
        throw new AssertionError();
    }

    @Accessor("GUI_SNIPPET")
    static Snippet odinclient$getGuiSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline odinclient$registerPipeline(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
