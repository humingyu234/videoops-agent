package org.dromara.aivideo.identity.service;

import org.dromara.aivideo.identity.domain.AppExternalIdentityChannel;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.dto.AppExternalIdentityRequestDTO;

/**
 * 创作端外部身份授权基础设施服务。
 *
 * <p>实现只能验证外部身份并返回来源和主体编号；不得查询用户表、创建用户或签发会话。</p>
 */
public interface IAppExternalIdentityService {

    /**
     * 当前适配器负责的授权渠道。
     */
    AppExternalIdentityChannel channel();

    /**
     * 消费一次性外部授权并解析身份。
     *
     * @param command 外部授权命令
     * @return 仅包含来源和外部主体编号的身份结果
     */
    AppExternalIdentityDTO exchange(AppExternalIdentityRequestDTO command);
}
