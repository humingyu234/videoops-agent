import { LogoutOutlined } from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProTable,
  type ActionType,
  type ProColumns
} from '@ant-design/pro-components';
import { App, Form, Result } from 'antd';
import { useRef, useState } from 'react';
import { kickoutAppSession, pageAppSessions } from '@/api/aivideo/identity';
import type {
  AppKickoutReasonCode,
  AppSessionAdmin,
  AppSessionQuery,
  AppTableParams
} from '@/api/aivideo/identity/types';
import RowActions from '@/components/common/RowActions';
import { useUserStore } from '@/stores/userStore';
import { hasPermi } from '@/utils/permission';
import { appKickoutReasonOptions } from '@/pages/aivideo/identityUi';

interface SessionKickoutFormValues {
  reasonCode?: AppKickoutReasonCode;
}

export default function AppSessionPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [kickoutForm] = Form.useForm<SessionKickoutFormValues>();
  const userInfo = useUserStore(state => state.userInfo);
  const [kickoutTarget, setKickoutTarget] = useState<AppSessionAdmin>();

  const canQuery = hasPermi(userInfo, ['aivideo:app-session:query']);
  const canKickout = hasPermi(userInfo, ['aivideo:app-session:kickout']);

  const openKickout = (session: AppSessionAdmin) => {
    kickoutForm.resetFields();
    kickoutForm.setFieldsValue({ reasonCode: 'admin_kickout' });
    setKickoutTarget(session);
  };

  const closeKickout = () => {
    kickoutForm.resetFields();
    setKickoutTarget(undefined);
  };

  const submitKickout = async (values: SessionKickoutFormValues) => {
    if (!kickoutTarget || !values.reasonCode) {
      return false;
    }
    await kickoutAppSession(kickoutTarget.id, { reasonCode: values.reasonCode });
    message.success('指定创作端会话已撤销');
    actionRef.current?.reload();
    return true;
  };

  const columns: ProColumns<AppSessionAdmin>[] = [
    { dataIndex: 'id', search: false, title: '会话编号', width: 220 },
    { dataIndex: 'appUserId', title: '创作端用户编号', width: 170 },
    { dataIndex: 'clientId', title: '客户端标识', width: 180 },
    { dataIndex: 'deviceName', search: false, title: '设备', width: 180 },
    { dataIndex: 'lastActiveAt', search: false, title: '最近活动时间', valueType: 'dateTime', width: 180 },
    {
      fixed: 'right',
      search: false,
      title: '操作',
      valueType: 'option',
      width: 80,
      render: (_, session) => (
        <RowActions
          actions={[
            canKickout && {
              danger: true,
              icon: <LogoutOutlined />,
              key: 'kickout',
              label: '撤销会话',
              onClick: () => openKickout(session)
            }
          ]}
        />
      )
    }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="创作端会话">
        <Result status="403" subTitle="当前运营端账号没有查看创作端会话的权限。" title="无权限访问" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="创作端会话">
      <ProTable<AppSessionAdmin, AppTableParams<AppSessionQuery>>
        actionRef={actionRef}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        request={params => pageAppSessions(params)}
        rowKey="id"
        search={{ labelWidth: 112 }}
        toolbar={{ title: '创作端会话列表' }}
      />

      <ModalForm<SessionKickoutFormValues>
        form={kickoutForm}
        layout="vertical"
        modalProps={{ destroyOnHidden: true, onCancel: closeKickout }}
        open={Boolean(kickoutTarget)}
        title="撤销指定创作端会话"
        onFinish={submitKickout}
        onOpenChange={nextOpen => !nextOpen && closeKickout()}
      >
        <p>仅撤销当前选中的会话，不影响同一用户的其他创作端会话。</p>
        <ProFormSelect
          label="撤销原因"
          name="reasonCode"
          options={appKickoutReasonOptions}
          rules={[{ required: true, message: '请选择撤销原因' }]}
        />
      </ModalForm>
    </PageContainer>
  );
}
