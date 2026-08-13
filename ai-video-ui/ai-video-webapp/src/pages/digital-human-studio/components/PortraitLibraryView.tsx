import type { UploadFile } from 'antd';
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Spin,
  Upload,
} from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { ApiError } from '@/services/ai-video/core/errors';
import { portraitApi } from '@/services/ai-video/portrait/api';
import type {
  Portrait,
  PortraitGender,
  PortraitInput,
} from '@/services/ai-video/portrait/types';
import type { AvatarSpaceSource } from '../avatar-space/model';
import {
  isSupportedPortraitImage,
  PORTRAIT_IMAGE_ACCEPT,
  PORTRAIT_IMAGE_FORMAT_MESSAGE,
} from '../utils/portraitImageFile';
import StudioIcon from './StudioIcon';

const PAGE_SIZE = 12;

interface PortraitLibraryViewProps {
  onOpenSpace: (source?: AvatarSpaceSource) => void;
  onToast: (message: string) => void;
}

interface PortraitFormValues {
  name: string;
  gender: PortraitGender;
  sceneTags?: string[];
  note?: string;
}

const genderLabel: Record<PortraitGender, string> = {
  female: '女',
  male: '男',
  unspecified: '未指定',
};

const statusLabel = {
  processing: '处理中',
  ready: '可使用',
  failed: '处理失败',
};

const formatSize = (size: string | number | undefined) => {
  const value = Number(size ?? 0);
  return value < 1024 * 1024
    ? `${Math.max(1, Math.round(value / 1024))}KB`
    : `${(value / 1024 / 1024).toFixed(1)}MB`;
};

const createIdempotencyKey = () =>
  globalThis.crypto?.randomUUID?.() ??
  `portrait-${Date.now()}-${Math.random().toString(16).slice(2)}`;

const readAsDataUrl = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });

const PortraitLibraryView: React.FC<PortraitLibraryViewProps> = ({
  onOpenSpace,
  onToast,
}) => {
  const [form] = Form.useForm<PortraitFormValues>();
  const [records, setRecords] = useState<Portrait[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [gender, setGender] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [errorCode, setErrorCode] = useState<number>();
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<Portrait>();
  const [detail, setDetail] = useState<Portrait>();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImage, setPreviewImage] = useState('');
  const [previewFileName, setPreviewFileName] = useState('');
  const fileReadRevisionRef = useRef(0);
  const stagedAssetRef = useRef<{ file: File; assetId: string } | undefined>(
    undefined,
  );
  const createRequestRef = useRef<
    { fingerprint: string; key: string } | undefined
  >(undefined);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    setErrorCode(undefined);
    try {
      const result = await portraitApi.list({
        keyword: keyword.trim() || undefined,
        availabilityStatus: status || undefined,
        gender: gender || undefined,
        pageNum: page,
        pageSize: PAGE_SIZE,
      });
      setRecords(result.rows ?? []);
      setTotal(result.total ?? 0);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '形象列表加载失败');
      setErrorCode(caught instanceof ApiError ? caught.code : 500);
    } finally {
      setLoading(false);
    }
  }, [gender, keyword, page, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const selectedFile = useMemo(
    () => fileList[0]?.originFileObj as File | undefined,
    [fileList],
  );

  const closePreview = () => {
    setPreviewOpen(false);
    setPreviewImage('');
    setPreviewFileName('');
  };

  const clearSelectedFile = () => {
    fileReadRevisionRef.current += 1;
    stagedAssetRef.current = undefined;
    createRequestRef.current = undefined;
    setFileList([]);
    closePreview();
  };

  const closeEditor = () => {
    setEditorOpen(false);
    clearSelectedFile();
  };

  const previewFile = async (file: UploadFile) => {
    try {
      let source = file.url ?? file.preview ?? file.thumbUrl;
      if (!source && file.originFileObj) {
        source = await readAsDataUrl(file.originFileObj);
        file.preview = source;
      }
      if (!source) return;
      setPreviewImage(source);
      setPreviewFileName(file.name);
      setPreviewOpen(true);
    } catch {
      Modal.error({
        title: '预览失败',
        content: '无法读取当前图片，请重新选择。',
      });
    }
  };

  const selectFile = async (next: UploadFile[]) => {
    const candidate = next.at(-1);
    const file = candidate?.originFileObj;
    if (!candidate || !file) {
      clearSelectedFile();
      return;
    }
    if (!isSupportedPortraitImage(file)) {
      Modal.warning({
        title: '文件类型不支持',
        content: PORTRAIT_IMAGE_FORMAT_MESSAGE,
      });
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      Modal.warning({
        title: '文件过大',
        content: '单张人物照片不能超过 10MB。',
      });
      return;
    }
    stagedAssetRef.current = undefined;
    createRequestRef.current = undefined;
    const revision = fileReadRevisionRef.current + 1;
    fileReadRevisionRef.current = revision;
    try {
      const preview = await readAsDataUrl(file);
      if (fileReadRevisionRef.current !== revision) return;
      setFileList([{ ...candidate, preview, thumbUrl: preview }]);
    } catch {
      if (fileReadRevisionRef.current !== revision) return;
      Modal.error({
        title: '图片读取失败',
        content: '无法读取当前图片，请重新选择。',
      });
      clearSelectedFile();
    }
  };

  const openCreate = () => {
    setEditing(undefined);
    clearSelectedFile();
    form.resetFields();
    form.setFieldValue('gender', 'unspecified');
    setEditorOpen(true);
  };

  const openEdit = (portrait: Portrait) => {
    void portraitApi
      .detail(portrait.portraitId)
      .then((current) => {
        setEditing(current);
        clearSelectedFile();
        form.setFieldsValue({
          name: current.name,
          gender: current.gender,
          sceneTags: current.sceneTags,
          note: current.note,
        });
        setEditorOpen(true);
      })
      .catch((caught) => {
        Modal.error({
          title: '加载失败',
          content: caught instanceof Error ? caught.message : '请稍后重试',
        });
      });
  };

  const openDetail = async (portrait: Portrait) => {
    try {
      setDetail(await portraitApi.detail(portrait.portraitId));
    } catch (caught) {
      Modal.error({
        title: '加载失败',
        content: caught instanceof Error ? caught.message : '请稍后重试',
      });
    }
  };

  const submit = async () => {
    const values = await form.validateFields();
    if (!editing && !selectedFile) {
      form.setFields([{ name: 'name', errors: ['请先选择人物照片'] }]);
      return;
    }
    setSubmitting(true);
    try {
      if (editing) {
        await portraitApi.update(editing.portraitId, {
          ...values,
          sceneTags: values.sceneTags ?? [],
          expectedRevision: editing.recordRevision,
        });
        onToast('形象资料已更新');
      } else {
        const file = selectedFile as File;
        let assetId =
          stagedAssetRef.current?.file === file
            ? stagedAssetRef.current.assetId
            : undefined;
        if (!assetId) {
          const asset = await portraitApi.upload(file);
          assetId = asset.assetId;
          stagedAssetRef.current = { file, assetId };
        }
        const fingerprint = JSON.stringify({
          assetId,
          ...values,
          sceneTags: values.sceneTags ?? [],
        });
        if (createRequestRef.current?.fingerprint !== fingerprint) {
          createRequestRef.current = {
            fingerprint,
            key: createIdempotencyKey(),
          };
        }
        const input: PortraitInput = {
          assetId,
          name: values.name,
          gender: values.gender,
          sceneTags: values.sceneTags ?? [],
          note: values.note,
          idempotencyKey: createRequestRef.current.key,
        };
        await portraitApi.create(input);
        onToast('形象已创建');
      }
      closeEditor();
      setPage(1);
      await load();
    } catch (caught) {
      Modal.error({
        title: editing ? '更新失败' : '创建失败',
        content: caught instanceof Error ? caught.message : '请稍后重试',
      });
    } finally {
      setSubmitting(false);
    }
  };

  const remove = async (portrait: Portrait) => {
    try {
      await portraitApi.remove(portrait.portraitId, portrait.recordRevision);
      if (detail?.portraitId === portrait.portraitId) setDetail(undefined);
      onToast('形象已永久删除');
      await load();
    } catch (caught) {
      Modal.error({
        title: '删除失败',
        content: caught instanceof Error ? caught.message : '请稍后重试',
      });
    }
  };

  return (
    <div className="library-page">
      <div className="page-toolbar">
        <label className="search-box">
          <StudioIcon name="search" />
          <input
            value={keyword}
            placeholder="搜索形象名称"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(1);
            }}
          />
        </label>
        <Select
          value={gender}
          style={{ width: 120 }}
          options={[
            { value: '', label: '全部性别' },
            { value: 'female', label: '女' },
            { value: 'male', label: '男' },
            { value: 'unspecified', label: '未指定' },
          ]}
          onChange={(value) => {
            setGender(value);
            setPage(1);
          }}
        />
        <Select
          value={status}
          style={{ width: 130 }}
          options={[
            { value: '', label: '全部状态' },
            { value: 'ready', label: '可使用' },
            { value: 'processing', label: '处理中' },
            { value: 'failed', label: '处理失败' },
          ]}
          onChange={(value) => {
            setStatus(value);
            setPage(1);
          }}
        />
        <Button icon={<StudioIcon name="star" />} onClick={() => onOpenSpace()}>
          形象空间
        </Button>
        <Button
          type="primary"
          className="portrait-primary-action"
          icon={<StudioIcon name="plus" />}
          onClick={openCreate}
        >
          新增形象
        </Button>
      </div>

      {error && (
        <Alert
          showIcon
          type={errorCode === 403 ? 'warning' : 'error'}
          message={errorCode === 403 ? '暂无形象查看权限' : '形象列表加载失败'}
          description={error}
          action={<Button onClick={() => void load()}>重试</Button>}
        />
      )}

      <Spin spinning={loading}>
        {!error && !loading && records.length === 0 ? (
          <Empty
            description={
              keyword || status || gender
                ? '没有符合条件的形象'
                : '还没有人物形象'
            }
          >
            {!keyword && !status && !gender && (
              <Button
                type="primary"
                className="portrait-primary-action"
                onClick={openCreate}
              >
                上传第一张人物照片
              </Button>
            )}
          </Empty>
        ) : (
          <div className="avatar-grid">
            {records.map((portrait) => (
              <article
                className="avatar-card portrait-live-card"
                key={portrait.portraitId}
              >
                <button
                  type="button"
                  className="portrait-card-main"
                  onClick={() => void openDetail(portrait)}
                >
                  <div className="avatar-cover">
                    {portrait.previewUrl ? (
                      <img alt={portrait.name} src={portrait.previewUrl} />
                    ) : (
                      <span className="portrait-placeholder">
                        <StudioIcon name="user" />
                      </span>
                    )}
                    <span className="avatar-gender">
                      {portrait.gender === 'female'
                        ? '♀'
                        : portrait.gender === 'male'
                          ? '♂'
                          : '—'}
                    </span>
                    <span
                      className={`asset-badge ${portrait.availabilityStatus === 'ready' ? 'tag-success' : portrait.availabilityStatus === 'failed' ? 'tag-danger' : 'tag-warn'}`}
                    >
                      {statusLabel[portrait.availabilityStatus]}
                    </span>
                  </div>
                  <div className="avatar-info">
                    <div>
                      <b>{portrait.name}</b>
                      <small>{genderLabel[portrait.gender]}</small>
                    </div>
                    <small>
                      {portrait.sceneTags.length
                        ? portrait.sceneTags.join(' · ')
                        : '暂无场景标签'}
                    </small>
                  </div>
                </button>
                <div className="portrait-card-actions">
                  <Button
                    disabled={
                      portrait.availabilityStatus !== 'ready' ||
                      !portrait.previewUrl
                    }
                    size="small"
                    type="text"
                    onClick={() =>
                      onOpenSpace({
                        kind: 'portrait',
                        name: portrait.name,
                        portraitId: portrait.portraitId,
                      })
                    }
                  >
                    进入形象空间
                  </Button>
                  <Button
                    type="text"
                    size="small"
                    onClick={() => openEdit(portrait)}
                  >
                    编辑
                  </Button>
                  <Popconfirm
                    title="永久删除这个形象？"
                    description="图片和形象记录会立即删除，无法恢复。"
                    okText="永久删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => void remove(portrait)}
                  >
                    <Button danger type="text" size="small">
                      删除
                    </Button>
                  </Popconfirm>
                </div>
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

      <Modal
        centered
        destroyOnHidden
        open={editorOpen}
        title={editing ? '编辑人物形象' : '新增人物形象'}
        okText={editing ? '保存' : '上传并创建'}
        cancelText="取消"
        confirmLoading={submitting}
        onCancel={closeEditor}
        onOk={() => void submit()}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ gender: 'unspecified' }}
        >
          {!editing && (
            <Form.Item label="人物照片" required>
              {fileList.length === 0 ? (
                <Upload.Dragger
                  accept={PORTRAIT_IMAGE_ACCEPT}
                  beforeUpload={() => false}
                  fileList={fileList}
                  maxCount={1}
                  showUploadList={false}
                  onChange={({ fileList: next }) => void selectFile(next)}
                >
                  <p>
                    <StudioIcon name="upload" />
                  </p>
                  <b>点击或拖拽上传一张人物照片</b>
                  <p>{PORTRAIT_IMAGE_FORMAT_MESSAGE}，最大 10MB</p>
                </Upload.Dragger>
              ) : (
                <Upload
                  className="portrait-upload-preview-list"
                  fileList={fileList}
                  listType="picture-card"
                  onPreview={(file) => void previewFile(file)}
                  onRemove={() => {
                    clearSelectedFile();
                    return true;
                  }}
                  showUploadList={{
                    showDownloadIcon: false,
                    showPreviewIcon: true,
                    showRemoveIcon: true,
                  }}
                />
              )}
            </Form.Item>
          )}
          <Form.Item
            name="name"
            label="形象名称"
            rules={[{ required: true, whitespace: true }, { max: 80 }]}
          >
            <Input placeholder="例如：亲切女主播" />
          </Form.Item>
          <Form.Item name="gender" label="性别" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'female', label: '女' },
                { value: 'male', label: '男' },
                { value: 'unspecified', label: '未指定' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="sceneTags"
            label="场景标签"
            rules={[{ type: 'array', max: 8 }]}
          >
            <Select
              mode="tags"
              tokenSeparators={[',', '，']}
              placeholder="最多 8 个，例如：带货、母婴"
            />
          </Form.Item>
          <Form.Item name="note" label="备注" rules={[{ max: 500 }]}>
            <Input.TextArea rows={3} placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        centered
        destroyOnHidden
        footer={null}
        open={previewOpen}
        title="图片预览"
        onCancel={closePreview}
      >
        {previewImage && (
          <img
            alt={`大图预览：${previewFileName}`}
            className="portrait-upload-preview-image"
            src={previewImage}
          />
        )}
      </Modal>

      <Modal
        open={Boolean(detail)}
        footer={null}
        title={detail?.name}
        onCancel={() => setDetail(undefined)}
      >
        {detail && (
          <div className="portrait-detail-live">
            {detail.previewUrl && (
              <img alt={detail.name} src={detail.previewUrl} />
            )}
            <div className="detail-grid">
              <div className="detail-cell">
                <span className="detail-cell-label">状态</span>
                <b>{statusLabel[detail.availabilityStatus]}</b>
              </div>
              <div className="detail-cell">
                <span className="detail-cell-label">性别</span>
                <b>{genderLabel[detail.gender]}</b>
              </div>
              <div className="detail-cell">
                <span className="detail-cell-label">分辨率</span>
                <b>
                  {detail.width ?? '-'}×{detail.height ?? '-'}
                </b>
              </div>
              <div className="detail-cell">
                <span className="detail-cell-label">文件</span>
                <b>
                  {detail.fileFormat?.toUpperCase() ?? '-'} ·{' '}
                  {formatSize(detail.sizeBytes)}
                </b>
              </div>
            </div>
            {detail.note && <p>{detail.note}</p>}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PortraitLibraryView;
