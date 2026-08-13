package org.dromara.aivideo.creation.service;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;

import java.io.IOException;
import java.io.InputStream;

@JsonIgnoreType
public interface CreationMediaHandle extends AutoCloseable {
    CreationAssetResolveDTO metadata();
    InputStream stream();
    long offset();
    long length();
    long totalSize();
    @Override void close() throws IOException;
}
