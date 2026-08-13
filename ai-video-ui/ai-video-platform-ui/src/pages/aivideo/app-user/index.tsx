import {
  EditOutlined,
  FileSearchOutlined,
  KeyOutlined,
  LogoutOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  UserSwitchOutlined
} from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProTable,
  type ActionType,
  type ProColumns
} from '@ant-design/pro-components';
import { App, Button, Form, Result } from 'antd';
import { useRef, useState } from 'react';
import {
  changeAppUserStatus,
  createAppUser,
  getAppUser,
  kickoutAppUser,
  pageAppRoles,
  pageAppUsers,
  replaceAppUserRoles,
  resetAppUserPassword,
  updateAppUser
} from '@/api/aivideo/identity';
import type {
  AppRoleAdmin,
  AppTableParams,
  AppUserAdmin,
  AppUserFormValues,
  AppUserQuery
} from '@/api/aivideo/identity/types';
import RowActions from '@/components/common/RowActions';
import { useUserStore } from '@/stores/userStore';
import { confirmAction } from '@/utils/modal';
import { hasPermi } from '@/utils/permission';
import OneTimeSecretModal from '@/pages/aivideo/components/OneTimeSecretModal';
import { AppIdentityStatusTag, appIdentityStatusOptions } from '@/pages/aivideo/identityUi';
import AppUserFormModal from './components/AppUserFormModal';
import AppUserSecurityDrawer from './components/AppUserSecurityDrawer';
import { toAppUserUpdateInput } from './userForm';

interface RoleAssignmentFormValues {
  roleIds?: string[];
}

interface OneTimePasswordState {
  title: string;
  value: string;
}

const roleQueryPageSize = 100;

export default function AppUserPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [userForm] = Form.useForm<AppUserFormValues>();
  const [roleAssignmentForm] = Form.useForm<RoleAssignmentFormValues>();
  const userInfo = useUserStore(state => state.userInfo);
  const [formOpen, setFormOpen] = useState(false);
  const [formTitle, setFormTitle] = useState('新增创作端用户');
  const [contactPreview, setContactPreview] = useState<Pick<AppUserAdmin, 'maskedEmail' | 'maskedPhone'>>();
  const [roleOptions, setRoleOptions] = useState<AppRoleAdmin[]>([]);
  const [detailUserId, setDetailUserId] = useState<string>();
  const [roleAssignmentTarget, setRoleAssignmentTarget] = useState<AppUserAdmin>();
  const [roleAssignmentOpen, setRoleAssignmentOpen] = useState(false);
  const [oneTimePassword, setOneTimePassword] = useState<OneTimePasswordState>();

  const canQuery = hasPermi(userInfo, ['aivideo:app-user:query']);
  const canAdd = hasPermi(userInfo, ['aivideo:app-user:add']);
  const canEdit = hasPermi(userInfo, ['aivideo:app-user:edit']);
  const canResetPassword = hasPermi(userInfo, ['aivideo:app-user:reset-password']);
  const canKickout = hasPermi(userInfo, ['aivideo:app-user:kickout']);
  const canAssignRole = hasPermi(userInfo, ['aivideo:app-user:assign-role']);

  const loadActiveRoles = async () => {
    const result = await pageAppRoles({ current: 1, pageSize: roleQueryPageSize, status: 'active' });
    setRoleOptions(result.data);
    return result.data;
  };

  const openAdd = async () => {
    try {
      const roles = await loadActiveRoles();
      userForm.resetFields();
      userForm.setFieldsValue({ roleIds: roles.length === 1 ? [roles[0].id] : [] });
      setContactPreview(undefined);
      setFormTitle('新增创作端用户');
      setFormOpen(true);
    } catch {
      // 集中请求层已展示失败提示，保持当前页面状态。
    }
  };

  const openEdit = (row: AppUserAdmin) => {
    userForm.resetFields();
    userForm.setFieldsValue({
      displayName: row.displayName,
      expectedIdentityRevision: row.identityRevision,
      id: row.id
    });
    setContactPreview({ maskedEmail: row.maskedEmail, maskedPhone: row.maskedPhone });
    setFormTitle('修改创作端用户');
    setFormOpen(true);
  };

  const submitUserForm = async (values: AppUserFormValues) => {
    if (values.id) {
      const updateInput = toAppUserUpdateInput(values);
      if (!updateInput) {
        message.warning('请检查资料填写与联系方式清空选项。');
        return false;
      }
      await updateAppUser(values.id, updateInput);
      message.success('创作端用户资料已更新');
      actionRef.current?.reload();
      return true;
    }
    if (!values.username || !values.displayName || !values.roleIds?.length) {
      return false;
    }

    const result = await createAppUser({
      displayName: values.displayName,
      email: values.email?.trim() || undefined,
      phone: values.phone?.trim() || undefined,
      roleIds: values.roleIds,
      username: values.username
    });
    setOneTimePassword({ title: `“${result.user.username}”的初始密码`, value: result.initialPassword });
    message.success('创作端用户已创建');
    actionRef.current?.reload();
    return true;
  };

  const changeStatus = async (row: AppUserAdmin, enabled: boolean) => {
    const status = enabled ? 'active' : 'disabled';
    const actionName = enabled ? '启用' : '停用';
    try {
      await changeAppUserStatus(row.id, { expectedIdentityRevision: row.identityRevision, status });
      message.success(`${actionName}成功`);
    } catch {
      // 集中请求层已展示失败提示，保留表格数据并重新获取服务端事实。
    } finally {
      actionRef.current?.reload();
    }
  };

  const resetPassword = async (row: AppUserAdmin) => {
    try {
      await confirmAction(`确认要重置创作端用户“${row.username}”的密码吗？原有创作端会话将失效。`);
      const result = await resetAppUserPassword(row.id, { expectedCredentialRevision: row.credentialRevision });
      setOneTimePassword({ title: `“${result.user.username}”的新初始密码`, value: result.initialPassword });
      message.success('密码已重置');
      actionRef.current?.reload();
    } catch {
      // 用户取消或请求失败时，不展示一次性密码。
    }
  };

  const kickoutUser = async (row: AppUserAdmin) => {
    try {
      await confirmAction(`确认要强制下线创作端用户“${row.username}”的全部会话吗？`);
      await kickoutAppUser(row.id, { reasonCode: 'admin_kickout' });
      message.success('已强制下线该用户的创作端会话');
      actionRef.current?.reload();
    } catch {
      // 用户取消或请求失败时保留当前列表。
    }
  };

  const openRoleAssignment = async (row: AppUserAdmin) => {
    try {
      const [roles, detail] = await Promise.all([loadActiveRoles(), getAppUser(row.id)]);
      setRoleOptions(roles);
      roleAssignmentForm.setFieldsValue({ roleIds: detail.roles.map(role => role.id) });
      setRoleAssignmentTarget(row);
      setRoleAssignmentOpen(true);
    } catch {
      // 集中请求层已展示失败提示，保持弹窗关闭。
    }
  };

  const submitRoleAssignment = async (values: RoleAssignmentFormValues) => {
    if (!roleAssignmentTarget || !values.roleIds) {
      return false;
    }
    await replaceAppUserRoles(roleAssignmentTarget.id, {
      expectedPermissionRevision: roleAssignmentTarget.permissionRevision,
      roleIds: values.roleIds
    });
    message.success('创作端角色已更新，受影响会话将重新校验权限');
    actionRef.current?.reload();
    return true;
  };

  const columns: ProColumns<AppUserAdmin>[] = [
    { dataIndex: 'id', search: false, title: '用户编号', width: 150 },
    {
      dataIndex: 'username',
      title: '登录账号',
      width: 160,
      render: (_, row) => (
        <Button type="link" onClick={() => setDetailUserId(row.id)}>
          {row.username}
        </Button>
      )
    },
    { dataIndex: 'displayName', title: '显示名称', width: 140 },
    { dataIndex: 'maskedPhone', search: false, title: '手机号码（脱敏）', width: 160 },
    { dataIndex: 'maskedEmail', search: false, title: '邮箱（脱敏）', width: 200 },
    {
      dataIndex: 'status',
      fieldProps: { options: appIdentityStatusOptions },
      title: '状态',
      valueType: 'select',
      width: 100,
      render: (_, row) => <AppIdentityStatusTag status={row.status} />
    },
    {
      dataIndex: 'mustChangePassword',
      search: false,
      title: '需改密',
      width: 100,
      render: (_, row) => (row.mustChangePassword ? '是' : '否')
    },
    { dataIndex: 'updateTime', search: false, title: '更新时间', valueType: 'dateTime', width: 170 },
    {
      fixed: 'right',
      search: false,
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, row) => (
        <RowActions
          actions={[
            {
              icon: <FileSearchOutlined />,
              key: 'detail',
              label: '详情',
              onClick: () => setDetailUserId(row.id)
            },
            canEdit && { icon: <EditOutlined />, key: 'edit', label: '修改', onClick: () => openEdit(row) },
            canEdit && {
              icon: <SafetyCertificateOutlined />,
              key: 'status',
              label: row.status === 'active' ? '停用' : '启用',
              confirm: `确认要${row.status === 'active' ? '停用' : '启用'}创作端用户“${row.username}”吗？`,
              onClick: () => changeStatus(row, row.status !== 'active')
            },
            canResetPassword && {
              icon: <KeyOutlined />,
              key: 'reset-password',
              label: '重置密码',
              onClick: () => {
                void resetPassword(row);
              }
            },
            canKickout && {
              icon: <LogoutOutlined />,
              key: 'kickout',
              label: '强制下线',
              onClick: () => {
                void kickoutUser(row);
              }
            },
            canAssignRole && {
              icon: <UserSwitchOutlined />,
              key: 'assign-role',
              label: '分配角色',
              onClick: () => {
                void openRoleAssignment(row);
              }
            }
          ]}
        />
      )
    }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="创作端用户">
        <Result status="403" subTitle="当前运营端账号没有查看创作端用户的权限。" title="无权限访问" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="创作端用户">
      <ProTable<AppUserAdmin, AppTableParams<AppUserQuery>>
        actionRef={actionRef}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        request={params => pageAppUsers(params)}
        rowKey="id"
        search={{ labelWidth: 96 }}
        toolbar={{ title: '创作端用户列表' }}
        toolBarRender={() => [
          canAdd && (
            <Button key="add" icon={<PlusOutlined />} type="primary" onClick={() => void openAdd()}>
              新增
            </Button>
          )
        ]}
      />

      <AppUserFormModal
        form={userForm}
        contactPreview={contactPreview}
        open={formOpen}
        roleOptions={roleOptions}
        title={formTitle}
        onClose={() => {
          setContactPreview(undefined);
          setFormOpen(false);
        }}
        onFinish={submitUserForm}
      />

      <AppUserSecurityDrawer open={Boolean(detailUserId)} userId={detailUserId} onClose={() => setDetailUserId(undefined)} />

      <ModalForm<RoleAssignmentFormValues>
        form={roleAssignmentForm}
        layout="vertical"
        modalProps={{
          destroyOnHidden: true,
          onCancel: () => {
            roleAssignmentForm.resetFields();
            setRoleAssignmentOpen(false);
            setRoleAssignmentTarget(undefined);
          }
        }}
        open={roleAssignmentOpen}
        title={`分配“${roleAssignmentTarget?.username || ''}”的创作端角色`}
        onFinish={submitRoleAssignment}
        onOpenChange={nextOpen => {
          if (!nextOpen) {
            roleAssignmentForm.resetFields();
            setRoleAssignmentOpen(false);
            setRoleAssignmentTarget(undefined);
          }
        }}
      >
        <ProFormSelect
          label="角色"
          mode="multiple"
          name="roleIds"
          options={roleOptions.map(role => ({ label: `${role.roleName}（${role.roleCode}）`, value: role.id }))}
          rules={[{ required: true, message: '请至少选择一个角色' }]}
        />
      </ModalForm>

      <OneTimeSecretModal
        label="请立即复制并通过安全渠道交付给用户"
        title={oneTimePassword?.title || '一次性初始密码'}
        value={oneTimePassword?.value}
        onClose={() => setOneTimePassword(undefined)}
      />
    </PageContainer>
  );
}
