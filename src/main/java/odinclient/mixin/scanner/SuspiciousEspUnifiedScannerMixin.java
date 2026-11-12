package odinclient.mixin.scanner;

import odinclient.module.render.esp.SuspiciousESP;
import odinclient.util.ScannerExecutor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

@Mixin(SuspiciousESP.class)
public abstract class SuspiciousEspUnifiedScannerMixin {
    @Unique private int odinclient$sharedSliceRemaining;

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/Executors;newSingleThreadExecutor(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"),
            remap = false)
    private ExecutorService odinclient$useUnifiedScanner(ThreadFactory factory) {
        return ScannerExecutor.getExecutor();
    }

    @Inject(method = "drainScanner", at = @At("HEAD"), remap = false)
    private void odinclient$beginSharedSlice(CallbackInfo ci) {
        this.odinclient$sharedSliceRemaining = 4;
    }

    @Redirect(
            method = "drainScanner",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/ConcurrentLinkedQueue;poll()Ljava/lang/Object;"),
            remap = false)
    private Object odinclient$pollSharedSlice(ConcurrentLinkedQueue<?> queue) {
        return this.odinclient$sharedSliceRemaining-- > 0 ? queue.poll() : null;
    }
}
