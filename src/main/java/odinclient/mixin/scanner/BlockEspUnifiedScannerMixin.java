package odinclient.mixin.scanner;

import odinclient.module.render.esp.BlockEsp;
import odinclient.util.ScannerExecutor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

@Mixin(BlockEsp.class)
public abstract class BlockEspUnifiedScannerMixin {
    @Redirect(
            method = "restartScanner",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/Executors;newSingleThreadExecutor(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"),
            remap = false)
    private ExecutorService odinclient$useUnifiedScanner(ThreadFactory factory) {
        return ScannerExecutor.getExecutor();
    }
}
