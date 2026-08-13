package org.dromara.aivideo.quota.service;

import org.dromara.aivideo.quota.dto.QuotaAccountSnapshotDTO;

/**
 * 个人积分账户查询服务。
 */
public interface IQuotaAccountService {

    /**
     * 查询指定创作端用户自己的个人积分账户。
     *
     * @param appUserId 当前创作端用户编号
     * @return 个人积分账户快照
     */
    QuotaAccountSnapshotDTO queryPersonalAccount(long appUserId);
}
