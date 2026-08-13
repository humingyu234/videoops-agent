package org.dromara.aivideo.workflow.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.domain.RunningHubAccount;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.mapper.RunningHubAccountMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowExecutionConfigMapper;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialWriteService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RunningHubAccountServiceImpl implements IRunningHubAccountService {

    private static final long CATALOG_TENANT_ID = 0L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RunningHubAccountMapper accountMapper;
    private final WorkflowExecutionConfigMapper executionConfigMapper;
    private final IWorkflowCredentialWriteService credentialWriteService;

    @Override
    public PageResult<RunningHubAccountDTOs.Summary> queryPage(RunningHubAccountDTOs.Query query,
                                                               PageQuery pageQuery) {
        RunningHubAccountDTOs.Query safeQuery = query == null ? new RunningHubAccountDTOs.Query(null, null) : query;
        Page<RunningHubAccount> page = accountMapper.selectAdminPage(buildPage(pageQuery), CATALOG_TENANT_ID, safeQuery);
        return PageResult.build(page.getRecords().stream().map(this::toSummary).toList(), page.getTotal());
    }

    @Override
    public RunningHubAccountDTOs.Detail queryDetail(String accountId) {
        return toDetail(requireAccount(parseId(accountId, "账号编号")));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(Long actorId, RunningHubAccountDTOs.Save command) {
        char[] apiKey = command == null ? null : command.apiKey();
        try {
            long actor = requireActorId(actorId);
            requireCommand(command);
            if (!hasText(apiKey)) {
                throw invalid("API Key 不能为空");
            }
            RunningHubAccount account = new RunningHubAccount();
            account.setTenantId(CATALOG_TENANT_ID);
            account.setAccountName(requiredText(command.accountName(), "账号名称"));
            account.setApiKeyCiphertext(credentialWriteService.encryptForStorage(
                WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, apiKey));
            account.setApiKeyMasked(mask(apiKey));
            account.setEnabled(false);
            account.setLastHealthStatus("unknown");
            account.setCredentialUpdatedAt(LocalDateTime.now());
            account.setRowRevision(0L);
            account.setDelFlag("0");
            account.setCreateBy(actor);
            account.setUpdateBy(actor);
            try {
                assertExactlyOne(accountMapper.insert(account), "RunningHub 账号创建失败");
            } catch (DuplicateKeyException exception) {
                throw new ServiceException("RunningHub 账号名称已存在",
                    WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT);
            }
            return Long.toString(account.getAccountId());
        } finally {
            clear(apiKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long actorId, String accountId, RunningHubAccountDTOs.Save command) {
        char[] apiKey = command == null ? null : command.apiKey();
        try {
            long actor = requireActorId(actorId);
            requireCommand(command);
            long id = parseId(accountId, "账号编号");
            RunningHubAccount current = requireAccount(id);
            long expectedRevision = requireExpectedRevision(command.expectedRevision());
            RunningHubAccount update = new RunningHubAccount();
            update.setAccountId(id);
            update.setTenantId(CATALOG_TENANT_ID);
            update.setAccountName(requiredText(command.accountName(), "账号名称"));
            update.setApiKeyCiphertext(current.getApiKeyCiphertext());
            update.setApiKeyMasked(current.getApiKeyMasked());
            update.setCredentialUpdatedAt(current.getCredentialUpdatedAt());
            if (hasText(apiKey)) {
                update.setApiKeyCiphertext(credentialWriteService.encryptForStorage(
                    WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, apiKey));
                update.setApiKeyMasked(mask(apiKey));
                update.setCredentialUpdatedAt(LocalDateTime.now());
            }
            update.setEnabled(Boolean.TRUE.equals(current.getEnabled()));
            update.setUpdateBy(actor);
            try {
                assertCas(accountMapper.updateContentCas(update, expectedRevision, actor));
            } catch (DuplicateKeyException exception) {
                throw new ServiceException("RunningHub 账号名称已存在",
                    WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT);
            }
        } finally {
            clear(apiKey);
        }
    }

    @Override
    public void enable(Long actorId, String accountId, long expectedRevision) {
        changeEnabled(requireActorId(actorId), accountId, expectedRevision, true);
    }

    @Override
    public void disable(Long actorId, String accountId, long expectedRevision) {
        changeEnabled(requireActorId(actorId), accountId, expectedRevision, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long actorId, String accountId, long expectedRevision) {
        long actor = requireActorId(actorId);
        long id = parseId(accountId, "账号编号");
        requireExpectedRevision(expectedRevision);
        RunningHubAccount account = accountMapper.selectCatalogAccountForUpdate(CATALOG_TENANT_ID, id);
        if (account == null) {
            throw invalid("RunningHub 账号不存在");
        }
        if (executionConfigMapper.countActiveReferences(CATALOG_TENANT_ID, id) > 0) {
            throw new ServiceException("RunningHub 账号仍被执行配置引用",
                WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT);
        }
        assertCas(accountMapper.logicalDelete(CATALOG_TENANT_ID, id, expectedRevision, actor));
    }

    @Override
    public List<RunningHubAccountDTOs.Option> queryOptions() {
        return accountMapper.selectEnabledOptions(CATALOG_TENANT_ID).stream()
            .map(account -> new RunningHubAccountDTOs.Option(
                Long.toString(account.getAccountId()), account.getAccountName()))
            .toList();
    }

    @Override
    public RunningHubAccountDTOs.InspectionCredential queryInspectionCredential(String accountId) {
        RunningHubAccount account = requireAccount(parseId(accountId, "账号编号"));
        if (account.getApiKeyCiphertext() == null || account.getApiKeyCiphertext().isBlank()) {
            throw invalid("RunningHub 账号未配置 API Key");
        }
        return new RunningHubAccountDTOs.InspectionCredential(
            Long.toString(account.getAccountId()), account.getAccountName(), account.getApiKeyCiphertext());
    }

    private void changeEnabled(long actorId, String accountId, long expectedRevision, boolean enabled) {
        long id = parseId(accountId, "账号编号");
        requireAccount(id);
        assertCas(accountMapper.updateEnabledCas(CATALOG_TENANT_ID, id, expectedRevision, enabled, actorId));
    }

    private RunningHubAccount requireAccount(long accountId) {
        RunningHubAccount account = accountMapper.selectCatalogAccount(CATALOG_TENANT_ID, accountId);
        if (account == null) {
            throw invalid("RunningHub 账号不存在");
        }
        return account;
    }

    private RunningHubAccountDTOs.Summary toSummary(RunningHubAccount account) {
        return new RunningHubAccountDTOs.Summary(
            Long.toString(account.getAccountId()), account.getAccountName(), account.getApiKeyMasked(),
            account.getApiKeyCiphertext() != null, Boolean.TRUE.equals(account.getEnabled()),
            account.getLastHealthStatus(), account.getLastHealthTime(), account.getLastHealthSummary(),
            valueOrZero(account.getRowRevision()), account.getUpdateTime());
    }

    private RunningHubAccountDTOs.Detail toDetail(RunningHubAccount account) {
        return new RunningHubAccountDTOs.Detail(
            Long.toString(account.getAccountId()), account.getAccountName(), account.getApiKeyMasked(),
            account.getApiKeyCiphertext() != null, Boolean.TRUE.equals(account.getEnabled()),
            account.getLastHealthStatus(), account.getLastHealthTime(), account.getLastHealthSummary(),
            account.getCredentialUpdatedAt(), valueOrZero(account.getRowRevision()), account.getCreateTime(),
            account.getUpdateTime());
    }

    private Page<RunningHubAccount> buildPage(PageQuery query) {
        int pageNum = query == null || query.getPageNum() == null ? 1 : Math.max(query.getPageNum(), 1);
        int pageSize = query == null || query.getPageSize() == null ? DEFAULT_PAGE_SIZE : query.getPageSize();
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw invalid("分页大小无效");
        }
        return new Page<>(pageNum, pageSize);
    }

    private long parseId(String value, String name) {
        try {
            long id = Long.parseLong(requiredText(value, name));
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw invalid(name + "无效");
        }
    }

    private long requireExpectedRevision(Long revision) {
        if (revision == null || revision < 0) {
            throw invalid("修订号无效");
        }
        return revision;
    }

    private void requireCommand(RunningHubAccountDTOs.Save command) {
        if (command == null) {
            throw invalid("账号参数不能为空");
        }
    }

    private String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalid(name + "不能为空");
        }
        return value.trim();
    }

    private boolean isBlank(char[] value) {
        for (char character : value) {
            if (!Character.isWhitespace(character) && character != '\0') {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(char[] value) {
        return value != null && value.length > 0 && !isBlank(value);
    }

    private void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private long requireActorId(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw invalid("操作人编号无效");
        }
        return actorId;
    }

    private String mask(char[] apiKey) {
        int visible = Math.min(4, apiKey.length);
        if (visible == apiKey.length) {
            return "***";
        }
        return "***" + new String(apiKey, apiKey.length - visible, visible);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private void assertCas(int affected) {
        if (affected != 1) {
            throw new ServiceException("RunningHub 账号修订冲突", WorkflowErrorCodes.WORKFLOW_REVISION_CONFLICT);
        }
    }

    private void assertExactlyOne(int affected, String message) {
        if (affected != 1) {
            throw invalid(message);
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, WorkflowErrorCodes.WORKFLOW_CONFIGURATION_INVALID);
    }
}
