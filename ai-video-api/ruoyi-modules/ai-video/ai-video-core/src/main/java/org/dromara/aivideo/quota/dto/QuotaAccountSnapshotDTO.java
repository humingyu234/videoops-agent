package org.dromara.aivideo.quota.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 个人积分账户只读快照。
 */
public record QuotaAccountSnapshotDTO(
    String quotaUnit,
    String availableBalance,
    String lockedBalance,
    String usedBalance,
    String totalBalance
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
