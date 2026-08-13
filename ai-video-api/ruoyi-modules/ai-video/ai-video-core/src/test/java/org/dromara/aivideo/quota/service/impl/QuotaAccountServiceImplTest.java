package org.dromara.aivideo.quota.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.quota.domain.AvQuotaAccount;
import org.dromara.aivideo.quota.dto.QuotaAccountSnapshotDTO;
import org.dromara.aivideo.quota.mapper.QuotaAccountMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class QuotaAccountServiceImplTest {

    @Test
    void returnsExactStringBalancesForTheCurrentPersonalAccount() {
        initializeMetadata();
        QuotaAccountMapper mapper = mock(QuotaAccountMapper.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectById(1001L)).thenReturn(personalUser());
        AvQuotaAccount account = new AvQuotaAccount();
        account.setAvailableBalance(Long.MAX_VALUE);
        account.setLockedBalance(12L);
        account.setUsedBalance(34L);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(account);

        QuotaAccountSnapshotDTO result = new QuotaAccountServiceImpl(mapper, userMapper)
            .queryPersonalAccount(1001L);

        assertThat(result.quotaUnit()).isEqualTo("ai_text_credit");
        assertThat(result.availableBalance()).isEqualTo("9223372036854775807");
        assertThat(result.lockedBalance()).isEqualTo("12");
        assertThat(result.usedBalance()).isEqualTo("34");
        assertThat(result.totalBalance()).isEqualTo("9223372036854775819");

        ArgumentCaptor<Wrapper<AvQuotaAccount>> query = wrapperCaptor();
        verify(mapper).selectOne(query.capture());
        assertThat(query.getValue()).isInstanceOfSatisfying(LambdaQueryWrapper.class, wrapper -> {
            assertThat(wrapper.getSqlSegment()).contains("tenant_id", "subject_type", "subject_id", "unit_code");
            assertThat(wrapper.getParamNameValuePairs()).containsValues(2001L, "app_user", 1001L,
                "ai_text_credit");
        });
        verifyNoMoreInteractions(mapper);
        verify(userMapper).selectById(1001L);
        verifyNoMoreInteractions(userMapper);
    }

    @Test
    void failsWithoutCreatingAFallbackAccountWhenThePersonalAccountDoesNotExist() {
        initializeMetadata();
        QuotaAccountMapper mapper = mock(QuotaAccountMapper.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectById(1001L)).thenReturn(personalUser());
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> new QuotaAccountServiceImpl(mapper, userMapper).queryPersonalAccount(1001L))
            .isInstanceOfSatisfying(ServiceException.class, exception -> {
                assertThat(exception.getCode()).isEqualTo(46135);
                assertThat(exception.getMessage()).isEqualTo("个人积分账户不存在");
            });

        verify(mapper).selectOne(any(Wrapper.class));
        verifyNoMoreInteractions(mapper);
        verify(userMapper).selectById(1001L);
        verifyNoMoreInteractions(userMapper);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Wrapper<AvQuotaAccount>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private static void initializeMetadata() {
        if (TableInfoHelper.getTableInfo(AvQuotaAccount.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AvQuotaAccount.class);
        }
    }

    private static AppUser personalUser() {
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setPersonalTenantId(2001L);
        return user;
    }
}
