import type { UploadProps } from 'antd';
import {
  Button,
  Empty,
  Image,
  List,
  Modal,
  Pagination,
  Result,
  Segmented,
  Spin,
  Upload,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, getHttpStatus } from '@/services/ai-video/core/errors';
import type { CreationAssetsApi } from '@/services/ai-video/creation-assets/api';
import type { CreationAsset } from '@/services/ai-video/creation-assets/types';
import type {
  ImageOverlayElement,
  PipVideoElement,
  TimelineTransform,
} from '@/services/ai-video/creation-timeline/types';
import { MIN_CLIP_DURATION_MS } from './geometry';

type AssetType = CreationAsset['assetType'];
type PickerStatus = 'ready' | 'loading' | 'forbidden' | 'invalid' | 'error';

const DEFAULT_PAGE_SIZE = 12;
const DEFAULT_DISPLAY_DURATION_MS = 3_000;

const assetTypeLabel: Record<AssetType, string> = {
  image: '图片',
  video: '视频',
  audio: '音频',
};

const assetAccept: Record<AssetType, string> = {
  image: 'image/*',
  video: 'video/*',
  audio: 'audio/*',
};

function statusFor(error: unknown): PickerStatus {
  const status =
    getHttpStatus(error) ??
    (error instanceof ApiError ? error.code : undefined);
  if (status === 403) return 'forbidden';
  if (status === 404) return 'invalid';
  return 'error';
}

function newIdempotencyKey(): string {
  return (
    globalThis.crypto?.randomUUID?.() ??
    `timeline-asset-${Date.now()}-${Math.random()}`
  );
}

function defaultTransform(): TimelineTransform {
  return {
    xRatio: 0,
    yRatio: 0,
    widthRatio: 1,
    heightRatio: 1,
    rotationDeg: 0,
    opacity: 1,
  };
}

function defaultCrop() {
  return { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1 };
}

function defaultFade() {
  return { fadeInMs: 0, fadeOutMs: 0 };
}

function boundedInsertRange({
  positionMs,
  projectDurationMs,
  displayDurationMs = DEFAULT_DISPLAY_DURATION_MS,
}: {
  positionMs: number;
  projectDurationMs: number;
  displayDurationMs?: number;
}) {
  const duration = Math.floor(projectDurationMs);
  if (duration < MIN_CLIP_DURATION_MS) {
    throw new Error('Project duration must allow at least one timeline clip');
  }
  const clipDuration = Math.min(
    duration,
    Math.max(MIN_CLIP_DURATION_MS, Math.floor(displayDurationMs)),
  );
  const startMs = Math.max(
    0,
    Math.min(duration - clipDuration, Math.floor(positionMs)),
  );
  return { startMs, endMs: startMs + clipDuration };
}

export interface NewTimelineElementInput {
  asset: CreationAsset;
  elementId: string;
  positionMs: number;
  projectDurationMs: number;
  displayDurationMs?: number;
  zIndex?: number;
}

export function createImageOverlayElement({
  asset,
  elementId,
  positionMs,
  projectDurationMs,
  displayDurationMs,
  zIndex = 100,
}: NewTimelineElementInput): ImageOverlayElement {
  if (asset.assetType !== 'image') {
    throw new Error('An image asset is required for an image overlay');
  }
  return {
    elementId,
    elementType: 'image_overlay',
    ...boundedInsertRange({ positionMs, projectDurationMs, displayDurationMs }),
    zIndex,
    enabled: true,
    locked: false,
    label: asset.originalName,
    assetId: asset.assetId,
    transform: defaultTransform(),
    fitMode: 'contain',
    crop: defaultCrop(),
    fade: defaultFade(),
    sourceStartOffset: 0,
    sourceEndOffset: 0,
    adoptedPrompt: null,
    sourceTaskId: null,
  };
}

export function createPictureInPictureElement({
  asset,
  elementId,
  positionMs,
  projectDurationMs,
  displayDurationMs,
  zIndex = 250,
}: NewTimelineElementInput): PipVideoElement {
  if (
    asset.assetType !== 'video' ||
    !asset.hasVideoStream ||
    !asset.durationMs ||
    asset.durationMs <= 0
  ) {
    throw new Error(
      'A video asset with a detected duration is required for picture in picture',
    );
  }
  return {
    elementId,
    elementType: 'pip_video',
    ...boundedInsertRange({ positionMs, projectDurationMs, displayDurationMs }),
    zIndex,
    enabled: true,
    locked: false,
    label: asset.originalName,
    assetId: asset.assetId,
    transform: {
      xRatio: 0.63,
      yRatio: 0.08,
      widthRatio: 0.32,
      heightRatio: 0.32,
      rotationDeg: 0,
      opacity: 1,
    },
    fitMode: 'cover',
    crop: defaultCrop(),
    fade: defaultFade(),
    sourceDurationMs: asset.durationMs,
    sourceStartMs: 0,
    loopWhenOverflow: true,
    audioEnabled: false,
  };
}

export interface CreationAssetPickerProps {
  api: CreationAssetsApi;
  open: boolean;
  usageIntent: string;
  allowedAssetTypes?: AssetType[];
  pageSize?: number;
  selectedAssetId?: string;
  assetInvalid?: boolean;
  title?: string;
  onClose?: () => void;
  onSelect?: (asset: CreationAsset) => void;
  onAssetInvalid?: (assetId?: string) => void;
}

function Preview({ asset, url }: { asset?: CreationAsset; url?: string }) {
  if (!asset || !url) return null;
  if (asset.assetType === 'image') {
    return <Image alt={asset.originalName} preview src={url} />;
  }
  if (asset.assetType === 'video') {
    return (
      <video aria-label={`${asset.originalName} 预览`} controls src={url}>
        <track kind="captions" label="字幕" />
      </video>
    );
  }
  return (
    <audio aria-label={`${asset.originalName} 预览`} controls src={url}>
      <track kind="captions" label="字幕" />
    </audio>
  );
}

export default function CreationAssetPicker({
  api,
  open,
  usageIntent,
  allowedAssetTypes = ['image', 'video', 'audio'],
  pageSize = DEFAULT_PAGE_SIZE,
  selectedAssetId,
  assetInvalid = false,
  title = '选择创作素材',
  onClose,
  onSelect,
  onAssetInvalid,
}: CreationAssetPickerProps) {
  const initialType = allowedAssetTypes[0] ?? 'image';
  const [assetType, setAssetType] = useState<AssetType>(initialType);
  const [pageNum, setPageNum] = useState(1);
  const [rows, setRows] = useState<CreationAsset[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<PickerStatus>('loading');
  const [reloadVersion, setReloadVersion] = useState(0);
  const [uploadFailed, setUploadFailed] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string>();

  useEffect(() => {
    if (!allowedAssetTypes.includes(assetType)) {
      setAssetType(initialType);
      setPageNum(1);
    }
  }, [allowedAssetTypes, assetType, initialType]);

  useEffect(() => {
    if (!open) return undefined;
    let active = true;
    setStatus(assetInvalid ? 'invalid' : 'loading');
    if (assetInvalid) return undefined;
    api
      .list({ assetType, pageNum, pageSize })
      .then((page) => {
        if (!active) return;
        setRows(page.rows);
        setTotal(page.total);
        setStatus('ready');
      })
      .catch((error: unknown) => {
        if (!active) return;
        const nextStatus = statusFor(error);
        setStatus(nextStatus);
        if (nextStatus === 'invalid') onAssetInvalid?.(selectedAssetId);
      });
    return () => {
      active = false;
    };
  }, [
    api,
    assetInvalid,
    assetType,
    onAssetInvalid,
    open,
    pageNum,
    pageSize,
    reloadVersion,
    selectedAssetId,
  ]);

  const selectedAsset = useMemo(
    () => rows.find((asset) => asset.assetId === selectedAssetId),
    [rows, selectedAssetId],
  );

  useEffect(() => {
    let active = true;
    let objectUrl: string | undefined;
    setPreviewUrl(undefined);
    if (!open || !selectedAsset) return undefined;
    api
      .content(selectedAsset.assetId)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setPreviewUrl(objectUrl);
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (statusFor(error) === 'invalid') {
          setStatus('invalid');
          onAssetInvalid?.(selectedAsset.assetId);
        } else if (statusFor(error) === 'forbidden') {
          setStatus('forbidden');
        } else {
          setStatus('error');
        }
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [api, onAssetInvalid, open, selectedAsset]);

  const reload = useCallback(() => {
    setReloadVersion((version) => version + 1);
  }, []);

  const customRequest = useCallback<NonNullable<UploadProps['customRequest']>>(
    async ({ file, onError, onProgress, onSuccess }) => {
      setUploadFailed(false);
      onProgress?.({ percent: 1 });
      try {
        const uploaded = await api.upload(file as File, {
          usageIntent,
          idempotencyKey: newIdempotencyKey(),
        });
        onProgress?.({ percent: 100 });
        onSuccess?.(uploaded);
        onSelect?.(uploaded);
        reload();
      } catch (error) {
        setUploadFailed(true);
        onError?.(error as Error);
      }
    },
    [api, onSelect, reload, usageIntent],
  );

  const content = () => {
    if (status === 'loading') return <Spin aria-label="正在加载素材" />;
    if (status === 'forbidden') {
      return <Result status="403" title="没有素材访问权限" />;
    }
    if (status === 'invalid') {
      return <Result status="warning" title="素材已失效，无法合成" />;
    }
    if (status === 'error') {
      return (
        <Result
          extra={<Button onClick={reload}>重试</Button>}
          status="error"
          title="素材加载失败"
        />
      );
    }
    return (
      <>
        <List
          dataSource={rows}
          locale={{ emptyText: <Empty description="没有可用素材" /> }}
          renderItem={(asset) => (
            <List.Item
              actions={[
                <Button
                  aria-label={`选择素材 ${asset.originalName}`}
                  key="select"
                  type={
                    selectedAssetId === asset.assetId ? 'primary' : 'default'
                  }
                  onClick={() => onSelect?.(asset)}
                >
                  选择
                </Button>,
              ]}
            >
              <List.Item.Meta
                description={assetTypeLabel[asset.assetType]}
                title={asset.originalName}
              />
            </List.Item>
          )}
        />
        {total > pageSize && (
          <Pagination
            current={pageNum}
            pageSize={pageSize}
            showSizeChanger={false}
            total={total}
            onChange={(page) => setPageNum(page)}
          />
        )}
        {selectedAsset && (
          <aside aria-label="素材预览">
            <Preview asset={selectedAsset} url={previewUrl} />
          </aside>
        )}
      </>
    );
  };

  return (
    <Modal
      destroyOnHidden={false}
      footer={null}
      open={open}
      title={title}
      onCancel={onClose}
    >
      <Segmented
        options={allowedAssetTypes.map((type) => ({
          label: assetTypeLabel[type],
          value: type,
        }))}
        value={assetType}
        onChange={(value) => {
          setAssetType(value as AssetType);
          setPageNum(1);
        }}
      />
      <Upload
        accept={assetAccept[assetType]}
        customRequest={customRequest}
        maxCount={1}
        showUploadList
      >
        <Button>上传素材</Button>
      </Upload>
      {uploadFailed && <p role="alert">上传素材失败</p>}
      {content()}
    </Modal>
  );
}
