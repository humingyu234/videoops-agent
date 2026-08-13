package org.dromara.aivideo.identity.event;

/**
 * 创作端 app 会话已正常注销后的同步内部事件。
 *
 * @param sessionId 服务端随机会话编号，不包含令牌原文
 */
public record AppSessionEndedEvent(String sessionId) {

    /**
     * 校验事件只携带可用于删除在线索引的安全会话编号。
     */
    public AppSessionEndedEvent {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("创作端会话编号格式不安全");
        }
    }
}
