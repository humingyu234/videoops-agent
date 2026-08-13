import { DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, PoweroffOutlined } from '@ant-design/icons';
import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Alert, App, Button, Result, Space, Tag } from 'antd';
import { useRef, useState } from 'react';
import type { RunningHubAccountSummary } from '@/api/aivideo/runninghub-account/types';
import type {
  WorkflowExecutionConfig,
  WorkflowExecutionConfigSave,
  WorkflowTemplateSummary,
  WorkflowTemplateTableParams
} from '@/api/aivideo/workflow-template/types';
import { pageRunningHubAccounts } from '@/api/aivideo/runninghub-account';
import { getDicts } from '@/api/system/dict/data';
import {
  createWorkflowTemplate,
  deleteWorkflowTemplate,
  disableWorkflowTemplate,
  enableWorkflowTemplate,
  getWorkflowExecutionConfig,
  getWorkflowTemplate,
  pageWorkflowTemplates,
  saveWorkflowExecutionConfig,
  updateWorkflowTemplate
} from '@/api/aivideo/workflow-template';
import RowActions from '@/components/common/RowActions';
import { useUserStore } from '@/stores/userStore';
import { hasPermi } from '@/utils/permission';
import { dictOptions } from '@/utils/dict';
import {
  buildWorkflowTemplateFormValues,
  toWorkflowTemplatePayloads,
  WorkflowTemplateFormFieldError,
  type WorkflowTemplateFormValues
} from './components/templateFormModel';
import WorkflowTemplateEditor from './components/WorkflowTemplateEditor';

const accountOptionPageSize = 100;
const discoveryCategoryDictType = 'aivideo_discovery_category';

function templateStatusTag(row: WorkflowTemplateSummary) {
  if (row.status === 'enabled') return <Tag color="success">已启用</Tag>;
  if (row.status === 'disabled') return <Tag>已停用</Tag>;
  if (row.status === 'pending_test') return <Tag color="warning">待验证（历史）</Tag>;
  return <Tag color="processing">草稿</Tag>;
}

export default function WorkflowTemplatePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const userInfo = useUserStore(state => state.userInfo);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editorReadonly, setEditorReadonly] = useState(false);
  const [initialValues, setInitialValues] = useState<WorkflowTemplateFormValues>();
  const [accounts, setAccounts] = useState<RunningHubAccountSummary[]>([]);
  const [categoryOptions, setCategoryOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [selectedAccountName, setSelectedAccountName] = useState<string | null>();
  const [submitting, setSubmitting] = useState(false);
  const [opening, setOpening] = useState(false);
  const [openingId, setOpeningId] = useState<string>();
  const [configError, setConfigError] = useState<string>();
  const [pendingConfigTemplateId, setPendingConfigTemplateId] = useState<string>();
  const [pendingConfigPayload, setPendingConfigPayload] = useState<WorkflowExecutionConfigSave>();

  const canQuery = hasPermi(userInfo, ['aivideo:workflow-template:query']);
  const canQueryAccounts = hasPermi(userInfo, ['aivideo:runninghub-account:query']);
  const canAdd = hasPermi(userInfo, ['aivideo:workflow-template:add']);
  const canEdit = hasPermi(userInfo, ['aivideo:workflow-template:edit']);
  const canRemove = hasPermi(userInfo, ['aivideo:workflow-template:remove']);
  const canEnable = hasPermi(userInfo, ['aivideo:workflow-template:enable']);
  const canDisable = hasPermi(userInfo, ['aivideo:workflow-template:disable']);

  const loadAccountOptions = async () => {
    const result = await pageRunningHubAccounts({ current: 1, enabled: true, pageSize: accountOptionPageSize });
    return result.data;
  };

  const loadCategoryOptions = async () => {
    const response = await getDicts(discoveryCategoryDictType);
    return dictOptions(response.data).filter(option => /^[1-9]\d{0,18}$/.test(option.value));
  };

  const resetEditorState = () => {
    setEditorOpen(false);
    setEditorReadonly(false);
    setInitialValues(undefined);
    setAccounts([]);
    setCategoryOptions([]);
    setSelectedAccountName(undefined);
    setConfigError(undefined);
    setPendingConfigTemplateId(undefined);
    setPendingConfigPayload(undefined);
  };

  const closeEditor = () => {
    if (submitting) return;
    resetEditorState();
  };

  const openCreate = async () => {
    setOpening(true);
    try {
      const [nextAccounts, nextCategoryOptions] = await Promise.all([loadAccountOptions(), loadCategoryOptions()]);
      setAccounts(nextAccounts);
      setCategoryOptions(nextCategoryOptions);
      setSelectedAccountName(undefined);
      setEditorReadonly(false);
      setInitialValues(buildWorkflowTemplateFormValues());
      setConfigError(undefined);
      setPendingConfigTemplateId(undefined);
      setEditorOpen(true);
    } catch {
      // 请求层已提示错误，账号列表加载失败时不打开不可提交的表单。
    } finally {
      setOpening(false);
    }
  };

  const openEdit = async (row: WorkflowTemplateSummary) => {
    setOpeningId(row.templateId);
    try {
      const configPromise: Promise<WorkflowExecutionConfig | undefined> = row.executionConfigured
        ? getWorkflowExecutionConfig(row.templateId)
        : Promise.resolve(undefined);
      const [detail, config, nextAccounts, nextCategoryOptions] = await Promise.all([
        getWorkflowTemplate(row.templateId),
        configPromise,
        loadAccountOptions(),
        loadCategoryOptions()
      ]);
      setAccounts(nextAccounts);
      setCategoryOptions(nextCategoryOptions);
      setSelectedAccountName(row.accountName);
      setEditorReadonly(false);
      setInitialValues(buildWorkflowTemplateFormValues(detail, config));
      setConfigError(undefined);
      setPendingConfigTemplateId(undefined);
      setEditorOpen(true);
    } catch {
      // 请求层已提示错误，详情或配置未完整加载时不打开表单。
    } finally {
      setOpeningId(undefined);
    }
  };

  const openView = async (row: WorkflowTemplateSummary) => {
    setOpeningId(row.templateId);
    try {
      const configPromise: Promise<WorkflowExecutionConfig | undefined> = row.executionConfigured
        ? getWorkflowExecutionConfig(row.templateId)
        : Promise.resolve(undefined);
      const [detail, config, nextCategoryOptions] = await Promise.all([
        getWorkflowTemplate(row.templateId),
        configPromise,
        loadCategoryOptions()
      ]);
      setAccounts([]);
      setCategoryOptions(nextCategoryOptions);
      setSelectedAccountName(row.accountName);
      setEditorReadonly(true);
      setInitialValues(buildWorkflowTemplateFormValues(detail, config));
      setConfigError(undefined);
      setPendingConfigTemplateId(undefined);
      setEditorOpen(true);
    } catch {
      // 请求层已提示错误，详情或配置未完整加载时不打开只读抽屉。
    } finally {
      setOpeningId(undefined);
    }
  };

  const submitTemplate = async (values: WorkflowTemplateFormValues) => {
    if (editorReadonly) return false;
    setSubmitting(true);
    setConfigError(undefined);
    let payloads;
    try {
      payloads = toWorkflowTemplatePayloads(values);
    } catch (error) {
      setSubmitting(false);
      if (error instanceof WorkflowTemplateFormFieldError) throw error;
      return false;
    }

    let templateId = pendingConfigTemplateId || values.templateId;
    let baseSaved = Boolean(pendingConfigTemplateId);
    try {
      if (!pendingConfigTemplateId) {
        if (values.templateId) {
          if (typeof values.expectedTemplateRevision !== 'number') return false;
          await updateWorkflowTemplate(values.templateId, {
            ...payloads.template,
            expectedRevision: values.expectedTemplateRevision
          });
          templateId = values.templateId;
        } else {
          templateId = await createWorkflowTemplate(payloads.template);
        }
        baseSaved = true;
        setPendingConfigTemplateId(templateId);
        setPendingConfigPayload(payloads.config);
      }

      if (!templateId) return false;
      await saveWorkflowExecutionConfig(templateId, pendingConfigPayload || payloads.config);
      message.success(values.templateId ? '工作流模板已更新' : '工作流模板草稿和配置已保存');
      resetEditorState();
      actionRef.current?.reload();
      return true;
    } catch {
      if (baseSaved) {
        const errorMessage = '草稿已保存，配置保存失败';
        setConfigError(errorMessage);
        actionRef.current?.reload();
      }
      return false;
    } finally {
      setSubmitting(false);
    }
  };

  const retryPendingConfig = async () => {
    if (submitting || !pendingConfigTemplateId || !pendingConfigPayload) return;
    setSubmitting(true);
    setConfigError(undefined);
    try {
      await saveWorkflowExecutionConfig(pendingConfigTemplateId, pendingConfigPayload);
      message.success(initialValues?.templateId ? '工作流模板已更新' : '工作流模板草稿和配置已保存');
      resetEditorState();
      actionRef.current?.reload();
    } catch {
      setConfigError('草稿已保存，配置保存失败');
      actionRef.current?.reload();
    } finally {
      setSubmitting(false);
    }
  };

  const removeTemplate = async (row: WorkflowTemplateSummary) => {
    try {
      await deleteWorkflowTemplate(row.templateId, row.rowRevision);
      message.success('工作流模板已删除');
    } catch {
      // 请求层已提示错误，随后重新读取服务端事实。
    } finally {
      actionRef.current?.reload();
    }
  };

  const changeTemplateStatus = async (row: WorkflowTemplateSummary) => {
    try {
      if (row.status === 'enabled') {
        await disableWorkflowTemplate(row.templateId, row.rowRevision);
        message.success('工作流模板已停用');
      } else {
        await enableWorkflowTemplate(row.templateId, row.rowRevision);
        message.success('工作流模板已启用');
      }
    } catch {
      // 请求层已提示错误，随后重新读取服务端事实。
    } finally {
      actionRef.current?.reload();
    }
  };

  const columns: ProColumns<WorkflowTemplateSummary>[] = [
    { dataIndex: 'name', title: '模板名称', width: 180 },
    {
      dataIndex: 'channel',
      fieldProps: {
        options: [
          { label: '视频模板', value: 'video_template' },
          { label: '工作流灵感', value: 'workflow_inspiration' }
        ]
      },
      renderText: value => (value === 'video_template' ? '视频模板' : '工作流灵感'),
      title: '频道',
      valueType: 'select',
      width: 120
    },
    { dataIndex: 'categoryName', search: false, title: '分类', width: 120 },
    {
      dataIndex: 'status',
      fieldProps: {
        options: [
          { label: '草稿', value: 'draft' },
          { label: '待验证（历史）', value: 'pending_test' },
          { label: '已启用', value: 'enabled' },
          { label: '已停用', value: 'disabled' }
        ]
      },
      render: (_, row) => templateStatusTag(row),
      title: '模板状态',
      valueType: 'select',
      width: 120
    },
    {
      dataIndex: 'executionConfigured',
      render: (_, row) => (row.executionConfigured ? '已配置' : '未配置'),
      search: false,
      title: '执行配置',
      width: 100
    },
    {
      dataIndex: 'executionEnabled',
      render: (_, row) => (row.executionEnabled ? '已启用' : '已停用'),
      search: false,
      title: '配置状态',
      width: 100
    },
    { dataIndex: 'accountName', search: false, title: 'RunningHub 账号', width: 160 },
    {
      dataIndex: 'recommended',
      hideInTable: true,
      title: '是否推荐',
      valueType: 'select',
      valueEnum: { false: '否', true: '是' }
    },
    { dataIndex: 'categoryId', hideInTable: true, title: '分类编号' },
    { dataIndex: 'updateTime', search: false, title: '更新时间', valueType: 'dateTime', width: 170 },
    {
      fixed: 'right',
      search: false,
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, row) => (
        <RowActions
          actions={[
            canQuery && {
              disabled: openingId === row.templateId,
              icon: <EyeOutlined />,
              key: 'view',
              label: '查看',
              onClick: () => void openView(row)
            },
            canEdit &&
              canQueryAccounts && {
                disabled: openingId === row.templateId,
                icon: <EditOutlined />,
                key: 'edit',
                label: '修改',
                onClick: () => void openEdit(row)
              },
            row.status === 'enabled'
              ? canDisable && {
                  confirm: `确认停用工作流模板“${row.name}”吗？停用后用户端将不可见。`,
                  icon: <PoweroffOutlined />,
                  key: 'disable',
                  label: '停用',
                  onClick: () => void changeTemplateStatus(row)
                }
              : canEnable && {
                  confirm: `确认已人工验证模板与配置，并启用工作流模板“${row.name}”吗？`,
                  icon: <PoweroffOutlined />,
                  key: 'enable',
                  label: '启用',
                  onClick: () => void changeTemplateStatus(row)
                },
            canRemove && {
              confirm: `确认删除工作流模板“${row.name}”吗？删除后不可恢复。`,
              danger: true,
              icon: <DeleteOutlined />,
              key: 'remove',
              label: '删除',
              onClick: () => void removeTemplate(row)
            }
          ]}
        />
      )
    }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="工作流模板">
        <Result status="403" subTitle="当前运营端账号没有查看工作流模板的权限。" title="无权限访问" />
      </PageContainer>
    );
  }

  if (editorOpen) {
    return (
      <>
        {pendingConfigTemplateId && (
          <Alert
            action={
              <Space>
                <Button disabled={submitting} onClick={closeEditor}>
                  返回列表
                </Button>
                <Button
                  disabled={!pendingConfigPayload}
                  loading={submitting}
                  type="primary"
                  onClick={() => void retryPendingConfig()}
                >
                  重试保存配置
                </Button>
              </Space>
            }
            description="为避免模板表单与执行映射不一致，基础资料和参数已锁定。"
            showIcon
            title="模板草稿已保存，请重试执行配置"
            type="warning"
          />
        )}
        <WorkflowTemplateEditor
          accountName={selectedAccountName}
          accountOptions={accounts}
          categoryOptions={categoryOptions}
          errorMessage={configError}
          initialValues={initialValues}
          readonly={editorReadonly}
          submitting={submitting || Boolean(pendingConfigTemplateId)}
          onClose={closeEditor}
          onFinish={submitTemplate}
        />
      </>
    );
  }

  return (
    <PageContainer title="工作流模板">
      <ProTable<WorkflowTemplateSummary, WorkflowTemplateTableParams>
        actionRef={actionRef}
        columns={columns}
        pagination={{ defaultPageSize: 10, pageSizeOptions: [10, 20, 50], showSizeChanger: true }}
        request={params => pageWorkflowTemplates(params)}
        rowKey="templateId"
        search={{ labelWidth: 96 }}
        toolbar={{ title: '工作流模板列表' }}
        toolBarRender={() => [
          canAdd && canQueryAccounts && (
            <Button
              key="create"
              aria-label="新增模板"
              icon={<PlusOutlined />}
              loading={opening}
              type="primary"
              onClick={() => void openCreate()}
            >
              新增模板
            </Button>
          )
        ]}
      />
    </PageContainer>
  );
}
