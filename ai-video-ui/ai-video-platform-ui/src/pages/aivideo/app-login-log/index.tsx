import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Result, Tag } from 'antd';
import { useRef } from 'react';
import { pageAppLoginLogs } from '@/api/aivideo/identity';
import type { AppLoginLogAdmin, AppLoginLogQuery, AppTableParams } from '@/api/aivideo/identity/types';
import { useUserStore } from '@/stores/userStore';
import { hasPermi } from '@/utils/permission';

export default function AppLoginLogPage() {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const userInfo = useUserStore(state => state.userInfo);
  const canQuery = hasPermi(userInfo, ['aivideo:app-login-log:query']);

  const columns: ProColumns<AppLoginLogAdmin>[] = [
    { dataIndex: 'id', search: false, title: '日志编号', width: 150 },
    { dataIndex: 'maskedIdentifier', search: false, title: '登录标识（脱敏）', width: 180 },
    { dataIndex: 'appUserId', title: '创作端用户编号', width: 160 },
    { dataIndex: 'clientId', title: '客户端标识', width: 160 },
    { dataIndex: 'authMethod', search: false, title: '认证方式', width: 120 },
    {
      dataIndex: 'resultCode',
      fieldProps: { max: 599, min: 100 },
      title: '结果码',
      valueType: 'digit',
      width: 100,
      render: (_, log) => <Tag color={log.resultCode && log.resultCode < 400 ? 'green' : 'red'}>{log.resultCode || '-'}</Tag>
    },
    { dataIndex: 'failureCategory', search: false, title: '失败类别', width: 160 },
    { dataIndex: 'ipAddress', search: false, title: 'IP 地址', width: 150 },
    { dataIndex: 'deviceSummary', search: false, title: '设备摘要', width: 180 },
    { dataIndex: 'sessionId', search: false, title: '会话编号', width: 180 },
    { dataIndex: 'requestId', search: false, title: '请求编号', width: 180 },
    { dataIndex: 'occurredAtRange', hideInTable: true, title: '发生时间', valueType: 'dateTimeRange' },
    { dataIndex: 'occurredAt', search: false, title: '发生时间', valueType: 'dateTime', width: 180 }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="创作端登录日志">
        <Result status="403" subTitle="当前运营端账号没有查看创作端登录日志的权限。" title="无权限访问" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="创作端登录日志">
      <ProTable<AppLoginLogAdmin, AppTableParams<AppLoginLogQuery>>
        actionRef={actionRef}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        request={params => pageAppLoginLogs(params)}
        rowKey="id"
        search={{ labelWidth: 108 }}
        toolbar={{ title: '创作端登录日志列表' }}
      />
    </PageContainer>
  );
}
