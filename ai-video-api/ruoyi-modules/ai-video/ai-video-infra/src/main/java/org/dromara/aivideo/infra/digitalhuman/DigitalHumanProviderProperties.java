package org.dromara.aivideo.infra.digitalhuman;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 数字人供应商和私有媒体配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "digital-human")
public class DigitalHumanProviderProperties {

    /** 服务端私有媒体根目录。 */
    private String mediaRoot;

    /** IndexTTS2 配置。 */
    private IndexTts2 indexTts2 = new IndexTts2();

    /** ComfyUI 配置。 */
    private ComfyUi comfyUi = new ComfyUi();

    @Getter
    @Setter
    public static class IndexTts2 {
        private String baseUrl;
        private String apiKey;
        private String basicUser;
        private String basicPassword;
        private String caCertificate;
        private boolean insecureSkipTlsVerify;
    }

    @Getter
    @Setter
    public static class ComfyUi {
        private String baseUrl;
        private String basicUser;
        private String basicPassword;
        private String workflowFile;
        private String workflowId;
        private List<String> insecureHttpAllowedHosts = List.of();
        private boolean insecureSkipTlsVerify;
    }
}
