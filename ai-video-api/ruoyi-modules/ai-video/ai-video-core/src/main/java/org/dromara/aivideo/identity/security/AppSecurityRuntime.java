package org.dromara.aivideo.identity.security;

/**
 * 创作端安全运行时启用判定的唯一事实源。
 *
 * <p>仅配置值为精确的 {@code true} 且当前运行时带有创作端启动标记时，
 * 才允许装配或放行创作端认证链。任一条件缺失时必须失效关闭，避免运营端或
 * 打包不完整的进程意外暴露 {@code /api/**}。</p>
 */
public final class AppSecurityRuntime {

    private static final String CREATOR_RUNTIME_MARKER = "META-INF/aivideo-creator-runtime.marker";

    private AppSecurityRuntime() {
    }

    /**
     * 判断当前进程是否可启用创作端安全运行时。
     *
     * @param configuredValue 原始开关值，不进行空白归一化
     * @param classLoader 当前运行时类加载器
     * @return 仅开关精确为 {@code true} 且存在创作端标记时返回 {@code true}
     */
    public static boolean isCreatorSecurityEnabled(String configuredValue, ClassLoader classLoader) {
        return classLoader != null
            && classLoader.getResource(CREATOR_RUNTIME_MARKER) != null
            && configuredValue != null
            && "true".equalsIgnoreCase(configuredValue);
    }
}
