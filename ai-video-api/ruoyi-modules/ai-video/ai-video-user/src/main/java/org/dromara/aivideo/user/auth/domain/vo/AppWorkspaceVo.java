package org.dromara.aivideo.user.auth.domain.vo;

/**
 * 创作端当前工作区的公开摘要。
 *
 * @param id 不可逆工作区键，不暴露租户或所有者数字编号
 * @param name 工作区展示名称
 * @param roleCode 当前工作区角色编码
 */
public record AppWorkspaceVo(String id, String name, String roleCode) {
}
