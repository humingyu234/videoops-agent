import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { history, useModel } from '@umijs/max';
import { Button, Empty, Input, Pagination, Result, Skeleton, Tag } from 'antd';
import { useMemo, useState } from 'react';
import type { AppAuthState } from '@/services/ai-video/auth/authState';
import { ApiError, getHttpStatus } from '@/services/ai-video/core/errors';
import { discoveryApi } from '@/services/ai-video/discovery/api';
import {
  discoveryQueryKeys,
  type DiscoveryQueryScope,
} from '@/services/ai-video/discovery/queryKeys';
import type {
  DiscoveryHome,
  WorkflowChannel,
  WorkflowTemplateCard,
} from '@/services/ai-video/discovery/types';
import styles from './discovery.module.css';

const PAGE_SIZE = 10;

function errorCode(error: unknown): number | undefined {
  return error instanceof ApiError ? error.code : getHttpStatus(error);
}

function Cover({ template }: { template: WorkflowTemplateCard }) {
  if (!template.cover) {
    return <span className={styles.mediaPlaceholder}>暂无封面</span>;
  }
  return (
    <img
      alt={template.cover.alt}
      className={styles.cardMedia}
      src={template.cover.url}
    />
  );
}

function TemplateCard({ template }: { template: WorkflowTemplateCard }) {
  return (
    <button
      aria-label={`查看模板：${template.title}`}
      className={styles.card}
      onClick={() => history.push(`/discover/templates/${template.templateId}`)}
      type="button"
    >
      <span className={styles.mediaWrap}>
        <Cover template={template} />
      </span>
      <span className={styles.cardBody}>
        <strong>{template.title}</strong>
        <span className={styles.summary}>{template.summary}</span>
        <span className={styles.cardMeta}>
          <span>{template.category.label}</span>
          {template.usageCount ? (
            <span>{template.usageCount} 次使用</span>
          ) : null}
        </span>
      </span>
    </button>
  );
}

function HomeSections({
  data,
  onChannelChange,
}: {
  data: DiscoveryHome;
  onChannelChange: (channel: WorkflowChannel) => void;
}) {
  const isEmpty =
    data.banners.length === 0 && data.recommendations.length === 0;
  if (isEmpty) {
    return (
      <Empty
        description="暂无发现首页内容"
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    );
  }
  return (
    <>
      {data.banners.length > 0 ? (
        <section aria-label="精选专题" className={styles.bannerGrid}>
          {data.banners.slice(0, 3).map((banner) => (
            <button
              className={styles.banner}
              key={banner.bannerId}
              onClick={() =>
                banner.target.type === 'template'
                  ? history.push(
                      `/discover/templates/${banner.target.templateId}`,
                    )
                  : onChannelChange(banner.target.channel)
              }
              type="button"
            >
              <img alt={banner.media.alt} src={banner.media.url} />
              <span className={styles.bannerShade} />
              <span className={styles.bannerCopy}>
                <strong>{banner.title}</strong>
                {banner.subtitle ? <small>{banner.subtitle}</small> : null}
                <em>
                  {banner.target.type === 'template' ? '查看模板' : '浏览频道'}{' '}
                  →
                </em>
              </span>
            </button>
          ))}
        </section>
      ) : null}

      {data.recommendations.length > 0 ? (
        <section className={styles.recommendSection}>
          <div className={styles.sectionHeading}>
            <div>
              <span className={styles.eyebrow}>EDITOR&apos;S PICK</span>
              <h2>本周精选工作流</h2>
            </div>
          </div>
          <div className={styles.recommendRail}>
            {data.recommendations.slice(0, 6).map((item) => (
              <button
                className={styles.recommendCard}
                key={item.templateId}
                onClick={() =>
                  history.push(`/discover/templates/${item.templateId}`)
                }
                type="button"
              >
                {item.cover ? (
                  <img alt={item.cover.alt} src={item.cover.url} />
                ) : (
                  <span className={styles.recommendPlaceholder}>暂无封面</span>
                )}
                <span>
                  <strong>{item.title}</strong>
                  <small>{item.category.label}</small>
                </span>
              </button>
            ))}
          </div>
        </section>
      ) : null}
    </>
  );
}

function DiscoveryContent({ scope }: { scope: DiscoveryQueryScope }) {
  const [channel, setChannel] = useState<WorkflowChannel>('video_template');
  const [categoryCode, setCategoryCode] = useState('all');
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [pageNum, setPageNum] = useState(1);
  const home = useQuery({
    queryKey: discoveryQueryKeys.home(scope),
    queryFn: discoveryApi.getHome,
    retry: false,
  });
  const params = useMemo(
    () => ({
      pageNum,
      pageSize: PAGE_SIZE,
      channel,
      categoryCode: categoryCode === 'all' ? undefined : categoryCode,
      keyword: search || undefined,
      sort: 'recommended' as const,
    }),
    [categoryCode, channel, pageNum, search],
  );
  const templates = useQuery({
    queryKey: discoveryQueryKeys.templates(scope, params),
    queryFn: () => discoveryApi.getTemplates(params),
    retry: false,
  });

  const changeChannel = (nextChannel: WorkflowChannel) => {
    setChannel(nextChannel);
    setPageNum(1);
  };
  const submitSearch = () => {
    setSearch(keyword.trim());
    setPageNum(1);
  };

  return (
    <div className={styles.page}>
      <div className={styles.searchRow}>
        <Input
          aria-label="搜索视频模板和创作灵感"
          className={styles.search}
          onChange={(event) => setKeyword(event.target.value)}
          onPressEnter={submitSearch}
          placeholder="搜索视频模板、风格或创作灵感"
          prefix={<SearchOutlined />}
          suffix={
            <Button onClick={submitSearch} shape="round" type="primary">
              搜索
            </Button>
          }
          value={keyword}
        />
      </div>

      <div className={styles.homeState}>
        {home.isPending ? (
          <div
            aria-label="发现首页加载中"
            className={styles.loading}
            role="status"
          >
            <Skeleton active paragraph={{ rows: 4 }} />
          </div>
        ) : home.isError ? (
          <Result
            extra={
              <Button onClick={() => void home.refetch()} type="primary">
                重新加载发现首页
              </Button>
            }
            status={errorCode(home.error) === 403 ? '403' : 'error'}
            subTitle="请稍后重试。"
            title={
              errorCode(home.error) === 403
                ? '暂无发现内容查看权限'
                : '发现首页加载失败'
            }
          />
        ) : home.data ? (
          <HomeSections data={home.data} onChannelChange={changeChannel} />
        ) : null}
      </div>

      <section className={styles.catalog}>
        <fieldset aria-label="分类筛选" className={styles.chips}>
          <button
            className={categoryCode === 'all' ? styles.chipActive : styles.chip}
            aria-pressed={categoryCode === 'all'}
            onClick={() => {
              setCategoryCode('all');
              setPageNum(1);
            }}
            type="button"
          >
            全部
          </button>
          {home.data?.categories.map((item) => (
            <button
              className={
                categoryCode === item.categoryCode
                  ? styles.chipActive
                  : styles.chip
              }
              aria-pressed={categoryCode === item.categoryCode}
              key={item.categoryCode}
              onClick={() => {
                setCategoryCode(item.categoryCode);
                setPageNum(1);
              }}
              type="button"
            >
              {item.label}
            </button>
          ))}
          <span className={styles.tagGroup}>
            {home.data?.tags.slice(0, 4).map((tag) => (
              <Tag key={tag.tagCode} variant="filled">
                {tag.label}
              </Tag>
            ))}
          </span>
        </fieldset>

        {templates.isPending ? (
          <div
            aria-label="模板列表加载中"
            className={styles.loading}
            role="status"
          >
            <Skeleton active paragraph={{ rows: 8 }} />
          </div>
        ) : templates.isError ? (
          <Result
            extra={
              <Button onClick={() => void templates.refetch()} type="primary">
                重新加载模板列表
              </Button>
            }
            status={errorCode(templates.error) === 403 ? '403' : 'error'}
            subTitle="请检查网络后重试。"
            title={
              errorCode(templates.error) === 403
                ? '暂无模板列表查看权限'
                : '模板列表加载失败'
            }
          />
        ) : templates.data?.rows.length ? (
          <>
            <div className={styles.masonry}>
              {templates.data.rows.map((template) => (
                <TemplateCard key={template.templateId} template={template} />
              ))}
            </div>
            {templates.data.total > PAGE_SIZE ? (
              <div className={styles.pagination}>
                <Pagination
                  current={pageNum}
                  onChange={setPageNum}
                  pageSize={PAGE_SIZE}
                  showSizeChanger={false}
                  total={templates.data.total}
                />
              </div>
            ) : null}
          </>
        ) : (
          <Empty
            description="没有找到匹配的模板"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        )}
      </section>
    </div>
  );
}

export default function DiscoveryPage() {
  const { initialState } = useModel('@@initialState') as {
    initialState?: AppAuthState;
  };
  const userId = initialState?.currentUser?.id;
  const workspaceId = initialState?.currentUser?.workspace?.id;
  const scope = userId && workspaceId ? { userId, workspaceId } : undefined;

  return scope ? (
    <DiscoveryContent scope={scope} />
  ) : (
    <div className={styles.identityPending}>
      <Skeleton active paragraph={{ rows: 6 }} />
      <span>正在确认登录身份</span>
    </div>
  );
}
