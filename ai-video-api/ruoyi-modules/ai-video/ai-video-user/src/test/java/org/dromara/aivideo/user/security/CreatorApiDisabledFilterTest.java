package org.dromara.aivideo.user.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures an explicit app-token disable cannot leave /api/** exposed after it is excluded from sys security.
 */
@Tag("dev")
class CreatorApiDisabledFilterTest {

    @Test
    void rejectsCreatorApiInsteadOfAllowingItWithoutAnAppSecurityChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/creation/drafts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> chainCalled.set(true);

        new CreatorApiDisabledFilter("false", getClass().getClassLoader()).doFilter(request, response, chain);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":46130");
    }

    @Test
    void rejectsCreatorApiWhenTheSecurityFlagIsMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/creation/drafts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> chainCalled.set(true);

        new CreatorApiDisabledFilter(" true ", getClass().getClassLoader()).doFilter(request, response, chain);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void permitsCreatorApiOnlyWhenTheSecurityFlagAndCreatorMarkerAreBothPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/creation/drafts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> chainCalled.set(true);

        new CreatorApiDisabledFilter("true", classLoaderWithCreatorMarker()).doFilter(request, response, chain);

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMatrixAndPercentEncodedCreatorApiPathsWhenAppSecurityIsDisabled() throws Exception {
        for (String path : Stream.of("/api;v=1/creation/drafts", "/%61pi/creation/drafts").toList()) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainCalled = new AtomicBoolean();
            FilterChain chain = (ignoredRequest, ignoredResponse) -> chainCalled.set(true);

            new CreatorApiDisabledFilter("false", getClass().getClassLoader()).doFilter(request, response, chain);

            assertThat(chainCalled).as(path).isFalse();
            assertThat(response.getStatus()).as(path).isEqualTo(503);
        }
    }

    private static ClassLoader classLoaderWithCreatorMarker() {
        return new ClassLoader(CreatorApiDisabledFilterTest.class.getClassLoader()) {
            @Override
            public URL getResource(String name) {
                if ("META-INF/aivideo-creator-runtime.marker".equals(name)) {
                    return markerResource();
                }
                return super.getResource(name);
            }
        };
    }

    private static URL markerResource() {
        try {
            return URI.create("file:/aivideo-creator-runtime.marker").toURL();
        } catch (MalformedURLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
