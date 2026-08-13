import { DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, PoweroffOutlined } from '@ant-design/icons';
import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { App, Button, Result, Tag } from 'antd';
import { useRef, useState } from 'react';
import type {
  RunningHubAccountDetail,
  RunningHubAccountSummary,
  RunningHubAccountTableParams
} from '@/api/aivideo/runninghub-account/types';
import {
  createRunningHubAccount,
  deleteRunningHubAccount,
  disableRunningHubAccount,
  enableRunningHubAccount,
  getRunningHubAccount,
  pageRunningHubAccounts,
  updateRunningHubAccount
} from '@/api/aivideo/runninghub-account';
import RowActions from '@/components/common/RowActions';
import { useUserStore } from '@/stores/userStore';
import { hasPermi } from '@/utils/permission';
import {
  toCreateRunningHubAccountInput,
  toUpdateRunningHubAccountInput,
  type RunningHubAccountFormValues
} from './components/accountFormModel';
import RunningHubAccountFormModal from './components/RunningHubAccountFormModal';

export default function RunningHubAccountPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const userInfo = useUserStore(state => state.userInfo);
  const [formOpen, setFormOpen] = useState(false);
  const [formDetail, setFormDetail] = useState<RunningHubAccountDetail>();
  const [formReadonly, setFormReadonly] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [openingId, setOpeningId] = useState<string>();

  const canQuery = hasPermi(userInfo, ['aivideo:runninghub-account:query']);
  const canAdd = hasPermi(userInfo, ['aivideo:runninghub-account:add']);
  const canEdit = hasPermi(userInfo, ['aivideo:runninghub-account:edit']);
  const canRemove = hasPermi(userInfo, ['aivideo:runninghub-account:remove']);
  const canEnable = hasPermi(userInfo, ['aivideo:runninghub-account:enable']);
  const canDisable = hasPermi(userInfo, ['aivideo:runninghub-account:disable']);

  const closeForm = () => {
    if (submitting) return;
    setFormOpen(false);
    setFormDetail(undefined);
    setFormReadonly(false);
  };

  const openCreate = () => {
    setFormDetail(undefined);
    setFormReadonly(false);
    setFormOpen(true);
  };

  const openDetail = async (row: RunningHubAccountSummary, readonly: boolean) => {
    setOpeningId(row.accountId);
    try {
      const detail = await getRunningHubAccount(row.accountId);
      setFormDetail(detail);
      setFormReadonly(readonly);
      setFormOpen(true);
    } catch {
      // 集中请求层已提示错误，保持表单关闭且不回填任何秘密。
    } finally {
      setOpeningId(undefined);
    }
  };

  const openView = (row: RunningHubAccountSummary) => openDetail(row, true);
  const openEdit = (row: RunningHubAccountSummary) => openDetail(row, false);

  const submitAccount = async (values: RunningHubAccountFormValues) => {
    if (formReadonly) return false;
    setSubmitting(true);
    try {
      if (values.accountId) {
        const input = toUpdateRunningHubAccountInput(values);
        if (!input) return false;
        await updateRunningHubAccount(values.accountId, input);
        message.success('RunningHub 账号已更新');
      } else {
        const input = toCreateRunningHubAccountInput(values);
        if (!input) return false;
        await createRunningHubAccount(input);
        message.success('RunningHub 账号已创建');
      }
      setFormOpen(false);
      setFormDetail(undefined);
      actionRef.current?.reload();
      return true;
    } catch {
      return false;
    } finally {
      setSubmitting(false);
    }
  };

  const removeAccount = async (row: RunningHubAccountSummary) => {
    try {
      await deleteRunningHubAccount(row.accountId, row.rowRevision);
      message.success('RunningHub 账号已删除');
    } catch {
      // 请求层已提示错误，随后重新读取服务端事实。
    } finally {
      actionRef.current?.reload();
    }
  };

  const changeAccountStatus = async (row: RunningHubAccountSummary) => {
    try {
      if (row.enabled) {
        await disableRunningHubAccount(row.accountId, row.rowRevision);
        message.success('RunningHub 账号已停用');
      } else {
        await enableRunningHubAccount(row.accountId, row.rowRevision);
        message.success('RunningHub 账号已启用');
      }
    } catch {
      // 请求层已提示错误，随后重新读取服务端事实。
    } finally {
      actionRef.current?.reload();
    }
  };

  const columns: ProColumns<RunningHubAccountSummary>[] = [
    { dataIndex: 'accountName', title: '账号名称', width: 180 },
    {
      dataIndex: 'enabled',
      fieldProps: {
        options: [
          { label: '已启用', value: true },
          { label: '已停用', value: false }
        ]
      },
      render: (_, row) => <Tag color={row.enabled ? 'success' : 'default'}>{row.enabled ? '已启用' : '已停用'}</Tag>,
      title: '状态',
      valueType: 'select',
      width: 100
    },
    {
      dataIndex: 'hasApiKey',
      render: (_, row) => (row.hasApiKey ? row.apiKeyMasked || '已配置' : '未配置'),
      search: false,
      title: 'API Key',
      width: 160
    },
    {
      dataIndex: 'lastHealthStatus',
      renderText: value => value || '未检测',
      search: false,
      title: '最近健康状态',
      width: 140
    },
    { dataIndex: 'lastHealthTime', search: false, title: '最近健康时间', valueType: 'dateTime', width: 170 },
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
              disabled: openingId === row.accountId,
              icon: <EyeOutlined />,
              key: 'view',
              label: '查看',
              onClick: () => void openView(row)
            },
            canEdit && {
              disabled: openingId === row.accountId,
              icon: <EditOutlined />,
              key: 'edit',
              label: '修改',
              onClick: () => void openEdit(row)
            },
            row.enabled
              ? canDisable && {
                  confirm: `确认停用 RunningHub 账号“${row.accountName}”吗？依赖该账号的模板将不可用。`,
                  icon: <PoweroffOutlined />,
                  key: 'disable',
                  label: '停用',
                  onClick: () => void changeAccountStatus(row)
                }
              : canEnable && {
                  confirm: `确认启用 RunningHub 账号“${row.accountName}”吗？`,
                  icon: <PoweroffOutlined />,
                  key: 'enable',
                  label: '启用',
                  onClick: () => void changeAccountStatus(row)
                },
            canRemove && {
              confirm: `确认删除 RunningHub 账号“${row.accountName}”吗？删除后不可恢复。`,
              danger: true,
              icon: <DeleteOutlined />,
              key: 'remove',
              label: '删除',
              onClick: () => void removeAccount(row)
            }
          ]}
        />
      )
    }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="RunningHub 账号">
        <Result status="403" subTitle="当前运营端账号没有查看 RunningHub 账号的权限。" title="无权限访问" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="RunningHub 账号">
      <ProTable<RunningHubAccountSummary, RunningHubAccountTableParams>
        actionRef={actionRef}
        columns={columns}
        pagination={{ defaultPageSize: 10, pageSizeOptions: [10, 20, 50], showSizeChanger: true }}
        request={params => pageRunningHubAccounts(params)}
        rowKey="accountId"
        search={{ labelWidth: 96 }}
        toolbar={{ title: 'RunningHub 账号列表' }}
        toolBarRender={() => [
          canAdd && (
            <Button key="create" aria-label="新增账号" icon={<PlusOutlined />} type="primary" onClick={openCreate}>
              新增账号
            </Button>
          )
        ]}
      />

      <RunningHubAccountFormModal
        detail={formDetail}
        open={formOpen}
        readonly={formReadonly}
        submitting={submitting}
        onClose={closeForm}
        onFinish={submitAccount}
      />
    </PageContainer>
  );
}
