package org.dromara.aivideo.workflow.service.impl;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.domain.RunningHubAccount;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.mapper.RunningHubAccountMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowExecutionConfigMapper;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialWriteService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class RunningHubAccountServiceImplTest {

    private static final long ACTOR_ID = 41L;

    @Test
    void createEncryptsRequiredKeyStartsDisabledWritesActorAndZerosInput() {
        Harness harness = harness();
        when(harness.cipher.encryptForStorage(eq(WorkflowCredentialPurpose.RUNNINGHUB_API_KEY), any()))
            .thenReturn("v1:ciphertext");
        when(harness.accountMapper.insert(any(RunningHubAccount.class))).thenAnswer(invocation -> {
            RunningHubAccount account = invocation.getArgument(0);
            account.setAccountId(901L);
            return 1;
        });
        char[] apiKey = "secret-api-key".toCharArray();

        String id = harness.service.create(ACTOR_ID, new RunningHubAccountDTOs.Save("Primary", apiKey, null));

        assertThat(id).isEqualTo("901");
        assertThat(apiKey).containsOnly('\0');
        ArgumentCaptor<RunningHubAccount> inserted = ArgumentCaptor.forClass(RunningHubAccount.class);
        verify(harness.accountMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getTenantId()).isZero();
        assertThat(inserted.getValue().getEnabled()).isFalse();
        assertThat(inserted.getValue().getCreateBy()).isEqualTo(ACTOR_ID);
        assertThat(inserted.getValue().getUpdateBy()).isEqualTo(ACTOR_ID);
        assertThat(inserted.getValue().getApiKeyCiphertext()).isEqualTo("v1:ciphertext");
        assertThat(inserted.getValue().getApiKeyMasked()).endsWith("-key").doesNotContain("secret-api");
        assertThat(inserted.getValue().toString()).doesNotContain("v1:ciphertext");
        verify(harness.cipher).encryptForStorage(eq(WorkflowCredentialPurpose.RUNNINGHUB_API_KEY), any());
    }

    @Test
    void duplicateAccountNameMapsToStableConflictCode() {
        Harness harness = harness();
        when(harness.cipher.encryptForStorage(eq(WorkflowCredentialPurpose.RUNNINGHUB_API_KEY), any()))
            .thenReturn("v1:ciphertext");
        when(harness.accountMapper.insert(any(RunningHubAccount.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> harness.service.create(
            ACTOR_ID, new RunningHubAccountDTOs.Save("Primary", "secret".toCharArray(), null)))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT));
    }

    @Test
    void updateWithEmptyKeyPreservesCiphertextEnabledStatusAndUsesActorCas() {
        Harness harness = harness();
        RunningHubAccount current = account(901L, true, 7L);
        current.setApiKeyCiphertext("v1:old-ciphertext");
        current.setApiKeyMasked("***-key");
        when(harness.accountMapper.selectCatalogAccount(0L, 901L)).thenReturn(current);
        when(harness.accountMapper.updateContentCas(any(), eq(7L), eq(ACTOR_ID))).thenReturn(1);

        harness.service.update(ACTOR_ID, "901", new RunningHubAccountDTOs.Save("Renamed", new char[0], 7L));

        ArgumentCaptor<RunningHubAccount> update = ArgumentCaptor.forClass(RunningHubAccount.class);
        verify(harness.accountMapper).updateContentCas(update.capture(), eq(7L), eq(ACTOR_ID));
        assertThat(update.getValue().getApiKeyCiphertext()).isEqualTo("v1:old-ciphertext");
        assertThat(update.getValue().getEnabled()).isTrue();
        assertThat(update.getValue().getUpdateBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    void allAccountWritesRejectMissingOrNonPositiveActor() {
        Harness harness = harness();
        assertInvalidActor(() -> harness.service.create(null, null));
        assertInvalidActor(() -> harness.service.update(0L, "901", null));
        assertInvalidActor(() -> harness.service.enable(-1L, "901", 1L));
        assertInvalidActor(() -> harness.service.disable(null, "901", 1L));
        assertInvalidActor(() -> harness.service.delete(0L, "901", 1L));
    }

    @Test
    void staleEnableAndDisableRevisionsAreRejected() {
        Harness enable = harness();
        when(enable.accountMapper.selectCatalogAccount(0L, 901L)).thenReturn(account(901L, false, 7L));
        when(enable.accountMapper.updateEnabledCas(0L, 901L, 7L, true, ACTOR_ID)).thenReturn(0);
        assertRevisionConflict(() -> enable.service.enable(ACTOR_ID, "901", 7L));

        Harness disable = harness();
        when(disable.accountMapper.selectCatalogAccount(0L, 901L)).thenReturn(account(901L, true, 7L));
        when(disable.accountMapper.updateEnabledCas(0L, 901L, 7L, false, ACTOR_ID)).thenReturn(0);
        assertRevisionConflict(() -> disable.service.disable(ACTOR_ID, "901", 7L));
    }

    @Test
    void deleteLocksAccountBeforeReferenceCheckAndUsesActorRevisionCas() {
        Harness harness = harness();
        when(harness.accountMapper.selectCatalogAccountForUpdate(0L, 901L))
            .thenReturn(account(901L, false, 7L));
        when(harness.executionConfigMapper.countActiveReferences(0L, 901L)).thenReturn(0L);
        when(harness.accountMapper.logicalDelete(0L, 901L, 7L, ACTOR_ID)).thenReturn(1);

        harness.service.delete(ACTOR_ID, "901", 7L);

        InOrder order = inOrder(harness.accountMapper, harness.executionConfigMapper);
        order.verify(harness.accountMapper).selectCatalogAccountForUpdate(0L, 901L);
        order.verify(harness.executionConfigMapper).countActiveReferences(0L, 901L);
        order.verify(harness.accountMapper).logicalDelete(0L, 901L, 7L, ACTOR_ID);
    }

    @Test
    void referencedAccountCannotBeDeletedAfterLock() {
        Harness harness = harness();
        when(harness.accountMapper.selectCatalogAccountForUpdate(0L, 901L))
            .thenReturn(account(901L, false, 7L));
        when(harness.executionConfigMapper.countActiveReferences(0L, 901L)).thenReturn(1L);

        assertThatThrownBy(() -> harness.service.delete(ACTOR_ID, "901", 7L))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_REFERENCE_CONFLICT));
    }

    @Test
    void staleAccountDeleteRevisionIsRejected() {
        Harness harness = harness();
        when(harness.accountMapper.selectCatalogAccountForUpdate(0L, 901L))
            .thenReturn(account(901L, false, 7L));
        when(harness.accountMapper.logicalDelete(0L, 901L, 7L, ACTOR_ID)).thenReturn(0);

        assertRevisionConflict(() -> harness.service.delete(ACTOR_ID, "901", 7L));
    }

    @Test
    void mapperStatementsProvideRowLockActorAuditAndRevisionCas() throws Exception {
        String lockSql = String.join(" ", RunningHubAccountMapper.class
            .getMethod("selectCatalogAccountForUpdate", long.class, long.class)
            .getAnnotation(Select.class).value());
        String updateSql = String.join(" ", RunningHubAccountMapper.class
            .getMethod("updateContentCas", RunningHubAccount.class, long.class, long.class)
            .getAnnotation(Update.class).value());
        String deleteSql = String.join(" ", RunningHubAccountMapper.class
            .getMethod("logicalDelete", long.class, long.class, long.class, long.class)
            .getAnnotation(Update.class).value());

        assertThat(lockSql).containsIgnoringCase("FOR UPDATE");
        assertThat(updateSql).contains("update_by = #{actorId}", "row_revision = #{expectedRevision}");
        assertThat(deleteSql).contains("update_by = #{actorId}", "row_revision = #{expectedRevision}");
    }

    @Test
    void secretFieldsNeverExistInReadDtosOrCommandToString() {
        assertThat(RunningHubAccountDTOs.Summary.class.getRecordComponents())
            .extracting(component -> component.getName().toLowerCase())
            .noneMatch(name -> name.contains("cipher") || name.equals("apikey"));
        assertThat(RunningHubAccountDTOs.Detail.class.getRecordComponents())
            .extracting(component -> component.getName().toLowerCase())
            .noneMatch(name -> name.contains("cipher") || name.equals("apikey"));
        RunningHubAccountDTOs.Save command = new RunningHubAccountDTOs.Save(
            "Primary", "do-not-leak".toCharArray(), 1L);
        assertThat(command.toString()).doesNotContain("do-not-leak");
    }

    @Test
    void suppliesEncryptedCredentialOnlyToInternalInspectionBoundary() {
        Harness harness = harness();
        RunningHubAccount account = account(901L, true, 7L);
        account.setApiKeyCiphertext("v1:encrypted-secret");
        when(harness.accountMapper.selectCatalogAccount(0L, 901L)).thenReturn(account);

        RunningHubAccountDTOs.InspectionCredential credential =
            harness.service.queryInspectionCredential("901");

        assertThat(credential.accountId()).isEqualTo("901");
        assertThat(credential.accountName()).isEqualTo("Primary");
        assertThat(credential.encryptedApiKey()).isEqualTo("v1:encrypted-secret");
        assertThat(credential.toString()).doesNotContain("v1:encrypted-secret");
    }

    private static Harness harness() {
        RunningHubAccountMapper accountMapper = mock(RunningHubAccountMapper.class);
        WorkflowExecutionConfigMapper configMapper = mock(WorkflowExecutionConfigMapper.class);
        IWorkflowCredentialWriteService cipher = mock(IWorkflowCredentialWriteService.class);
        return new Harness(new RunningHubAccountServiceImpl(accountMapper, configMapper, cipher),
            accountMapper, configMapper, cipher);
    }

    private static RunningHubAccount account(long id, boolean enabled, long revision) {
        RunningHubAccount account = new RunningHubAccount();
        account.setAccountId(id);
        account.setTenantId(0L);
        account.setAccountName("Primary");
        account.setEnabled(enabled);
        account.setRowRevision(revision);
        account.setDelFlag("0");
        return account;
    }

    private static void assertInvalidActor(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_CONFIGURATION_INVALID));
    }

    private static void assertRevisionConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_REVISION_CONFLICT));
    }

    private record Harness(RunningHubAccountServiceImpl service, RunningHubAccountMapper accountMapper,
                           WorkflowExecutionConfigMapper executionConfigMapper,
                           IWorkflowCredentialWriteService cipher) {
    }
}
