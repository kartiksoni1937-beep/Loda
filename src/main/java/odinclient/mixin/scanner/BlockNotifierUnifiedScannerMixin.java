package odinclient.mixin.scanner;

import odinclient.module.world.BlockNotifier;
import odinclient.util.ScannerExecutor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

@Mixin(BlockNotifier.class)
public abstract class BlockNotifierUnifiedScannerMixin {
    @Redirect(
            method = "createScanExecutor",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/Executors;newFixedThreadPool(ILjava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"),
            remap = false)
    private static ExecutorService odinclient$useUnifiedScanner(
            int threads, ThreadFactory threadFactory) {
        return ScannerExecutor.getExecutor();
    }
}
