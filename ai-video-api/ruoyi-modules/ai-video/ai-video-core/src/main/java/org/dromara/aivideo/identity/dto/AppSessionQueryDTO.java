package org.dromara.aivideo.identity.dto;

import lombok.Getter;
import lombok.Setter;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 创作端在线会话分页查询条件。
 */
@Getter
@Setter
public class AppSessionQueryDTO extends PageQuery {

    /**
     * 要筛选的创作端用户编号。
     */
    private Long appUserId;

    /**
     * 要筛选的创作端认证客户端标识。
     */
    private String clientId;
}
