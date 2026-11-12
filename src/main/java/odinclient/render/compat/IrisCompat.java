package odinclient.render.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.lang.reflect.Method;

public final class IrisCompat {
    private static final Object api;
    private static final Method isRenderingShadowPassMethod;
    private static final Method isShaderPackInUseMethod;

    private IrisCompat() {}

    public static boolean isRenderingShadowPass() {
        return invokeBoolean(isRenderingShadowPassMethod);
    }

    public static boolean isShaderPackInUse() {
        return invokeBoolean(isShaderPackInUseMethod);
    }

    public static void assignBasicColorPipeline(RenderPipeline pipeline) {
        try {
            Class<?> irisPipelines = Class.forName("net.irisshaders.iris.pipeline.IrisPipelines");
            Class<? extends Enum> shaderKeyClass =
                    Class.forName("net.irisshaders.iris.pipeline.programs.ShaderKey")
                            .asSubclass(Enum.class);
            Enum<?> basicColor = Enum.valueOf(shaderKeyClass, "BASIC_COLOR");
            Method assignPipeline =
                    irisPipelines.getMethod("assignPipeline", RenderPipeline.class, shaderKeyClass);
            assignPipeline.invoke(null, pipeline, basicColor);
        } catch (Throwable ignored) {
        }
    }

    private static boolean invokeBoolean(Method method) {
        if (api != null && method != null) {
            try {
                return (Boolean) method.invoke(api);
            } catch (Throwable e) {
                return false;
            }
        } else {
            return false;
        }
    }

    static {
        Object apiInstance = null;
        Method shadowPassMethod = null;
        Method shaderPackMethod = null;

        try {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            apiInstance = irisApi.getMethod("getInstance").invoke(null);
            shadowPassMethod = irisApi.getMethod("isRenderingShadowPass");
            shaderPackMethod = irisApi.getMethod("isShaderPackInUse");
        } catch (Throwable ignored) {
        }

        api = apiInstance;
        isRenderingShadowPassMethod = shadowPassMethod;
        isShaderPackInUseMethod = shaderPackMethod;
    }
}
