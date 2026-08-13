import { ProDescriptions } from '@ant-design/pro-components';
import { Alert, Divider, Drawer, List, Space } from 'antd';
import { useEffect, useState } from 'react';
import type { AppUserDetail } from '@/api/aivideo/identity/types';
import { getAppUser } from '@/api/aivideo/identity';
import { AppIdentityStatusTag } from '@/pages/aivideo/identityUi';

interface AppUserSecurityDrawerProps {
  open: boolean;
  userId?: string;
  onClose: () => void;
}

export default function AppUserSecurityDrawer({ open, userId, onClose }: AppUserSecurityDrawerProps) {
  const [detail, setDetail] = useState<AppUserDetail>();
  const [loading, setLoading] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    if (!open || !userId) {
      return;
    }

    let cancelled = false;
    setLoading(true);
    setLoadFailed(false);
    void getAppUser(userId)
      .then(result => {
        if (!cancelled) {
          setDetail(result);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLoadFailed(true);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [open, userId]);

  const closeDrawer = () => {
    setDetail(undefined);
    setLoadFailed(false);
    onClose();
  };

  const user = detail?.user;

  return (
    <Drawer destroyOnHidden open={open} title="创作端用户安全详情" size={760} onClose={closeDrawer}>
      {loadFailed ? (
        <Alert showIcon title="详情加载失败，请关闭后重试。" type="error" />
      ) : (
        <>
          <ProDescriptions
            bordered
            column={2}
            dataSource={{
              ...user,
              statusDisplay: user?.status,
              sessionCount: detail?.sessions.length || 0
            }}
            loading={loading}
            columns={[
              { dataIndex: 'id', title: '创作端用户编号' },
              { dataIndex: 'username', title: '登录账号' },
              { dataIndex: 'displayName', title: '显示名称' },
              { dataIndex: 'maskedPhone', title: '手机号码（脱敏）' },
              { dataIndex: 'maskedEmail', title: '邮箱（脱敏）' },
              {
                dataIndex: 'statusDisplay',
                render: (_, record) => <AppIdentityStatusTag status={record.status} />,
                title: '账号状态'
              },
              { dataIndex: 'credentialRevision', title: '凭据修订号' },
              { dataIndex: 'identityRevision', title: '身份修订号' },
              { dataIndex: 'permissionRevision', title: '权限修订号' },
              { dataIndex: 'sessionCount', title: '当前会话数' },
              { dataIndex: 'createTime', title: '创建时间', valueType: 'dateTime' },
              { dataIndex: 'updateTime', title: '更新时间', valueType: 'dateTime' }
            ]}
          />
          <Divider>已分配角色</Divider>
          <List
            dataSource={detail?.roles || []}
            locale={{ emptyText: loading ? '正在加载角色…' : '暂无角色' }}
            renderItem={role => (
              <List.Item>
                <Space>
                  <span>{role.roleName}</span>
                  <span>{role.roleCode}</span>
                  <AppIdentityStatusTag status={role.status} />
                </Space>
              </List.Item>
            )}
          />
          <Divider>会话摘要</Divider>
          <List
            dataSource={detail?.sessions || []}
            locale={{ emptyText: loading ? '正在加载会话…' : '暂无有效会话' }}
            renderItem={session => (
              <List.Item>
                <Space orientation="vertical" size={0}>
                  <span>{session.deviceName || '未知设备'}</span>
                  <span>客户端：{session.clientId || '-'}</span>
                  <span>最近活动：{session.lastActiveAt || '-'}</span>
                </Space>
              </List.Item>
            )}
          />
        </>
      )}
    </Drawer>
  );
}
