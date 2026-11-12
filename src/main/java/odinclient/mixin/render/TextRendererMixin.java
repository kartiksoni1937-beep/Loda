package odinclient.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;

import odinclient.module.privacy.NameProtect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    @ModifyVariable(
            method =
                    "prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private String odinclient$protectPreparedString(String text) {
        return NameProtect.replaceNames(text);
    }

    @ModifyVariable(
            method =
                    "prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private OrderedText odinclient$protectPreparedText(OrderedText text) {
        return NameProtect.replaceNamesInOrderedText(text);
    }

    @ModifyVariable(
            method =
                    "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private OrderedText odinclient$protectOutlinedText(OrderedText text) {
        return NameProtect.replaceNamesInOrderedText(text);
    }

    @ModifyVariable(
            method = "getWidth(Ljava/lang/String;)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private String odinclient$protectStringWidth(String text) {
        return NameProtect.replaceNames(text);
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/StringVisitable;)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private StringVisitable odinclient$protectVisitableWidth(StringVisitable text) {
        return NameProtect.replaceNamesInVisitable(text);
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/OrderedText;)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private OrderedText odinclient$protectOrderedWidth(OrderedText text) {
        return NameProtect.replaceNamesInOrderedText(text);
    }
}
