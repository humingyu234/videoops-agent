package org.dromara.aivideo.asset.dto;

/** 人物照片上传命令；归属只从当前 app 会话派生。 */
public record UploadPortraitImageDTO(String fileName, String contentType, byte[] content) {
}
