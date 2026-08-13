package org.dromara.aivideo.infra.oss;

import lombok.Data;
import lombok.ToString;
import org.dromara.common.oss.properties.OssProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Development OSS override shared by the creator and operating API starters. */
@Data
@ConfigurationProperties(prefix = "aivideo.oss")
public class AiVideoOssProperties {

    private boolean enabled;
    private String configKey = "aliyun";
    private String endpoint;
    private String domainUrl = "";
    private String prefix = "";
    @ToString.Exclude
    private String accessKey;
    @ToString.Exclude
    private String secretKey;
    private String bucketName;
    private String region;
    private boolean https = true;
    private String accessPolicy = "0";

    OssProperties toOssProperties() {
        OssProperties target = new OssProperties();
        target.setEndpoint(endpoint);
        target.setDomainUrl(domainUrl);
        target.setPrefix(prefix);
        target.setAccessKey(accessKey);
        target.setSecretKey(secretKey);
        target.setBucketName(bucketName);
        target.setRegion(region);
        target.setIsHttps(https ? "Y" : "N");
        target.setAccessPolicy(accessPolicy);
        return target;
    }
}
