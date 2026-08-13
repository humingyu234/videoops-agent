package org.dromara.common.satoken.core.dao;

import cn.dev33.satoken.dao.auto.SaTokenDaoBySessionFollowObject;
import cn.dev33.satoken.util.SaFoxUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.redis.utils.RedisUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sa-Token持久层接口(使用框架自带RedisUtils实现 协议统一)
 * <p>
 * 采用 caffeine + redis 多级缓存 优化并发查询效率
 * <p>
 * SaTokenDaoBySessionFollowObject 是 SaTokenDao 子集简化了session方法处理
 *
 * @author Lion Li
 */
public class PlusSaTokenDao implements SaTokenDaoBySessionFollowObject {

    /**
     * 创作端 app 登录类型的完整 Sa-Token Redis 键前缀。
     *
     * <p>app 会话撤销必须跨节点立即生效，不能复用仅在当前 JVM 可见的 Caffeine 本地缓存。</p>
     */
    private static final String APP_NAMESPACE_KEY_PREFIX = "Authorization:app:";

    private static final Cache<String, Object> CAFFEINE = Caffeine.newBuilder()
        // 设置最后一次写入或访问后经过固定时间过期
        .expireAfterWrite(5, TimeUnit.SECONDS)
        // 初始的缓存空间大小
        .initialCapacity(100)
        // 缓存的最大条数
        .maximumSize(1000)
        .build();

    /**
     * 物理 Redis 键前缀。生产默认空值，受控集成测试可使用每次运行独立前缀。
     */
    private final String redisKeyPrefix;

    /**
     * 创建保持既有 Redis 键名的 Sa-Token 存储。
     */
    public PlusSaTokenDao() {
        this("");
    }

    /**
     * 创建使用指定物理 Redis 键前缀的 Sa-Token 存储。
     *
     * <p>逻辑键、登录类型和 HTTP 令牌名不受影响；仅 Redis 中的实际键名增加此前缀。</p>
     *
     * @param redisKeyPrefix 物理 Redis 键前缀，可为空
     */
    public PlusSaTokenDao(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix == null ? "" : redisKeyPrefix;
    }

    /**
     * 获取Value，如无返空
     */
    @Override
    public String get(String key) {
        return getCacheValue(key);
    }

    /**
     * 写入Value，并设定存活时间 (单位: 秒)
     */
    @Override
    public void set(String key, String value, long timeout) {
        writeValue(key, value, timeout);
    }

    /**
     * 修修改指定key-value键值对 (过期时间不变)
     */
    @Override
    public void update(String key, String value) {
        String redisKey = storageKey(key);
        if (RedisUtils.hasKey(redisKey)) {
            RedisUtils.setCacheObject(redisKey, value, true);
            invalidate(key);
        }
    }

    /**
     * 删除Value
     */
    @Override
    public void delete(String key) {
        if (RedisUtils.deleteObject(storageKey(key))) {
            invalidate(key);
        }
    }

    /**
     * 获取Value的剩余存活时间 (单位: 秒)
     */
    @Override
    public long getTimeout(String key) {
        return toTimeoutSeconds(RedisUtils.getTimeToLive(storageKey(key)));
    }

    /**
     * 修改Value的剩余存活时间 (单位: 秒)
     */
    @Override
    public void updateTimeout(String key, long timeout) {
        RedisUtils.expire(storageKey(key), Duration.ofSeconds(timeout));
    }


    /**
     * 获取Object，如无返空
     */
    @Override
    public Object getObject(String key) {
        return getCacheValue(key);
    }

    /**
     * 获取 Object (指定反序列化类型)，如无返空
     *
     * @param key 键名称
     * @return object
     */
    @Override
    public <T> T getObject(String key, Class<T> classType) {
        return classType.cast(getCacheValue(key));
    }

    /**
     * 写入Object，并设定存活时间 (单位: 秒)
     */
    @Override
    public void setObject(String key, Object object, long timeout) {
        writeValue(key, object, timeout);
    }

    /**
     * 更新Object (过期时间不变)
     */
    @Override
    public void updateObject(String key, Object object) {
        String redisKey = storageKey(key);
        if (RedisUtils.hasKey(redisKey)) {
            RedisUtils.setCacheObject(redisKey, object, true);
            invalidate(key);
        }
    }

    /**
     * 删除Object
     */
    @Override
    public void deleteObject(String key) {
        if (RedisUtils.deleteObject(storageKey(key))) {
            invalidate(key);
        }
    }

    /**
     * 获取Object的剩余存活时间 (单位: 秒)
     */
    @Override
    public long getObjectTimeout(String key) {
        return toTimeoutSeconds(RedisUtils.getTimeToLive(storageKey(key)));
    }

    /**
     * 修改Object的剩余存活时间 (单位: 秒)
     */
    @Override
    public void updateObjectTimeout(String key, long timeout) {
        RedisUtils.expire(storageKey(key), Duration.ofSeconds(timeout));
    }

    /**
     * 搜索数据
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        String pattern = prefix + "*" + keyword + "*";
        if (isAppNamespaceKey(prefix)) {
            return searchRedisKeys(storageKey(pattern), start, size, sortType);
        }
        String cacheKey = pattern + start + StringUtils.COLON + size + StringUtils.COLON + sortType;
        return (List<String>) CAFFEINE.get(cacheKey,
            key -> searchRedisKeys(storageKey(pattern), start, size, sortType));
    }

    /**
     * 从缓存读取对象。
     *
     * @param key 缓存键
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    private <T> T getCacheValue(String key) {
        String redisKey = storageKey(key);
        if (isAppNamespaceKey(key)) {
            return (T) RedisUtils.getCacheObject(redisKey);
        }
        return (T) CAFFEINE.get(key, ignored -> RedisUtils.getCacheObject(redisKey));
    }

    /**
     * 从 Redis 读取匹配键并按 Sa-Token 约定分页排序。
     *
     * @param pattern Redis 键匹配模式
     * @param start 起始下标
     * @param size 最大数量
     * @param sortType 是否按倒序排序
     * @return 匹配的键列表
     */
    private List<String> searchRedisKeys(String redisPattern, int start, int size, boolean sortType) {
        Collection<String> keys = RedisUtils.keys(redisPattern);
        List<String> list = new ArrayList<>(keys.size());
        for (String key : keys) {
            list.add(logicalKey(key));
        }
        return SaFoxUtil.searchList(list, start, size, sortType);
    }

    /**
     * 判断键是否属于必须跨节点即时读取的创作端 app Sa-Token 命名空间。
     *
     * @param key Redis 键或搜索前缀
     * @return 属于 app 命名空间时返回 true
     */
    private boolean isAppNamespaceKey(String key) {
        return key != null && key.startsWith(APP_NAMESPACE_KEY_PREFIX);
    }

    /**
     * 写入缓存值并刷新本地缓存。
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时间
     */
    private void writeValue(String key, Object value, long timeout) {
        if (timeout == 0 || timeout <= NOT_VALUE_EXPIRE) {
            return;
        }
        String redisKey = storageKey(key);
        if (timeout == NEVER_EXPIRE) {
            RedisUtils.setCacheObject(redisKey, value);
        } else {
            RedisUtils.setCacheObject(redisKey, value, Duration.ofSeconds(timeout));
        }
        invalidate(key);
    }

    /**
     * 将 Sa-Token 逻辑键转换为实际 Redis 键。
     */
    private String storageKey(String logicalKey) {
        return redisKeyPrefix + logicalKey;
    }

    /**
     * 将当前实例扫描到的物理 Redis 键还原为 Sa-Token 逻辑键。
     */
    private String logicalKey(String redisKey) {
        if (redisKeyPrefix.isEmpty() || !redisKey.startsWith(redisKeyPrefix)) {
            return redisKey;
        }
        return redisKey.substring(redisKeyPrefix.length());
    }

    /**
     * 清除本地缓存。
     *
     * @param key 缓存键
     */
    private void invalidate(String key) {
        CAFFEINE.invalidate(key);
    }

    /**
     * 将 Redis TTL 转为秒。
     *
     * @param timeoutRedis Redis TTL 毫秒值
     * @return Sa-Token 需要的秒值
     */
    private long toTimeoutSeconds(long timeoutRedis) {
        // 加1的目的 解决sa-token使用秒 redis是毫秒导致1秒的精度问题 手动补偿
        return timeoutRedis < 0 ? timeoutRedis : timeoutRedis / 1000 + 1;
    }

}
