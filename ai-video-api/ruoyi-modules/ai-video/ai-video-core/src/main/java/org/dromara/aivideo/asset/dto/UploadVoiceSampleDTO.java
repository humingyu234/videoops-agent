package org.dromara.aivideo.asset.dto;

import java.io.InputStream;

/** 声音样本流式上传命令。 */
public record UploadVoiceSampleDTO(String fileName, String contentType, long fileSize, InputStream content) {
}
