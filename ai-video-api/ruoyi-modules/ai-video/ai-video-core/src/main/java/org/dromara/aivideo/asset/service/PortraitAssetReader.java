package org.dromara.aivideo.asset.service;

import org.dromara.aivideo.asset.dto.AssetDTO;

import java.io.InputStream;

/** 从 OSS 读取已归属人物形象资产流的回调。 */
@FunctionalInterface
public interface PortraitAssetReader<T> {
    T read(AssetDTO asset, InputStream input);
}
