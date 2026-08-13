package org.dromara.aivideo.quota.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.quota.constant.QuotaErrorCodes;
import org.dromara.aivideo.quota.domain.AvQuotaAccount;
import org.dromara.aivideo.quota.dto.QuotaAccountSnapshotDTO;
import org.dromara.aivideo.quota.mapper.QuotaAccountMapper;
import org.dromara.aivideo.quota.service.IQuotaAccountService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * 个人积分账户只读查询实现。
 */
@RequiredArgsConstructor
@Service
public class QuotaAccountServiceImpl implements IQuotaAccountService {

    private static final String PERSONAL_SUBJECT_TYPE = "app_user";
    private static final String AI_TEXT_CREDIT = "ai_text_credit";

    private final QuotaAccountMapper quotaAccountMapper;
    private final AppUserMapper appUserMapper;

    @Override
    public QuotaAccountSnapshotDTO queryPersonalAccount(long appUserId) {
        AppUser user = appUserMapper.selectById(appUserId);
        if (user == null || user.getPersonalTenantId() == null || user.getPersonalTenantId() <= 0) {
            throw new ServiceException("创作端个人账户信息不存在");
        }

        LambdaQueryWrapper<AvQuotaAccount> query = QueryBuilder.lambda(AvQuotaAccount.class)
            .eq(AvQuotaAccount::getTenantId, user.getPersonalTenantId())
            .eq(AvQuotaAccount::getSubjectType, PERSONAL_SUBJECT_TYPE)
            .eq(AvQuotaAccount::getSubjectId, appUserId)
            .eq(AvQuotaAccount::getUnitCode, AI_TEXT_CREDIT)
            .build();
        AvQuotaAccount account = quotaAccountMapper.selectOne(query);
        if (account == null) {
            throw new ServiceException("个人积分账户不存在", QuotaErrorCodes.QUOTA_ACCOUNT_NOT_FOUND);
        }

        BigInteger available = requireBalance(account.getAvailableBalance());
        BigInteger locked = requireBalance(account.getLockedBalance());
        BigInteger used = requireBalance(account.getUsedBalance());
        return new QuotaAccountSnapshotDTO(
            AI_TEXT_CREDIT,
            available.toString(),
            locked.toString(),
            used.toString(),
            available.add(locked).toString());
    }

    private BigInteger requireBalance(Long balance) {
        if (balance == null || balance < 0) {
            throw new ServiceException("个人积分账户余额异常");
        }
        return BigInteger.valueOf(balance);
    }
}
