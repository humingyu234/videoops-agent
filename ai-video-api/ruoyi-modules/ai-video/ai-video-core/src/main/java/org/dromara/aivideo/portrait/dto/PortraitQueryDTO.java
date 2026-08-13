package org.dromara.aivideo.portrait.dto;

/** 人物形象分页筛选条件。 */
public record PortraitQueryDTO(String keyword, String availabilityStatus, String gender) {
}
