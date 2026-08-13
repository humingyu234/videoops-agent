package org.dromara.aivideo.infra.asset;

import org.dromara.aivideo.asset.domain.FileObject;
import org.dromara.aivideo.asset.service.IFileSecurityScanService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** Reads the private object server-side and verifies its actual media header before use. */
@Service
public class WorkflowFileSecurityScanServiceImpl implements IFileSecurityScanService {

    private final WorkflowInputFileValidator validator = new WorkflowInputFileValidator();

    @Override
    public FileSecurityScanResult scan(FileObject fileObject) {
        if (fileObject == null || fileObject.getObjectKey() == null || fileObject.getObjectKey().isBlank()) {
            throw new ServiceException("工作流输入文件不可用");
        }
        return OssFactory.instance().download(fileObject.getObjectKey(), (object, input) -> {
            try {
                byte[] header = input.readNBytes(64);
                validator.requireDeclaredTypeMatches(fileObject.getContentType(), header);
                String contentType = validator.detectContentType(header);
                return new FileSecurityScanResult(contentType, object.size(), format(contentType));
            } catch (IOException exception) {
                throw new ServiceException("工作流输入文件读取失败", exception);
            }
        });
    }

    private String format(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "audio/wav" -> "wav";
            case "audio/mpeg" -> "mp3";
            case "video/mp4" -> "mp4";
            default -> throw new ServiceException("工作流输入文件类型不可用");
        };
    }
}
