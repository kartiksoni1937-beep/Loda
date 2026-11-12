package odinclient.mixin.accessor;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLayer.class)
public interface ShaderLayerInvoker {
    @Invoker("of")
    static RenderLayer odinclient$createLayer(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
