package org.dromara.aivideo.identity.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 在 Spring 启动时注册创作端专属 app 登录逻辑。
 */
@Component
@ConditionalOnAppSessionRuntimeEnabled
public class AppStpLogicRegistrar {

    private final AppStpLogic logic;

    /**
     * 创建并注册 app 登录逻辑，不声明第二个 StpLogic Spring Bean。
     *
     * @param properties 创作端独立 Sa-Token 配置
     */
    public AppStpLogicRegistrar(AppSaTokenProperties properties) {
        Objects.requireNonNull(properties, "创作端 Sa-Token 配置不能为空");
        SaManager.removeStpLogic("app");
        AppStpLogic appLogic = new AppStpLogic();
        appLogic.setConfig(appTokenConfig(properties));
        SaManager.putStpLogic(appLogic);
        this.logic = appLogic;
    }

    /**
     * 返回已注册的创作端 app 登录逻辑。
     *
     * @return 创作端 app 登录逻辑
     */
    AppStpLogic logic() {
        return logic;
    }

    /**
     * 构造与默认 login 命名空间完全隔离的 app 登录配置。
     *
     * @param properties 创作端独立 Sa-Token 配置
     * @return app 登录逻辑配置
     */
    private SaTokenConfig appTokenConfig(AppSaTokenProperties properties) {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setIsReadHeader(true);
        config.setIsReadBody(false);
        config.setIsReadCookie(false);
        config.setIsConcurrent(true);
        config.setIsShare(false);
        // 每次 app 登录都显式传入已验证客户端的 activeTimeout；AppStpLogic 使用 app 专属请求标记键。
        config.setDynamicActiveTimeout(true);
        config.setJwtSecretKey(properties.getJwtSecret());
        return config;
    }
}
