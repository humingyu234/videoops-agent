package org.dromara.aivideo.script.dto;

public record UserScriptCreateDTO(String displayTitle, String scriptText, String idempotencyKey) {
}
