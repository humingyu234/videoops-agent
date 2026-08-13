package org.dromara.aivideo.identity.service;

import org.dromara.aivideo.identity.dto.CreateAppAuthClientDTO;
import org.dromara.aivideo.identity.dto.RotateAppAuthClientSecretDTO;
import org.dromara.aivideo.identity.dto.UpdateAppAuthClientDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSecretDTO;
import org.dromara.aivideo.identity.security.AppActorContext;

/**
 * 创作端认证客户端写模型服务。
 */
public interface IAppAuthClientService {

    /**
     * 创建创作端认证客户端并返回仅本次可见的明文密钥。
     */
    AppAuthClientSecretDTO create(CreateAppAuthClientDTO command, AppActorContext actor);

    /**
     * 修改创作端认证客户端策略或状态。
     */
    void update(UpdateAppAuthClientDTO command, AppActorContext actor);

    /**
     * 轮换创作端认证客户端密钥并返回仅本次可见的明文密钥。
     */
    AppAuthClientSecretDTO rotateSecret(RotateAppAuthClientSecretDTO command, AppActorContext actor);
}
