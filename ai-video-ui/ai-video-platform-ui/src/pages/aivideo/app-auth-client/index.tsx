import { EditOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
  type ActionType,
  type ProColumns
} from '@ant-design/pro-components';
import { App, Button, Form, Result } from 'antd';
import { useRef, useState } from 'react';
import {
  createAppAuthClient,
  pageAppAuthClients,
  rotateAppAuthClientSecret,
  updateAppAuthClient
} from '@/api/aivideo/identity';
import type {
  AppAuthClientAdmin,
  AppAuthClientQuery,
  AppIdentityStatus,
  AppRevision,
  AppTableParams
} from '@/api/aivideo/identity/types';
import RowActions from '@/components/common/RowActions';
import { useUserStore } from '@/stores/userStore';
import { confirmAction } from '@/utils/modal';
import { hasPermi } from '@/utils/permission';
import OneTimeSecretModal from '@/pages/aivideo/components/OneTimeSecretModal';
import { AppIdentityStatusTag, appIdentityStatusOptions } from '@/pages/aivideo/identityUi';

interface AppAuthClientFormValues {
  id?: string;
  clientKey?: string;
  grantTypes?: string;
  accessPaths?: string;
  ipWhitelist?: string;
  tokenTimeout?: number;
  activeTimeout?: number;
  status?: AppIdentityStatus;
  expectedClientRevision?: AppRevision;
}

interface OneTimeClientSecretState {
  title: string;
  value: string;
}

export default function AppAuthClientPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [clientForm] = Form.useForm<AppAuthClientFormValues>();
  const userInfo = useUserStore(state => state.userInfo);
  const [formOpen, setFormOpen] = useState(false);
  const [formTitle, setFormTitle] = useState('新增创作端认证客户端');
  const [oneTimeSecret, setOneTimeSecret] = useState<OneTimeClientSecretState>();

  const canQuery = hasPermi(userInfo, ['aivideo:app-auth-client:query']);
  const canEdit = hasPermi(userInfo, ['aivideo:app-auth-client:edit']);
  const canRotateSecret = hasPermi(userInfo, ['aivideo:app-auth-client:rotate-secret']);

  const openCreate = () => {
    clientForm.resetFields();
    clientForm.setFieldsValue({ activeTimeout: 1800, status: 'active', tokenTimeout: 7200 });
    setFormTitle('新增创作端认证客户端');
    setFormOpen(true);
  };

  const openEdit = (client: AppAuthClientAdmin) => {
    clientForm.resetFields();
    clientForm.setFieldsValue({
      accessPaths: client.accessPaths,
      activeTimeout: client.activeTimeout,
      clientKey: client.clientKey,
      expectedClientRevision: client.clientRevision,
      id: client.id,
      ipWhitelist: client.ipWhitelist || undefined,
      grantTypes: client.grantTypes,
      status: client.status,
      tokenTimeout: client.tokenTimeout
    });
    setFormTitle(`修改认证客户端“${client.clientKey}”`);
    setFormOpen(true);
  };

  const submitClient = async (values: AppAuthClientFormValues) => {
    if (
      !values.clientKey ||
      !values.grantTypes ||
      !values.accessPaths ||
      !values.tokenTimeout ||
      !values.activeTimeout ||
      !values.status
    ) {
      return false;
    }

    if (values.id) {
      if (!values.expectedClientRevision) {
        return false;
      }
      await updateAppAuthClient(values.id, {
        accessPaths: values.accessPaths,
        activeTimeout: values.activeTimeout,
        clientKey: values.clientKey,
        expectedClientRevision: values.expectedClientRevision,
        grantTypes: values.grantTypes,
        ipWhitelist: values.ipWhitelist,
        status: values.status,
        tokenTimeout: values.tokenTimeout
      });
      message.success('创作端认证客户端已更新');
    } else {
      const result = await createAppAuthClient({
        accessPaths: values.accessPaths,
        activeTimeout: values.activeTimeout,
        clientKey: values.clientKey,
        grantTypes: values.grantTypes,
        ipWhitelist: values.ipWhitelist,
        status: values.status,
        tokenTimeout: values.tokenTimeout
      });
      setOneTimeSecret({ title: `“${result.client.clientKey}”的客户端密钥`, value: result.clientSecret });
      message.success('创作端认证客户端已创建');
    }
    actionRef.current?.reload();
    return true;
  };

  const rotateSecret = async (client: AppAuthClientAdmin) => {
    try {
      await confirmAction(`确认要轮换认证客户端“${client.clientKey}”的密钥吗？旧密钥会立即失效。`);
      const result = await rotateAppAuthClientSecret(client.id, { expectedClientRevision: client.clientRevision });
      setOneTimeSecret({ title: `“${result.client.clientKey}”的新客户端密钥`, value: result.clientSecret });
      message.success('客户端密钥已轮换');
      actionRef.current?.reload();
    } catch {
      // 用户取消或请求失败时，绝不展示旧密钥或猜测新密钥。
    }
  };

  const closeClientForm = () => {
    clientForm.resetFields();
    setFormOpen(false);
  };

  const columns: ProColumns<AppAuthClientAdmin>[] = [
    { dataIndex: 'id', search: false, title: '客户端编号', width: 150 },
    { dataIndex: 'clientId', search: false, title: '客户端标识', width: 170 },
    { dataIndex: 'clientKey', title: '客户端键', width: 170 },
    { dataIndex: 'grantTypes', search: false, title: '授权方式', width: 180 },
    { dataIndex: 'accessPaths', search: false, title: '允许路径', width: 220, ellipsis: true },
    { dataIndex: 'ipWhitelist', search: false, title: 'IP 白名单', width: 180, ellipsis: true },
    { dataIndex: 'tokenTimeout', search: false, title: '令牌超时（秒）', width: 140 },
    { dataIndex: 'activeTimeout', search: false, title: '活跃超时（秒）', width: 140 },
    {
      dataIndex: 'status',
      fieldProps: { options: appIdentityStatusOptions },
      render: (_, client) => <AppIdentityStatusTag status={client.status} />,
      title: '状态',
      valueType: 'select',
      width: 100
    },
    { dataIndex: 'activeSessionCount', search: false, title: '活跃会话数', width: 120 },
    {
      dataIndex: 'hasSecret',
      render: (_, client) => (client.hasSecret ? '已配置' : '未配置'),
      search: false,
      title: '密钥状态',
      width: 100
    },
    {
      fixed: 'right',
      search: false,
      title: '操作',
      valueType: 'option',
      width: 110,
      render: (_, client) => (
        <RowActions
          actions={[
            canEdit && { icon: <EditOutlined />, key: 'edit', label: '修改', onClick: () => openEdit(client) },
            canRotateSecret && {
              icon: <KeyOutlined />,
              key: 'rotate-secret',
              label: '轮换密钥',
              onClick: () => {
                void rotateSecret(client);
              }
            }
          ]}
        />
      )
    }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="创作端认证客户端">
        <Result status="403" subTitle="当前运营端账号没有查看创作端认证客户端的权限。" title="无权限访问" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="创作端认证客户端">
      <ProTable<AppAuthClientAdmin, AppTableParams<AppAuthClientQuery>>
        actionRef={actionRef}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        request={params => pageAppAuthClients(params)}
        rowKey="id"
        search={{ labelWidth: 96 }}
        toolbar={{ title: '创作端认证客户端列表' }}
        toolBarRender={() => [
          canEdit && (
            <Button key="create" icon={<PlusOutlined />} type="primary" onClick={openCreate}>
              新增客户端
            </Button>
          )
        ]}
      />

      <ModalForm<AppAuthClientFormValues>
        form={clientForm}
        layout="vertical"
        modalProps={{ destroyOnHidden: true, onCancel: closeClientForm }}
        open={formOpen}
        title={formTitle}
        width={760}
        onFinish={submitClient}
        onOpenChange={nextOpen => !nextOpen && closeClientForm()}
      >
        <ProFormText name="id" hidden />
        <ProFormText name="expectedClientRevision" hidden />
        <ProFormText
          label="客户端键"
          name="clientKey"
          placeholder="例如 creator-web"
          rules={[{ required: true, message: '客户端键不能为空' }]}
        />
        <ProFormTextArea
          label="授权方式"
          name="grantTypes"
          placeholder="例如 password,sms,email"
          rules={[{ required: true, message: '授权方式不能为空' }]}
        />
        <ProFormTextArea
          label="允许访问路径"
          name="accessPaths"
          placeholder="例如 /api/auth/**"
          rules={[{ required: true, message: '允许访问路径不能为空' }]}
        />
        <ProFormTextArea label="IP 白名单" name="ipWhitelist" placeholder="可选，支持逗号或换行分隔" />
        <div className="form-grid">
          <ProFormDigit
            label="令牌超时（秒）"
            min={1}
            name="tokenTimeout"
            rules={[{ required: true, message: '令牌超时不能为空' }]}
          />
          <ProFormDigit
            label="活跃超时（秒）"
            min={1}
            name="activeTimeout"
            rules={[{ required: true, message: '活跃超时不能为空' }]}
          />
        </div>
        <ProFormSelect
          label="状态"
          name="status"
          options={appIdentityStatusOptions}
          rules={[{ required: true, message: '状态不能为空' }]}
        />
      </ModalForm>

      <OneTimeSecretModal
        label="请立即复制并妥善保存；关闭后不可再次查看"
        title={oneTimeSecret?.title || '一次性客户端密钥'}
        value={oneTimeSecret?.value}
        onClose={() => setOneTimeSecret(undefined)}
      />
    </PageContainer>
  );
}
