package org.dromara.aivideo.asset.service;

import org.dromara.aivideo.asset.dto.AssetDTO;

import java.io.InputStream;

/** 在 OSS 下载回调有效期内消费私有声音流。 */
@FunctionalInterface
public interface VoiceAssetReader<T> {
    T read(AssetDTO asset, InputStream input);
}
