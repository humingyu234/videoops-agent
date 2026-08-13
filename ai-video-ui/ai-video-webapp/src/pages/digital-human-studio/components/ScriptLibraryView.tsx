import {
  Alert,
  Button,
  Empty,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Spin,
  Tag,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/services/ai-video/core/errors';
import { userScriptApi } from '@/services/ai-video/script/api';
import type {
  UserScriptDetail,
  UserScriptInput,
  UserScriptListItem,
} from '@/services/ai-video/script/types';
import ScriptEditorModal from './ScriptEditorModal';
import StudioIcon from './StudioIcon';

const PAGE_SIZE = 20;

export const pageAfterDeletingLastRow = (page: number, rowCount: number) =>
  rowCount === 1 && page > 1 ? page - 1 : page;

interface ScriptLibraryViewProps {
  onToast: (message: string, type?: 'success' | 'error') => void;
}

const createIdempotencyKey = () =>
  globalThis.crypto?.randomUUID?.() ??
  `script-${Date.now()}-${Math.random().toString(16).slice(2)}`;

const formatDuration = (seconds: number) => {
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return minutes ? `${minutes}分${remainder}秒` : `${remainder}秒`;
};

const ScriptLibraryView: React.FC<ScriptLibraryViewProps> = ({ onToast }) => {
  const [records, setRecords] = useState<UserScriptListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [errorCode, setErrorCode] = useState<number>();
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<UserScriptDetail>();
  const [detail, setDetail] = useState<UserScriptDetail>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [versionText, setVersionText] = useState('');
  const [versionLoading, setVersionLoading] = useState(false);
  const [versionError, setVersionError] = useState('');
  const [intentKey, setIntentKey] = useState('');
  const requestRevision = useRef(0);

  const load = useCallback(async () => {
    const revision = requestRevision.current + 1;
    requestRevision.current = revision;
    setLoading(true);
    setError('');
    setErrorCode(undefined);
    try {
      const result = await userScriptApi.list({
        keyword: keyword.trim() || undefined,
        orderByColumn: 'updatedAt',
        isAsc: 'desc',
        pageNum: page,
        pageSize: PAGE_SIZE,
      });
      if (requestRevision.current !== revision) return;
      setRecords(result.rows ?? []);
      setTotal(result.total ?? 0);
    } catch (caught) {
      if (requestRevision.current !== revision) return;
      setError(caught instanceof Error ? caught.message : '文案列表加载失败');
      setErrorCode(caught instanceof ApiError ? caught.code : 500);
    } finally {
      if (requestRevision.current === revision) setLoading(false);
    }
  }, [keyword, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(undefined);
    setIntentKey(createIdempotencyKey());
    setEditorOpen(true);
  };

  const closeEditor = () => {
    setEditorOpen(false);
    setEditing(undefined);
    setIntentKey('');
  };

  const openDetail = async (item: UserScriptListItem) => {
    setDetailLoading(true);
    try {
      const current = await userScriptApi.detail(item.scriptId);
      setDetail(current);
      setVersionText(current.currentVersion.scriptText);
      setVersionError('');
    } catch (caught) {
      Modal.error({
        title: '文案详情加载失败',
        content: caught instanceof Error ? caught.message : '请稍后重试',
      });
    } finally {
      setDetailLoading(false);
    }
  };

  const openEdit = async (item: UserScriptListItem) => {
    try {
      const current = await userScriptApi.detail(item.scriptId);
      setEditing(current);
      setIntentKey(createIdempotencyKey());
      setEditorOpen(true);
    } catch (caught) {
      Modal.error({
        title: '文案加载失败',
        content: caught instanceof Error ? caught.message : '请稍后重试',
      });
    }
  };

  const submit = async (input: UserScriptInput) => {
    try {
      if (editing) {
        await userScriptApi.createVersion(editing.scriptId, {
          ...input,
          parentVersionId: editing.currentVersionId,
          expectedScriptRevision: editing.scriptRevision,
        });
        onToast('文案新版本已保存');
      } else {
        await userScriptApi.create(input);
        onToast('文案已保存');
      }
      closeEditor();
      setPage(1);
      await load();
    } catch (caught) {
      const conflict = caught instanceof ApiError && caught.code === 46136;
      Modal.error({
        title: conflict ? '文案已被更新' : '保存失败',
        content: conflict
          ? '服务器已有更新。你的本地内容仍保留，请复制后重新打开最新版本再保存。'
          : caught instanceof Error
            ? caught.message
            : '请稍后重试',
      });
      throw caught;
    }
  };

  const copy = async (item: UserScriptListItem) => {
    try {
      const current = await userScriptApi.version(
        item.scriptId,
        item.currentVersionId,
      );
      await navigator.clipboard.writeText(current.scriptText);
      onToast('文案正文已复制');
    } catch (caught) {
      Modal.error({
        title: '复制失败',
        content: caught instanceof Error ? caught.message : '请稍后重试',
      });
    }
  };

  const remove = async (item: UserScriptListItem) => {
    try {
      await userScriptApi.remove(item.scriptId);
      if (detail?.scriptId === item.scriptId) setDetail(undefined);
      onToast('文案已删除');
      const nextPage = pageAfterDeletingLastRow(page, records.length);
      if (nextPage !== page) {
        setPage(nextPage);
      } else {
        await load();
      }
    } catch (caught) {
      const referenced = caught instanceof ApiError && caught.code === 46118;
      Modal.error({
        title: referenced ? '文案正在被引用' : '删除失败',
        content: referenced
          ? '当前文案已关联其他内容，暂时不能删除。'
          : caught instanceof Error
            ? caught.message
            : '请稍后重试',
      });
    }
  };

  const viewVersion = async (versionId: string) => {
    if (!detail) return;
    setVersionLoading(true);
    setVersionError('');
    try {
      const version = await userScriptApi.version(detail.scriptId, versionId);
      setVersionText(version.scriptText);
    } catch (caught) {
      setVersionError(
        caught instanceof Error ? caught.message : '版本正文加载失败',
      );
    } finally {
      setVersionLoading(false);
    }
  };

  return (
    <div className="library-page script-library-live">
      <div className="page-toolbar">
        <Input
          allowClear
          className="script-search"
          prefix={<StudioIcon name="search" />}
          placeholder="搜索文案标题或正文摘要"
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setPage(1);
          }}
        />
        <Button
          type="primary"
          icon={<StudioIcon name="plus" />}
          onClick={openCreate}
        >
          新建文案
        </Button>
        <span className="spacer" />
        <span className="toolbar-count">共 {total} 条</span>
      </div>

      {error && (
        <Alert
          showIcon
          type={errorCode === 403 ? 'warning' : 'error'}
          title={errorCode === 403 ? '暂无文案查看权限' : '文案列表加载失败'}
          description={error}
          action={<Button onClick={() => void load()}>重试</Button>}
        />
      )}

      <Spin spinning={loading}>
        {!error && !loading && records.length === 0 ? (
          <Empty description={keyword ? '没有符合条件的文案' : '还没有文案'}>
            {!keyword && (
              <Button type="primary" onClick={openCreate}>
                新建第一篇文案
              </Button>
            )}
          </Empty>
        ) : (
          <div className="script-list">
            {records.map((item) => (
              <article
                className="script-card script-live-card"
                key={item.scriptId}
              >
                <button
                  type="button"
                  className="script-card-main"
                  onClick={() => void openDetail(item)}
                >
                  <span className="script-main">
                    <span className="script-title">
                      <b>{item.displayTitle}</b>
                      <Tag color="blue">v{item.versionNo}</Tag>
                    </span>
                    <span className="script-meta">
                      <i>{item.effectiveCharacterCount} 字</i>
                      <i>约 {formatDuration(item.estimatedDurationSeconds)}</i>
                      <i>{item.versionCount} 个版本</i>
                    </span>
                    <span className="script-preview">{item.preview}</span>
                  </span>
                </button>
                <span className="script-actions">
                  <Button type="text" onClick={() => void openEdit(item)}>
                    编辑
                  </Button>
                  <Button type="text" onClick={() => void copy(item)}>
                    复制
                  </Button>
                  <Popconfirm
                    title="删除这篇文案？"
                    description="删除后无法恢复。"
                    okText="删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => void remove(item)}
                  >
                    <Button danger type="text">
                      删除
                    </Button>
                  </Popconfirm>
                </span>
              </article>
            ))}
          </div>
        )}
      </Spin>

      {total > PAGE_SIZE && (
        <Pagination
          current={page}
          pageSize={PAGE_SIZE}
          total={total}
          showSizeChanger={false}
          onChange={setPage}
        />
      )}

      <ScriptEditorModal
        open={editorOpen}
        idempotencyKey={intentKey}
        initialValues={
          editing
            ? {
                displayTitle: editing.displayTitle,
                scriptText: editing.currentVersion.scriptText,
              }
            : undefined
        }
        title={editing ? '编辑文案并保存新版本' : '新建文案'}
        onCancel={closeEditor}
        onSubmit={submit}
      />

      <Modal
        open={Boolean(detail) || detailLoading}
        loading={detailLoading}
        footer={null}
        title={detail?.displayTitle ?? '文案详情'}
        width={720}
        onCancel={() => setDetail(undefined)}
      >
        {detail && (
          <div className="script-detail-live">
            <div className="script-detail-meta">
              <Tag color="blue">当前 v{detail.currentVersion.versionNo}</Tag>
              <span>{detail.currentVersion.effectiveCharacterCount} 字</span>
              <span>
                约{' '}
                {formatDuration(detail.currentVersion.estimatedDurationSeconds)}
              </span>
            </div>
            {versionError && (
              <Alert
                showIcon
                type="error"
                title="版本正文加载失败"
                description={versionError}
              />
            )}
            <Spin spinning={versionLoading}>
              <pre>{versionText}</pre>
            </Spin>
            <div className="drawer-section">
              <div className="prop-title">版本记录</div>
              {[...detail.versions]
                .sort((left, right) => right.versionNo - left.versionNo)
                .map((version) => (
                  <div className="version-node" key={version.versionId}>
                    <b>v{version.versionNo}</b>
                    <small>{version.preview}</small>
                    <Button
                      size="small"
                      type="link"
                      loading={versionLoading}
                      onClick={() => void viewVersion(version.versionId)}
                    >
                      查看
                    </Button>
                  </div>
                ))}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ScriptLibraryView;
