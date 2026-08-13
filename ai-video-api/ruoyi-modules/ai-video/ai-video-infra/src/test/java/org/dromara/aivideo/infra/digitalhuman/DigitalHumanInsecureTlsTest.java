package org.dromara.aivideo.infra.digitalhuman;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("dev")
class DigitalHumanInsecureTlsTest {

    @Test
    void exposesExplicitInsecureTlsSettingsForBothProviders() {
        DigitalHumanProviderProperties.IndexTts2 indexTts2 = new DigitalHumanProviderProperties.IndexTts2();
        indexTts2.setInsecureSkipTlsVerify(true);
        DigitalHumanProviderProperties.ComfyUi comfyUi = new DigitalHumanProviderProperties.ComfyUi();
        comfyUi.setInsecureSkipTlsVerify(true);

        assertThat(indexTts2.isInsecureSkipTlsVerify()).isTrue();
        assertThat(comfyUi.isInsecureSkipTlsVerify()).isTrue();
    }

    @Test
    void createsAnExplicitTrustAllClientOnlyWhenRequested() {
        var client = DigitalHumanHttpSupport.client(null, true);
        var trustManager = DigitalHumanHttpSupport.insecureTrustManager();

        assertThat(client.sslParameters().getEndpointIdentificationAlgorithm()).isBlank();
        assertThatCode(() -> trustManager.checkServerTrusted(new X509Certificate[0], "RSA"))
            .doesNotThrowAnyException();
        assertThat(trustManager.getAcceptedIssuers()).isEmpty();
    }

    @Test
    void providerConfigurationUsesConfiguredTlsVerificationMode() throws Exception {
        DigitalHumanProviderProperties properties = new DigitalHumanProviderProperties();
        DigitalHumanProviderConfiguration configuration = new DigitalHumanProviderConfiguration();

        properties.getIndexTts2().setInsecureSkipTlsVerify(false);
        properties.getComfyUi().setInsecureSkipTlsVerify(false);
        HttpClient secureIndexTts2 = httpClient(configuration.voiceSynthesisService(properties));
        HttpClient secureComfyUi = httpClient(configuration.digitalHumanVideoService(properties));

        properties.getIndexTts2().setInsecureSkipTlsVerify(true);
        properties.getComfyUi().setInsecureSkipTlsVerify(true);
        HttpClient insecureIndexTts2 = httpClient(configuration.voiceSynthesisService(properties));
        HttpClient insecureComfyUi = httpClient(configuration.digitalHumanVideoService(properties));

        assertThat(secureIndexTts2.sslContext()).isSameAs(SSLContext.getDefault());
        assertThat(secureComfyUi.sslContext()).isSameAs(SSLContext.getDefault());
        assertThat(insecureIndexTts2.sslContext()).isNotSameAs(SSLContext.getDefault());
        assertThat(insecureComfyUi.sslContext()).isNotSameAs(SSLContext.getDefault());
        assertThat(insecureIndexTts2.sslParameters().getEndpointIdentificationAlgorithm()).isBlank();
        assertThat(insecureComfyUi.sslParameters().getEndpointIdentificationAlgorithm()).isBlank();
    }

    private static HttpClient httpClient(Object providerClient) {
        return (HttpClient) ReflectionTestUtils.getField(providerClient, "httpClient");
    }
}
