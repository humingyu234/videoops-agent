package org.dromara.aivideo.portrait.dto;

import java.util.List;

/** 创建人物形象命令。 */
public record CreatePortraitDTO(String assetId, String name, String gender, List<String> sceneTags, String note,
                                String idempotencyKey) {
}
