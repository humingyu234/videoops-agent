import { EditOutlined, PlusOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProFormText,
  ProTable,
  type ActionType,
  type ProColumns
} from '@ant-design/pro-components';
import { App, Button, Form, Result } from 'antd';
import { useRef, useState } from 'react';
import {
  createAppRole,
  listAppPermissions,
  pageAppRoles,
  replaceAppRolePermissions,
  updateAppRole
} from '@/api/aivideo/identity';
import type {
  AppIdentityStatus,
  AppPermissionAdmin,
  AppRevision,
  AppRoleAdmin,
  AppRoleQuery,
  AppRoleScopeType,
  AppTableParams
} from '@/api/aivideo/identity/types';
import RowActions from '@/components/common/RowActions';
import { useUserStore } from '@/stores/userStore';
import { hasPermi } from '@/utils/permission';
import {
  AppIdentityStatusTag,
  appIdentityStatusOptions,
  appRoleScopeOptions,
  getAppRoleScopeLabel
} from '@/pages/aivideo/identityUi';

interface AppRoleFormValues {
  id?: string;
  roleCode?: string;
  roleName?: string;
  scopeType?: AppRoleScopeType;
  status?: AppIdentityStatus;
  expectedRoleRevision?: AppRevision;
}

interface PermissionAssignmentFormValues {
  permissionIds?: string[];
}

export default function AppRolePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [roleForm] = Form.useForm<AppRoleFormValues>();
  const [permissionForm] = Form.useForm<PermissionAssignmentFormValues>();
  const userInfo = useUserStore(state => state.userInfo);
  const [roleFormOpen, setRoleFormOpen] = useState(false);
  const [roleFormTitle, setRoleFormTitle] = useState('新增创作端角色');
  const [permissionTarget, setPermissionTarget] = useState<AppRoleAdmin>();
  const [permissionOptions, setPermissionOptions] = useState<AppPermissionAdmin[]>([]);

  const canQuery = hasPermi(userInfo, ['aivideo:app-role:query']);
  const canEdit = hasPermi(userInfo, ['aivideo:app-role:edit']);
  const canAssignPermission = hasPermi(userInfo, ['aivideo:app-role:assign-permission']);

  const openCreateRole = () => {
    roleForm.resetFields();
    roleForm.setFieldsValue({ scopeType: 'personal', status: 'active' });
    setRoleFormTitle('新增创作端角色');
    setRoleFormOpen(true);
  };

  const openEditRole = (role: AppRoleAdmin) => {
    roleForm.resetFields();
    roleForm.setFieldsValue({
      expectedRoleRevision: role.roleRevision,
      id: role.id,
      roleCode: role.roleCode,
      roleName: role.roleName,
      scopeType: role.scopeType,
      status: role.status
    });
    setRoleFormTitle(`修改创作端角色“${role.roleName}”`);
    setRoleFormOpen(true);
  };

  const submitRole = async (values: AppRoleFormValues) => {
    if (!values.roleName || !values.status) {
      return false;
    }

    if (values.id) {
      if (!values.expectedRoleRevision) {
        return false;
      }
      await updateAppRole(values.id, {
        expectedRoleRevision: values.expectedRoleRevision,
        roleName: values.roleName,
        status: values.status
      });
      message.success('创作端角色已更新');
    } else {
      if (!values.roleCode || !values.scopeType) {
        return false;
      }
      await createAppRole({
        roleCode: values.roleCode,
        roleName: values.roleName,
        scopeType: values.scopeType,
        status: values.status
      });
      message.success('创作端角色已创建');
    }
    actionRef.current?.reload();
    return true;
  };

  const openPermissionAssignment = async (role: AppRoleAdmin) => {
    try {
      const permissions = await listAppPermissions();
      setPermissionOptions(permissions.filter(permission => permission.status === 'active'));
      permissionForm.setFieldsValue({ permissionIds: role.permissionIds });
      setPermissionTarget(role);
    } catch {
      // 集中请求层已经展示失败提示。
    }
  };

  const submitPermissionAssignment = async (values: PermissionAssignmentFormValues) => {
    if (!permissionTarget || !values.permissionIds) {
      return false;
    }
    await replaceAppRolePermissions(permissionTarget.id, {
      expectedRoleRevision: permissionTarget.roleRevision,
      permissionIds: values.permissionIds
    });
    message.success('创作端角色权限已更新，受影响会话将重新校验权限');
    actionRef.current?.reload();
    return true;
  };

  const closeRoleForm = () => {
    roleForm.resetFields();
    setRoleFormOpen(false);
  };

  const closePermissionForm = () => {
    permissionForm.resetFields();
    setPermissionTarget(undefined);
  };

  const columns: ProColumns<AppRoleAdmin>[] = [
    { dataIndex: 'id', search: false, title: '角色编号', width: 150 },
    { dataIndex: 'roleCode', title: '角色代码', width: 190 },
    { dataIndex: 'roleName', title: '角色名称', width: 160 },
    {
      dataIndex: 'scopeType',
      fieldProps: { options: appRoleScopeOptions },
      render: (_, role) => getAppRoleScopeLabel(role.scopeType),
      title: '作用域',
      valueType: 'select',
      width: 110
    },
    {
      dataIndex: 'builtIn',
      render: (_, role) => (role.builtIn ? '系统内置' : '自定义'),
      search: false,
      title: '类型',
      width: 110
    },
    {
      dataIndex: 'status',
      fieldProps: { options: appIdentityStatusOptions },
      render: (_, role) => <AppIdentityStatusTag status={role.status} />,
      title: '状态',
      valueType: 'select',
      width: 100
    },
    { dataIndex: 'userReferenceCount', search: false, title: '引用用户数', width: 120 },
    { dataIndex: 'updateTime', search: false, title: '更新时间', valueType: 'dateTime', width: 170 },
    {
      fixed: 'right',
      search: false,
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, role) => (
        <RowActions
          actions={[
            canEdit && { icon: <EditOutlined />, key: 'edit', label: '修改', onClick: () => openEditRole(role) },
            canAssignPermission && {
              icon: <SafetyCertificateOutlined />,
              key: 'permissions',
              label: '分配权限',
              onClick: () => {
                void openPermissionAssignment(role);
              }
            }
          ]}
        />
      )
    }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="创作端角色与权限">
        <Result status="403" subTitle="当前运营端账号没有查看创作端角色的权限。" title="无权限访问" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="创作端角色与权限">
      <ProTable<AppRoleAdmin, AppTableParams<AppRoleQuery>>
        actionRef={actionRef}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        request={params => pageAppRoles(params)}
        rowKey="id"
        search={{ labelWidth: 90 }}
        toolbar={{ title: '创作端角色列表' }}
        toolBarRender={() => [
          canEdit && (
            <Button key="create" icon={<PlusOutlined />} type="primary" onClick={openCreateRole}>
              新增角色
            </Button>
          )
        ]}
      />

      <ModalForm<AppRoleFormValues>
        form={roleForm}
        layout="vertical"
        modalProps={{ destroyOnHidden: true, onCancel: closeRoleForm }}
        open={roleFormOpen}
        title={roleFormTitle}
        onFinish={submitRole}
        onOpenChange={nextOpen => !nextOpen && closeRoleForm()}
      >
        <ProFormText name="id" hidden />
        <ProFormText name="expectedRoleRevision" hidden />
        <ProFormText
          label="角色代码"
          name="roleCode"
          placeholder="例如 content_editor"
          rules={[{ required: true, message: '角色代码不能为空' }]}
          fieldProps={{ disabled: Boolean(roleForm.getFieldValue('id')) }}
        />
        <ProFormText label="角色名称" name="roleName" rules={[{ required: true, message: '角色名称不能为空' }]} />
        <ProFormSelect
          label="作用域"
          name="scopeType"
          options={appRoleScopeOptions}
          rules={[{ required: true, message: '作用域不能为空' }]}
          fieldProps={{ disabled: Boolean(roleForm.getFieldValue('id')) }}
        />
        <ProFormSelect
          label="状态"
          name="status"
          options={appIdentityStatusOptions}
          rules={[{ required: true, message: '状态不能为空' }]}
        />
      </ModalForm>

      <ModalForm<PermissionAssignmentFormValues>
        form={permissionForm}
        layout="vertical"
        modalProps={{ destroyOnHidden: true, onCancel: closePermissionForm }}
        open={Boolean(permissionTarget)}
        title={`分配“${permissionTarget?.roleName || ''}”的创作端权限`}
        onFinish={submitPermissionAssignment}
        onOpenChange={nextOpen => !nextOpen && closePermissionForm()}
      >
        <ProFormSelect
          label="权限"
          mode="multiple"
          name="permissionIds"
          options={permissionOptions.map(permission => ({
            label: `${permission.permissionName}（${permission.permissionCode}）`,
            value: permission.id
          }))}
          rules={[{ required: true, message: '请至少选择一项权限' }]}
        />
      </ModalForm>
    </PageContainer>
  );
}
