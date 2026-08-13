package org.dromara.aivideo.identity;

import org.dromara.common.satoken.core.dao.PlusSaTokenDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证创作端 app Sa-Token 键不使用跨节点不可见的本地缓存。
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppSaTokenDaoNamespaceIT {

    private AppSessionIntegrationTestFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AppSessionIntegrationTestFixture.create();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fixture != null) {
            try {
                fixture.close();
            } finally {
                fixture = null;
            }
        }
    }

    @Test
    void bypassesLocalCacheForAppKeysButKeepsDefaultLoginNamespaceBehavior() {
        PlusSaTokenDao dao = new PlusSaTokenDao(AppSessionIntegrationRuntime.environment().redisKeyPrefix());
        String suffix = UUID.randomUUID().toString();
        String appKey = "Authorization:app:token:peer-cache-" + suffix;
        String systemKey = "Authorization:login:token:peer-cache-" + suffix;

        dao.set(appKey, "app-session", 60);
        assertThat(dao.get(appKey)).isEqualTo("app-session");
        fixture.deleteRedisKeyAsAnotherNode(appKey);
        assertThat(dao.get(appKey)).isNull();

        String appSearchPrefix = "Authorization:app:token-session:peer-cache-" + suffix + ':';
        String appSearchKey = appSearchPrefix + "desktop";
        dao.set(appSearchKey, "app-search-session", 60);
        assertThat(dao.searchData(appSearchPrefix, "", 0, 10, false)).contains(appSearchKey);
        fixture.deleteRedisKeyAsAnotherNode(appSearchKey);
        assertThat(dao.searchData(appSearchPrefix, "", 0, 10, false)).isEmpty();

        dao.set(systemKey, "system-session", 60);
        assertThat(dao.get(systemKey)).isEqualTo("system-session");
        fixture.deleteRedisKeyAsAnotherNode(systemKey);
        assertThat(dao.get(systemKey)).isEqualTo("system-session");
    }
}
