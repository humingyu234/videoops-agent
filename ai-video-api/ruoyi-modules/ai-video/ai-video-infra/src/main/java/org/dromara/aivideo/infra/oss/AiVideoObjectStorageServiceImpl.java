package org.dromara.aivideo.infra.oss;

import org.dromara.aivideo.asset.service.IObjectStorageService;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Uses the configured private OSS client to issue short-lived workflow input PUT URLs. */
@Service
public class AiVideoObjectStorageServiceImpl implements IObjectStorageService {

    private static final Duration PUT_URL_TTL = Duration.ofMinutes(15);

    @Override
    public SinglePutAuthorization createSinglePutAuthorization(String businessPrefix, String fileName, String contentType) {
        OssClient client = OssFactory.instance();
        String key = client.buildPathKey(businessPrefix, UUID.randomUUID() + suffix(fileName));
        return createSinglePutAuthorizationForExistingObject(key, contentType);
    }

    @Override
    public SinglePutAuthorization createSinglePutAuthorizationForExistingObject(String objectKey, String contentType) {
        OssClient client = OssFactory.instance();
        String putUrl = client.presignPutUrl(objectKey, PUT_URL_TTL, Map.of());
        return new SinglePutAuthorization(objectKey, putUrl, contentType);
    }

    private String suffix(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return ".bin";
        }
        String suffix = fileName.substring(dot).toLowerCase(java.util.Locale.ROOT);
        return suffix.matches("\\.[a-z0-9]{1,10}") ? suffix : ".bin";
    }
}
