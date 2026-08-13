package org.dromara.aivideo.user.quota.domain.vo;

import org.dromara.aivideo.quota.dto.QuotaAccountSnapshotDTO;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创作端个人积分账户响应。
 */
public record QuotaAccountVo(
    String quotaUnit,
    String availableBalance,
    String lockedBalance,
    String usedBalance,
    String totalBalance
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 从核心服务快照创建端侧响应。
     *
     * @param snapshot 积分账户快照
     * @return 端侧响应
     */
    public static QuotaAccountVo from(QuotaAccountSnapshotDTO snapshot) {
        return new QuotaAccountVo(snapshot.quotaUnit(), snapshot.availableBalance(), snapshot.lockedBalance(),
            snapshot.usedBalance(), snapshot.totalBalance());
    }
}
