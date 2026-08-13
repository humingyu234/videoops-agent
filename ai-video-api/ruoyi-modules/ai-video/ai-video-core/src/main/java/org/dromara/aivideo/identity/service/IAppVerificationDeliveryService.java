package org.dromara.aivideo.identity.service;

import org.dromara.aivideo.identity.dto.AppVerificationDeliveryDTO;
import org.dromara.aivideo.identity.security.AppVerificationChannel;

/**
 * 创作端验证码投递基础设施服务。
 *
 * <p>实现只负责投递，不能查询创作端或运营端用户表，也不得记录联系方式或验证码明文。</p>
 */
public interface IAppVerificationDeliveryService {

    /**
     * 当前适配器负责的联系方式渠道。
     */
    AppVerificationChannel channel();

    /**
     * 投递一次性验证码；失败时抛出异常，由调用方撤销已创建挑战。
     */
    void deliver(AppVerificationDeliveryDTO command);
}
