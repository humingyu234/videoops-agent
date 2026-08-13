import { Button, Empty, Image, Modal, Pagination, Spin } from 'antd';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { portraitApi } from '@/services/ai-video/portrait/api';
import type { Portrait } from '@/services/ai-video/portrait/types';
import { voiceApi } from '@/services/ai-video/voice/api';
import { toVoiceItem } from '@/services/ai-video/voice/adapter';
import type { Voice } from '@/services/ai-video/voice/types';
import StepFooter from '../components/StepFooter';
import StudioIcon from '../components/StudioIcon';
import type { StudioState } from '../model';
import { useVoicePlayback } from '../voices/useVoicePlayback';

const PAGE_SIZE = 6;

interface AssetSelectionStepProps {
  state: StudioState;
  update: (patch: Partial<StudioState>) => void;
  onAddAvatar: () => void;
  onAddVoice: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onFinish: () => void;
  onToast: (message: string) => void;
}

const statusText = {
  failed: '处理失败',
  processing: '处理中',
  ready: '可使用',
} as const;

const transcriptionText = {
  failed: '解析失败',
  pending: '待解析',
  ready: '已解析',
  transcribing: '解析中',
  unparsed: '未解析',
} as const;

const createKey = (prefix: string) =>
  globalThis.crypto?.randomUUID?.() ??
  `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;

const AssetSelectionStep: React.FC<AssetSelectionStepProps> = ({
  state,
  update,
  onPrevious,
  onNext,
  onFinish,
  onToast,
}) => {
  const [portraits, setPortraits] = useState<Portrait[]>([]);
  const [portraitPage, setPortraitPage] = useState(1);
  const [portraitTotal, setPortraitTotal] = useState(0);
  const [portraitLoading, setPortraitLoading] = useState(true);
  const [portraitError, setPortraitError] = useState('');
  const [voices, setVoices] = useState<Voice[]>([]);
  const [voicePage, setVoicePage] = useState(1);
  const [voiceTotal, setVoiceTotal] = useState(0);
  const [voiceLoading, setVoiceLoading] = useState(true);
  const [voiceError, setVoiceError] = useState('');
  const [uploadKind, setUploadKind] = useState<'portrait' | 'voice'>();
  const [uploadFile, setUploadFile] = useState<File>();
  const [uploadName, setUploadName] = useState('');
  const [uploadNote, setUploadNote] = useState('');
  const [uploading, setUploading] = useState(false);
  const [selectedPortrait, setSelectedPortrait] = useState<Portrait>();
  const [selectedVoice, setSelectedVoice] = useState<Voice>();
  const swipeStart = useRef<number | undefined>(undefined);
  const playback = useVoicePlayback();

  const loadPortraits = useCallback(async (page = portraitPage) => {
    setPortraitLoading(true);
    setPortraitError('');
    try {
      const result = await portraitApi.list({ pageNum: page, pageSize: PAGE_SIZE });
      setPortraits(result.rows ?? []);
      setPortraitTotal(result.total ?? 0);
      const selected = result.rows?.find((item) => item.portraitId === state.selectedAvatar);
      if (selected) setSelectedPortrait(selected);
    } catch (error) {
      setPortraitError(error instanceof Error ? error.message : '人物形象加载失败');
    } finally {
      setPortraitLoading(false);
    }
  }, [portraitPage, state.selectedAvatar]);

  const loadVoices = useCallback(async (page = voicePage) => {
    setVoiceLoading(true);
    setVoiceError('');
    try {
      const result = await voiceApi.list({ voiceType: 'origin', pageNum: page, pageSize: PAGE_SIZE });
      const rows = (result.rows ?? []).filter((item) => item.voiceType === 'origin');
      setVoices(rows);
      setVoiceTotal(result.total ?? 0);
      const selected = rows.find((item) => item.voiceId === state.selectedVoice);
      if (selected) setSelectedVoice(selected);
    } catch (error) {
      setVoiceError(error instanceof Error ? error.message : '参考声音加载失败');
    } finally {
      setVoiceLoading(false);
    }
  }, [state.selectedVoice, voicePage]);

  useEffect(() => { void loadPortraits(); }, [loadPortraits]);
  useEffect(() => { void loadVoices(); }, [loadVoices]);
  useEffect(() => playback.stop, [playback.stop]);

  const choosePortrait = (portrait: Portrait) => {
    if (portrait.availabilityStatus !== 'ready') {
      onToast(portrait.availabilityStatus === 'failed' ? '该形象处理失败' : '该形象仍在处理中');
      return;
    }
    setSelectedPortrait(portrait);
    update({
      selectedAvatar: portrait.portraitId,
      portraitImage: null,
      videoGenerationIntent: null,
      videoJob: null,
    });
  };

  const chooseVoice = (voice: Voice) => {
    setSelectedVoice(voice);
    update({
      selectedVoice: voice.voiceId,
      referenceAudio: null,
      voiceGenerationIntent: null,
      voiceJob: null,
      videoGenerationIntent: null,
      videoJob: null,
    });
  };

  const changePageBySwipe = (kind: 'portrait' | 'voice', delta: number) => {
    if (Math.abs(delta) < 48) return;
    const backwards = delta > 0;
    if (kind === 'portrait') {
      const last = Math.max(1, Math.ceil(portraitTotal / PAGE_SIZE));
      setPortraitPage((page) => Math.min(last, Math.max(1, page + (backwards ? -1 : 1))));
    } else {
      const last = Math.max(1, Math.ceil(voiceTotal / PAGE_SIZE));
      setVoicePage((page) => Math.min(last, Math.max(1, page + (backwards ? -1 : 1))));
    }
  };

  const beginUpload = (kind: 'portrait' | 'voice') => {
    setUploadKind(kind);
    setUploadFile(undefined);
    setUploadName('');
    setUploadNote('');
  };

  const saveUpload = async () => {
    if (!uploadFile) return onToast(uploadKind === 'portrait' ? '请选择人物照片' : '请选择声音文件');
    if (!uploadName.trim()) return onToast(uploadKind === 'portrait' ? '请输入形象名称' : '请输入声音名称');
    setUploading(true);
    try {
      if (uploadKind === 'portrait') {
        const asset = await portraitApi.upload(uploadFile);
        const created = await portraitApi.create({
          assetId: asset.assetId,
          name: uploadName.trim(),
          gender: 'unspecified',
          sceneTags: [],
          note: uploadNote.trim() || undefined,
          idempotencyKey: createKey('portrait'),
        });
        setSelectedPortrait(created);
        setPortraitPage(1);
        update({ selectedAvatar: created.portraitId, portraitImage: null, videoGenerationIntent: null, videoJob: null });
        await loadPortraits(1);
        onToast('形象已上传并保存到列表');
      } else {
        const created = await voiceApi.upload(uploadFile, {
          idempotencyKey: createKey('voice'),
          name: uploadName.trim(),
          gender: 'unspecified',
          tags: [],
          note: uploadNote.trim() || undefined,
          transcriptionRequested: false,
        });
        setSelectedVoice(created);
        setVoicePage(1);
        update({ selectedVoice: created.voiceId, referenceAudio: null, voiceGenerationIntent: null, voiceJob: null, videoGenerationIntent: null, videoJob: null });
        await loadVoices(1);
        onToast('原声音已上传并保存，无需等待解析');
      }
      setUploadKind(undefined);
    } catch (error) {
      Modal.error({ title: '上传失败', content: error instanceof Error ? error.message : '请稍后重试' });
    } finally {
      setUploading(false);
    }
  };

  const selectedVoiceItem = useMemo(
    () => selectedVoice ? toVoiceItem(selectedVoice) : undefined,
    [selectedVoice],
  );

  const renderPagination = (kind: 'portrait' | 'voice') => {
    const page = kind === 'portrait' ? portraitPage : voicePage;
    const total = kind === 'portrait' ? portraitTotal : voiceTotal;
    const setPage = kind === 'portrait' ? setPortraitPage : setVoicePage;
    return total > PAGE_SIZE ? (
      <Pagination
        className="asset-bottom-pagination"
        current={page}
        pageSize={PAGE_SIZE}
        showSizeChanger={false}
        simple
        total={total}
        onChange={setPage}
      />
    ) : <div className="asset-bottom-pagination-placeholder" />;
  };

  return (
    <>
      <div className="asset-selection-grid">
        <section className="asset-selection-section">
          <div className="section-head">
            <div><h2 className="section-title">人物形象</h2><div className="section-sub">每页 6 个 · 两行三列</div></div>
            <div className="asset-head-actions">
              <Button size="small" onClick={() => void loadPortraits()}><StudioIcon name="refresh" /> 刷新</Button>
              <Button size="small" type="primary" onClick={() => beginUpload('portrait')}><StudioIcon name="upload" /> 新增形象</Button>
            </div>
          </div>
          <div
            className="asset-swipe-area"
            onTouchStart={(event) => { swipeStart.current = event.touches[0]?.clientX; }}
            onTouchEnd={(event) => changePageBySwipe('portrait', (event.changedTouches[0]?.clientX ?? 0) - (swipeStart.current ?? 0))}
          >
            <Spin spinning={portraitLoading}>
              {portraitError ? <div className="asset-load-error" role="alert">{portraitError}<Button size="small" onClick={() => void loadPortraits()}>重试</Button></div>
                : !portraitLoading && portraits.length === 0 ? <Empty description="暂无人物形象" />
                  : <div className="asset-live-grid">
                    {portraits.map((portrait) => (
                      <article className={`asset-live-portrait${state.selectedAvatar === portrait.portraitId ? ' selected' : ''}`} key={portrait.portraitId}>
                        <div className="asset-live-cover">
                          {portrait.previewUrl ? <Image alt={portrait.name} preview={{ toolbarRender: () => null }} src={portrait.previewUrl} /> : <span className="portrait-placeholder"><StudioIcon name="user" /></span>}
                          <span className={`asset-badge ${portrait.availabilityStatus === 'ready' ? 'tag-success' : portrait.availabilityStatus === 'failed' ? 'tag-danger' : 'tag-warn'}`}>{statusText[portrait.availabilityStatus]}</span>
                        </div>
                        <button aria-label={`选择形象 ${portrait.name}`} disabled={portrait.availabilityStatus !== 'ready'} type="button" onClick={() => choosePortrait(portrait)}>
                          <b>{portrait.name}</b><small>{portrait.sceneTags.length ? portrait.sceneTags.join(' · ') : '暂无场景标签'}</small>
                        </button>
                      </article>
                    ))}
                  </div>}
            </Spin>
          </div>
          {renderPagination('portrait')}
        </section>

        <section className="asset-selection-section">
          <div className="section-head">
            <div><h2 className="section-title">参考声音</h2><div className="section-sub">仅显示原声音 · 每页 6 条</div></div>
            <div className="asset-head-actions">
              <Button size="small" onClick={() => void loadVoices()}><StudioIcon name="refresh" /> 刷新</Button>
              <Button size="small" type="primary" onClick={() => beginUpload('voice')}><StudioIcon name="upload" /> 新增原声音</Button>
            </div>
          </div>
          <div
            className="asset-swipe-area"
            onTouchStart={(event) => { swipeStart.current = event.touches[0]?.clientX; }}
            onTouchEnd={(event) => changePageBySwipe('voice', (event.changedTouches[0]?.clientX ?? 0) - (swipeStart.current ?? 0))}
          >
            <Spin spinning={voiceLoading}>
              {voiceError ? <div className="asset-load-error" role="alert">{voiceError}<Button size="small" onClick={() => void loadVoices()}>重试</Button></div>
                : !voiceLoading && voices.length === 0 ? <Empty description="暂无原声音" />
                  : <div className="asset-voice-list">
                    {voices.map((voice) => {
                      const item = toVoiceItem(voice);
                      const progress = playback.progressByVoice[item.id] ?? 0;
                      const playing = playback.playingVoiceId === item.id;
                      return <div className={`asset-voice-row${state.selectedVoice === voice.voiceId ? ' selected' : ''}`} key={voice.voiceId}>
                        <button aria-label={`选择声音 ${voice.name}`} className="asset-voice-select" type="button" onClick={() => chooseVoice(voice)}>
                          <span className="asset-list-thumb"><StudioIcon name="volume" /></span>
                          <span className="asset-list-main"><span className="asset-list-name">{voice.name}</span><span className="asset-list-sub">{item.meta || '原声音'} · {item.dur}</span></span>
                          <span className="asset-list-tags"><span className="tag tag-warn">原声音</span><span className="tag tag-soft">{transcriptionText[voice.transcriptionStatus]}</span></span>
                        </button>
                        <button aria-label={`${playing ? '暂停' : '播放'} ${voice.name}`} className="icon-btn asset-voice-play" type="button" onClick={() => playback.toggle(item)}><StudioIcon name={playing ? 'pause' : 'play'} /></button>
                        <button
                          aria-label={`${voice.name} 音轨`}
                          className="asset-voice-track"
                          type="button"
                          onClick={(event) => {
                            const rect = event.currentTarget.getBoundingClientRect();
                            playback.play(item, rect.width > 0 ? (event.clientX - rect.left) / rect.width : 0);
                          }}
                        ><span style={{ width: `${progress * 100}%` }} /></button>
                      </div>;
                    })}
                  </div>}
            </Spin>
          </div>
          {renderPagination('voice')}
        </section>
      </div>

      <div className="assets-summary-bar">
        <div className="assets-summary-item"><span className="assets-summary-icon"><StudioIcon name="user" /></span><div><small>形象</small><b>{selectedPortrait?.name ?? '未选择'}</b></div></div>
        <div className="assets-summary-divider" />
        <div className="assets-summary-item"><span className="assets-summary-icon"><StudioIcon name="volume" /></span><div><small>声音</small><b>{selectedVoice?.name ?? '未选择'}</b></div></div>
      </div>

      <StepFooter step={2} nextLabel="下一步：配置" nextEnabled={Boolean(selectedPortrait?.availabilityStatus === 'ready' && selectedVoiceItem)} extra={<span className="tag tag-warn">原声音上传后无需等待解析</span>} onPrevious={onPrevious} onNext={onNext} onFinish={onFinish} />

      <Modal open={Boolean(uploadKind)} title={uploadKind === 'portrait' ? '新增人物形象' : '新增原声音'} okText="上传并保存" cancelText="取消" confirmLoading={uploading} onOk={() => void saveUpload()} onCancel={() => !uploading && setUploadKind(undefined)}>
        <div className="upload-modal-content">
          <input aria-label={uploadKind === 'portrait' ? '人物照片' : '原声音文件'} accept={uploadKind === 'portrait' ? 'image/jpeg,image/png,image/webp' : '.mp3,.wav,.m4a,audio/mpeg,audio/wav,audio/mp4'} type="file" onChange={(event) => setUploadFile(event.target.files?.[0])} />
          <label className="field"><span className="field-label">名称</span><input aria-label="资源名称" className="input" value={uploadName} onChange={(event) => setUploadName(event.target.value)} /></label>
          <label className="field"><span className="field-label">描述（可选）</span><input aria-label="资源描述" className="input" value={uploadNote} onChange={(event) => setUploadNote(event.target.value)} /></label>
          {uploadKind === 'voice' && <div className="upload-warning">这里只负责上传原声音，不等待解析；需要解析时请前往声音功能模块。</div>}
        </div>
      </Modal>
    </>
  );
};

export default AssetSelectionStep;
