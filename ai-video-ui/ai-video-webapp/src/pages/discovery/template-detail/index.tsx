import { ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { history, useModel, useParams } from '@umijs/max';
import { Button, Result, Skeleton, Tag } from 'antd';
import type { AppAuthState } from '@/services/ai-video/auth/authState';
import { ApiError, getHttpStatus } from '@/services/ai-video/core/errors';
import { discoveryApi } from '@/services/ai-video/discovery/api';
import {
  type DiscoveryQueryScope,
  discoveryQueryKeys,
} from '@/services/ai-video/discovery/queryKeys';
import { TemplateRunForm } from '../template-create';
import styles from './template-detail.module.css';

function errorCode(error: unknown): number | undefined {
  return error instanceof ApiError ? error.code : getHttpStatus(error);
}

function TemplateDetailContent({
  scope,
  templateId,
}: {
  scope: DiscoveryQueryScope;
  templateId: string;
}) {
  const detail = useQuery({
    queryKey: discoveryQueryKeys.template(scope, templateId),
    queryFn: () => discoveryApi.getTemplate(templateId),
    retry: false,
  });
  const creationConfig = useQuery({
    queryKey: discoveryQueryKeys.creationConfig(scope, templateId),
    queryFn: () => discoveryApi.getCreationConfig(templateId),
    retry: false,
  });
  const detailCode = detail.isError ? errorCode(detail.error) : undefined;
  const configCode = creationConfig.isError
    ? errorCode(creationConfig.error)
    : undefined;

  if (detail.isPending)
    return (
      <div className={styles.loading}>
        <Skeleton active avatar paragraph={{ rows: 12 }} />
      </div>
    );
  if (detailCode === 403 || configCode === 403)
    return (
      <Result
        extra={
          <Button onClick={() => history.push('/discover')} type="primary">
            返回发现
          </Button>
        }
        status="403"
        subTitle="请联系管理员开通权限。"
        title="暂无模板查看权限"
      />
    );
  if (detailCode === 46501 || configCode === 46501)
    return (
      <Result
        extra={
          <Button onClick={() => history.push('/discover')} type="primary">
            返回发现
          </Button>
        }
        status="warning"
        subTitle="该模板可能已下架或暂停使用。"
        title="模板暂不可用"
      />
    );
  if (detail.isError)
    return (
      <Result
        extra={
          <Button onClick={() => void detail.refetch()} type="primary">
            重新加载模板
          </Button>
        }
        status="error"
        subTitle="网络暂时不可用，请稍后重试。"
        title="模板加载失败"
      />
    );

  const template = detail.data;
  const preview = template.preview ?? template.cover;
  return (
    <>
      <section className={styles.hero}>
        <div className={styles.leftColumn} data-testid="template-detail-left">
          <div className={styles.preview}>
            {preview ? (
              <img alt={preview.alt} src={preview.url} />
            ) : (
              <span className={styles.previewPlaceholder}>暂无封面</span>
            )}
          </div>
          <article className={styles.description}>
            <span className={styles.kicker}>ABOUT THIS TEMPLATE</span>
            <h2>模板介绍</h2>
            <p>{template.description}</p>
          </article>
        </div>
        <div className={styles.intro} data-testid="template-detail-right">
          <span className={styles.channel}>
            {template.channel === 'video_template' ? '视频模板' : '创作灵感'}
          </span>
          <h2>{template.title}</h2>
          <p className={styles.summary}>{template.summary}</p>
          <div className={styles.tags}>
            {template.tags.map((tag) => (
              <Tag key={tag.tagCode} variant="filled">
                {tag.label}
              </Tag>
            ))}
          </div>
          {creationConfig.isPending ? (
            <p className={styles.hint}>正在加载制作配置</p>
          ) : configCode === 46503 ? (
            <p className={styles.configWarning}>制作配置暂不可用</p>
          ) : creationConfig.isError || !creationConfig.data ? (
            <div className={styles.configError}>
              <span>制作配置加载失败</span>
              <Button
                onClick={() => void creationConfig.refetch()}
                size="small"
              >
                重新加载
              </Button>
            </div>
          ) : (
            <section className={styles.requirements}>
              <h2>制作前需要准备</h2>
              <TemplateRunForm
                config={creationConfig.data}
                templateId={templateId}
              />
            </section>
          )}
        </div>
      </section>
      {template.cases.length ? (
        <section className={styles.cases}>
          <div>
            <span className={styles.kicker}>MORE EXAMPLES</span>
            <h2>更多效果</h2>
          </div>
          <div className={styles.caseGrid}>
            {template.cases.map((item) => (
              <img alt={item.alt} key={item.mediaId} src={item.url} />
            ))}
          </div>
        </section>
      ) : null}
    </>
  );
}

export default function TemplateDetailPage() {
  const { templateId = '' } = useParams<{ templateId: string }>();
  const { initialState } = useModel('@@initialState') as {
    initialState?: AppAuthState;
  };
  const userId = initialState?.currentUser?.id;
  const workspaceId = initialState?.currentUser?.workspace?.id;
  const scope = userId && workspaceId ? { userId, workspaceId } : undefined;
  return (
    <div className={styles.page}>
      <Button
        className={styles.back}
        icon={<ArrowLeftOutlined />}
        onClick={() => history.push('/discover')}
        type="text"
      >
        返回发现
      </Button>
      {scope && templateId ? (
        <TemplateDetailContent scope={scope} templateId={templateId} />
      ) : (
        <div className={styles.identityPending}>
          <Skeleton active paragraph={{ rows: 8 }} />
          <span>正在确认登录身份</span>
        </div>
      )}
    </div>
  );
}
