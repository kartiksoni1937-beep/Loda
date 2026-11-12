package odinclient.mixin.accessor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DrawContext.class)
public interface GuiGraphicsAccessor {
    @Accessor("state")
    GuiRenderState odinclient$renderState();
}
