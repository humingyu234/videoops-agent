import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { Result } from 'antd';
import { useRef } from 'react';
import { pageAppSecurityAudits } from '@/api/aivideo/identity';
import type {
  AppSecurityAuditAdmin,
  AppSecurityAuditQuery,
  AppTableParams
} from '@/api/aivideo/identity/types';
import { useUserStore } from '@/stores/userStore';
import { hasPermi } from '@/utils/permission';
import { appActorTypeOptions } from '@/pages/aivideo/identityUi';

export default function AppSecurityAuditPage() {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const userInfo = useUserStore(state => state.userInfo);
  const canQuery = hasPermi(userInfo, ['aivideo:app-security-audit:query']);

  const columns: ProColumns<AppSecurityAuditAdmin>[] = [
    { dataIndex: 'id', search: false, title: '审计编号', width: 150 },
    { dataIndex: 'resourceType', title: '资源类型', width: 130 },
    { dataIndex: 'resourceId', title: '资源编号', width: 150 },
    { dataIndex: 'action', title: '安全操作', width: 160 },
    {
      dataIndex: 'actorType',
      fieldProps: { options: appActorTypeOptions },
      title: '操作者类型',
      valueType: 'select',
      width: 130
    },
    { dataIndex: 'actorId', title: '操作者编号', width: 150 },
    { dataIndex: 'reason', search: false, title: '原因代码', width: 190 },
    { dataIndex: 'ipAddress', search: false, title: 'IP 地址', width: 150 },
    { dataIndex: 'requestId', search: false, title: '请求编号', width: 180 },
    { dataIndex: 'occurredAtRange', hideInTable: true, title: '发生时间', valueType: 'dateTimeRange' },
    { dataIndex: 'occurredAt', search: false, title: '发生时间', valueType: 'dateTime', width: 180 }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="创作端安全审计">
        <Result status="403" subTitle="当前运营端账号没有查看创作端安全审计的权限。" title="无权限访问" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="创作端安全审计">
      <ProTable<AppSecurityAuditAdmin, AppTableParams<AppSecurityAuditQuery>>
        actionRef={actionRef}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        request={params => pageAppSecurityAudits(params)}
        rowKey="id"
        search={{ labelWidth: 108 }}
        toolbar={{ title: '创作端安全审计列表' }}
      />
    </PageContainer>
  );
}
