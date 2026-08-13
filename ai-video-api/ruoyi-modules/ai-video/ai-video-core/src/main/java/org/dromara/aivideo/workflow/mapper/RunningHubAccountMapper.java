package org.dromara.aivideo.workflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.aivideo.workflow.domain.RunningHubAccount;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

public interface RunningHubAccountMapper extends BaseMapperPlus<RunningHubAccount, RunningHubAccount> {

    Page<RunningHubAccount> selectAdminPage(Page<RunningHubAccount> page,
                                            @Param("tenantId") long tenantId,
                                            @Param("query") RunningHubAccountDTOs.Query query);

    @Select("""
        SELECT account_id, tenant_id, account_name, api_key_ciphertext, api_key_masked, enabled,
               last_health_status, last_health_time, last_health_summary, credential_updated_at,
               row_revision, create_by, update_by, del_flag, create_time, update_time
        FROM av_runninghub_account
        WHERE tenant_id = #{tenantId} AND account_id = #{accountId} AND del_flag = '0'
        """)
    RunningHubAccount selectCatalogAccount(@Param("tenantId") long tenantId, @Param("accountId") long accountId);

    @Select("""
        SELECT account_id, tenant_id, account_name, api_key_ciphertext, api_key_masked, enabled,
               last_health_status, last_health_time, last_health_summary, credential_updated_at,
               row_revision, create_by, update_by, del_flag, create_time, update_time
        FROM av_runninghub_account
        WHERE tenant_id = #{tenantId} AND account_id = #{accountId} AND del_flag = '0'
        FOR UPDATE
        """)
    RunningHubAccount selectCatalogAccountForUpdate(@Param("tenantId") long tenantId,
                                                     @Param("accountId") long accountId);

    @Select("""
        SELECT account_id, tenant_id, account_name, api_key_ciphertext, api_key_masked, enabled,
               last_health_status, last_health_time, last_health_summary, credential_updated_at,
               row_revision, create_by, update_by, del_flag, create_time, update_time
        FROM av_runninghub_account
        WHERE tenant_id = #{tenantId} AND enabled = 1 AND del_flag = '0'
        ORDER BY account_name ASC, account_id ASC
        """)
    List<RunningHubAccount> selectEnabledOptions(@Param("tenantId") long tenantId);

    @Update("""
        UPDATE av_runninghub_account
        SET account_name = #{account.accountName}, api_key_ciphertext = #{account.apiKeyCiphertext},
            api_key_masked = #{account.apiKeyMasked}, enabled = #{account.enabled},
            credential_updated_at = #{account.credentialUpdatedAt}, update_by = #{actorId}, update_time = NOW(),
            row_revision = row_revision + 1
        WHERE tenant_id = #{account.tenantId} AND account_id = #{account.accountId}
          AND del_flag = '0' AND row_revision = #{expectedRevision}
        """)
    int updateContentCas(@Param("account") RunningHubAccount account,
                         @Param("expectedRevision") long expectedRevision,
                         @Param("actorId") long actorId);

    @Update("""
        UPDATE av_runninghub_account
        SET enabled = #{enabled}, update_by = #{actorId}, update_time = NOW(), row_revision = row_revision + 1
        WHERE tenant_id = #{tenantId} AND account_id = #{accountId}
          AND del_flag = '0' AND row_revision = #{expectedRevision}
        """)
    int updateEnabledCas(@Param("tenantId") long tenantId, @Param("accountId") long accountId,
                         @Param("expectedRevision") long expectedRevision, @Param("enabled") boolean enabled,
                         @Param("actorId") long actorId);

    @Update("""
        UPDATE av_runninghub_account
        SET del_flag = '1', update_by = #{actorId}, update_time = NOW(), row_revision = row_revision + 1
        WHERE tenant_id = #{tenantId} AND account_id = #{accountId} AND del_flag = '0'
          AND row_revision = #{expectedRevision}
        """)
    int logicalDelete(@Param("tenantId") long tenantId, @Param("accountId") long accountId,
                      @Param("expectedRevision") long expectedRevision, @Param("actorId") long actorId);
}
