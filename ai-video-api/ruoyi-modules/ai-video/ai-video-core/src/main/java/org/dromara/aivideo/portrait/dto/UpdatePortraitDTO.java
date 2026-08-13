package org.dromara.aivideo.portrait.dto;

import java.util.List;

/** 修改人物形象资料命令。 */
public record UpdatePortraitDTO(String portraitId, String name, String gender, List<String> sceneTags,
                                String note, String expectedRevision) {
}
