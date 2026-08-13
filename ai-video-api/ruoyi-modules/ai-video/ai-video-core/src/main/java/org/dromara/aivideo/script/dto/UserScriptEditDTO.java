package org.dromara.aivideo.script.dto;

public record UserScriptEditDTO(String scriptId, String parentVersionId, String expectedScriptRevision,
                                String displayTitle, String scriptText, String idempotencyKey) {
}
