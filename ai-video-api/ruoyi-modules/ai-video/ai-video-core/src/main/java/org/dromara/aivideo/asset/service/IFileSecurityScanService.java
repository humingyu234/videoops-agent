package org.dromara.aivideo.asset.service;

import org.dromara.aivideo.asset.domain.FileObject;

/** Fail-closed inspection of an uploaded private object before it becomes an asset. */
public interface IFileSecurityScanService {

    FileSecurityScanResult scan(FileObject fileObject);

    record FileSecurityScanResult(String contentType, Long sizeBytes, String fileFormat) {
    }
}
