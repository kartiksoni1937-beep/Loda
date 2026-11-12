package odinclient.mixin.fake;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import odinclient.module.fake.FakeElytra;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityRenderer.class)
public abstract class FakeElytraBipedMixin {
    @Inject(method = "updateBipedRenderState", at = @At("TAIL"))
    private static void odinclient$fakeElytraEquipment(
            LivingEntity entity,
            BipedEntityRenderState state,
            float tickDelta,
            ItemModelManager itemModelManager,
            CallbackInfo ci) {
        if (FakeElytra.isEnabled()) {
            ItemStack chestStack = state.equippedChestStack;
            if (chestStack != null && chestStack.isOf(Items.GOLDEN_CHESTPLATE)) {
                EquippableComponent elytra =
                        Items.ELYTRA.getDefaultStack().get(DataComponentTypes.EQUIPPABLE);
                if (elytra != null) {
                    chestStack.set(DataComponentTypes.EQUIPPABLE, elytra);
                }
            }

            if (entity == MinecraftClient.getInstance().player && FakeElytra.isGliding()) {
                state.isGliding = true;
                state.limbAmplitudeInverse = (float) entity.getVelocity().lengthSquared() / 0.2F;
                state.limbAmplitudeInverse =
                        Math.max(
                                1.0F,
                                state.limbAmplitudeInverse
                                        * state.limbAmplitudeInverse
                                        * state.limbAmplitudeInverse);
            }
        }
    }
}
