import { Modal } from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { ApiError, isAbortError } from '@/services/ai-video/core/errors';
import { toVoiceItem } from '@/services/ai-video/voice/adapter';
import { voiceApi } from '@/services/ai-video/voice/api';
import StudioIcon from '../components/StudioIcon';
import {
  type AssetStatus,
  VOICES,
  type VoiceItem,
  type VoiceType,
} from '../model';
import { useVoicePlayback } from './useVoicePlayback';
import VoiceCard from './VoiceCard';
import { splitVoiceSentences } from './voiceTimeline';

interface VoiceLibraryViewProps {
  onAddVoice: () => void;
  onToast: (message: string, type?: 'success' | 'error') => void;
}

interface DeleteModalInstance {
  destroy: () => void;
  update: (config: {
    cancelButtonProps: { disabled: boolean };
    keyboard: boolean;
  }) => void;
}

const PAGE_SIZE = 6;
const IS_TEST = process.env.NODE_ENV === 'test';
const SESSION_ERROR_CODES = new Set([401, 46129, 46131]);

export const filterDeletedVoices = (
  voices: VoiceItem[],
  deletedVoiceIds: ReadonlySet<string>,
) => voices.filter((voice) => !deletedVoiceIds.has(voice.id));

export const applyLoadedVoices = (
  mounted: boolean,
  voices: VoiceItem[],
  deletedVoiceIds: ReadonlySet<string>,
  commit: (voices: VoiceItem[]) => void,
) => {
  if (!mounted) return false;
  commit(filterDeletedVoices(voices, deletedVoiceIds));
  return true;
};

const VoiceLibraryView: React.FC<VoiceLibraryViewProps> = ({
  onAddVoice,
  onToast,
}) => {
  const [voices, setVoices] = useState<VoiceItem[]>(() =>
    IS_TEST
      ? VOICES.map((voice) => ({ ...voice, sents: [...voice.sents] }))
      : [],
  );
  const [loading, setLoading] = useState(!IS_TEST);
  const [loadError, setLoadError] = useState(false);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [type, setType] = useState<'all' | VoiceType>('all');
  const [status, setStatus] = useState<'all' | AssetStatus>('all');
  const [page, setPage] = useState(1);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const playback = useVoicePlayback();
  const [modal, modalContextHolder] = Modal.useModal();
  const [deletingVoiceId, setDeletingVoiceId] = useState<string | null>(null);
  const deleteFlowRef = useRef<string | null>(null);
  const deletePromiseRef = useRef<Promise<void> | null>(null);
  const deletedVoiceIdsRef = useRef<Set<string>>(new Set());
  const deleteModalRef = useRef<DeleteModalInstance | null>(null);
  const mountedRef = useRef(true);

  const loadVoices = useCallback(async () => {
    if (IS_TEST) return;
    try {
      const response = await voiceApi.list({ pageNum: 1, pageSize: 50 });
      applyLoadedVoices(
        mountedRef.current,
        response.rows.map(toVoiceItem),
        deletedVoiceIdsRef.current,
        setVoices,
      );
      if (!mountedRef.current) return;
      setLoadError(false);
    } catch {
      if (mountedRef.current) setLoadError(true);
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (IS_TEST) return;
    void loadVoices();
    const refresh = () => void loadVoices();
    window.addEventListener('aivideo:voice-changed', refresh);
    return () => window.removeEventListener('aivideo:voice-changed', refresh);
  }, [loadVoices]);

  useEffect(() => {
    if (
      IS_TEST ||
      !voices.some(
        (voice) =>
          voice.transcriptionStatus === 'pending' ||
          voice.transcriptionStatus === 'transcribing',
      )
    )
      return;
    const timer = window.setTimeout(() => void loadVoices(), 1500);
    return () => window.clearTimeout(timer);
  }, [loadVoices, voices]);

  const filtered = useMemo(() => {
    const lowerSearch = search.toLocaleLowerCase();
    return voices.filter(
      (voice) =>
        (type === 'all' || voice.type === type) &&
        (status === 'all' || voice.status === status) &&
        (!search ||
          voice.name.toLocaleLowerCase().includes(lowerSearch) ||
          voice.meta.includes(search)),
    );
  }, [search, status, type, voices]);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount);
  const pageData = filtered.slice(
    (currentPage - 1) * PAGE_SIZE,
    currentPage * PAGE_SIZE,
  );

  useEffect(() => {
    if (page > pageCount) setPage(pageCount);
  }, [page, pageCount]);

  useEffect(() => {
    if (
      playback.playingVoiceId &&
      !pageData.some((voice) => voice.id === playback.playingVoiceId)
    ) {
      playback.stop();
    }
  }, [pageData, playback.playingVoiceId, playback.stop]);

  useEffect(
    () => () => {
      if (searchTimer.current) clearTimeout(searchTimer.current);
    },
    [],
  );

  useEffect(
    () => () => {
      mountedRef.current = false;
      deleteModalRef.current?.destroy();
      deleteModalRef.current = null;
      deleteFlowRef.current = null;
      deletePromiseRef.current = null;
      deletedVoiceIdsRef.current.clear();
    },
    [],
  );

  const resetForFilter = () => {
    setPage(1);
  };

  const expand = (id: string) =>
    setExpandedIds((current) => {
      if (current.has(id)) return current;
      const next = new Set(current);
      next.add(id);
      return next;
    });

  const toggleExpanded = (id: string) => {
    if (playback.playingVoiceId === id) playback.stop();
    setExpandedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const editVoice = (voice: VoiceItem) => {
    playback.stop();
    expand(voice.id);
    setEditingId(voice.id);
    setDraft(voice.script);
  };

  const cancelEdit = (id: string) => {
    setEditingId(null);
    setDraft('');
    expand(id);
  };

  const saveEdit = async (id: string) => {
    const compact = draft.replace(/\s+/g, ' ').trim();
    if (!compact) {
      onToast('文案不能为空', 'error');
      return;
    }
    const currentVoice = voices.find((voice) => voice.id === id);
    try {
      const updated = currentVoice?.recordRevision
        ? toVoiceItem(
            await voiceApi.updateTranscript(id, {
              transcriptText: compact,
              expectedRevision: currentVoice.recordRevision,
            }),
          )
        : undefined;
      setVoices((current) =>
        current.map((voice) =>
          voice.id === id
            ? (updated ?? {
                ...voice,
                script: compact,
                sents: splitVoiceSentences(compact),
              })
            : voice,
        ),
      );
    } catch {
      onToast('文案保存失败，请刷新后重试', 'error');
      return;
    }
    setEditingId(null);
    setDraft('');
    expand(id);
    onToast('文案已保存', 'success');
  };

  const retryTranscription = async (voice: VoiceItem) => {
    if (!voice.recordRevision) return;
    try {
      const updated = toVoiceItem(
        await (voice.transcriptionStatus === 'unparsed' ? voiceApi.start(voice.id, voice.recordRevision) : voiceApi.retry(voice.id, voice.recordRevision)),
      );
      setVoices((current) =>
        current.map((item) => (item.id === voice.id ? updated : item)),
      );
      onToast(voice.transcriptionStatus === 'unparsed' ? '已开始解析' : '已重新开始解析', 'success');
    } catch {
      onToast(voice.transcriptionStatus === 'unparsed' ? '开始解析失败，请刷新后重试' : '重新解析失败，请刷新后重试', 'error');
    }
  };

  const resyncTranscription = (voice: VoiceItem) => {
    const expectedRevision = voice.recordRevision;
    if (!expectedRevision) return;
    modal.confirm({
      title: '重新同步声音文案？',
      content: '重新同步会覆盖当前文案，包括人工编辑内容。',
      okText: '确认重新同步',
      cancelText: '取消',
      onOk: async () => {
        try {
          const updated = toVoiceItem(
            await voiceApi.resync(voice.id, expectedRevision),
          );
          setVoices((current) =>
            current.map((item) => (item.id === voice.id ? updated : item)),
          );
          playback.stop();
          onToast('已重新开始同步文案', 'success');
        } catch (error) {
          onToast('重新同步失败，请刷新后重试', 'error');
          throw error;
        }
      },
    });
  };

  const deleteVoice = (voice: VoiceItem) => {
    if (deleteFlowRef.current || deletePromiseRef.current) return;
    deleteFlowRef.current = voice.id;
    const instance = modal.confirm({
      title: `删除声音“${voice.name}”？`,
      content: '删除后无法恢复，音频文件和文案将同时删除。',
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        if (deletePromiseRef.current) return deletePromiseRef.current;
        instance.update({
          cancelButtonProps: { disabled: true },
          keyboard: false,
        });
        if (mountedRef.current) setDeletingVoiceId(voice.id);
        if (playback.playingVoiceId === voice.id) playback.stop();
        const request = Promise.resolve().then(async () => {
          try {
            await voiceApi.delete(voice.id);
            if (!mountedRef.current) return;
            deletedVoiceIdsRef.current.add(voice.id);
            setVoices((current) =>
              filterDeletedVoices(current, deletedVoiceIdsRef.current),
            );
            setExpandedIds((current) => {
              const next = new Set(current);
              next.delete(voice.id);
              return next;
            });
            if (editingId === voice.id) {
              setEditingId(null);
              setDraft('');
            }
            onToast('声音已删除', 'success');
          } catch (error) {
            if (mountedRef.current && deleteModalRef.current === instance) {
              instance.update({
                cancelButtonProps: { disabled: false },
                keyboard: true,
              });
            }
            if (!isAbortError(error) && mountedRef.current) {
              if (error instanceof ApiError && error.code === 403) {
                onToast('没有删除声音的权限', 'error');
              } else if (
                !(
                  error instanceof ApiError &&
                  SESSION_ERROR_CODES.has(error.code)
                )
              ) {
                onToast('声音删除失败，请刷新后重试', 'error');
              }
            }
            throw error;
          }
        });
        const tracked = request.finally(() => {
          if (deletePromiseRef.current === tracked) {
            deletePromiseRef.current = null;
            if (
              deleteModalRef.current === null &&
              deleteFlowRef.current === voice.id
            ) {
              deleteFlowRef.current = null;
            }
            if (mountedRef.current) setDeletingVoiceId(null);
          }
        });
        deletePromiseRef.current = tracked;
        return tracked;
      },
      afterClose: () => {
        if (deleteModalRef.current === instance) deleteModalRef.current = null;
        if (
          deletePromiseRef.current === null &&
          deleteFlowRef.current === voice.id
        ) {
          deleteFlowRef.current = null;
        }
      },
    });
    deleteModalRef.current = instance;
  };

  const filterButton = <T extends string>(
    value: T,
    selected: T,
    label: string,
    change: (next: T) => void,
  ) => (
    <button
      className={value === selected ? 'active' : ''}
      type="button"
      onClick={() => {
        resetForFilter();
        change(value);
      }}
    >
      {label}
    </button>
  );

  const from = filtered.length === 0 ? 0 : (currentPage - 1) * PAGE_SIZE + 1;
  const to = Math.min(currentPage * PAGE_SIZE, filtered.length);

  return (
    <div className="library-page voice-library">
      {modalContextHolder}
      <div className="page-toolbar">
        <label className="search-box">
          <StudioIcon name="search" />
          <input
            placeholder="声音名称"
            value={searchInput}
            onChange={(event) => {
              const value = event.target.value;
              setSearchInput(value);
              setPage(1);
              if (searchTimer.current) clearTimeout(searchTimer.current);
              searchTimer.current = setTimeout(() => {
                setSearch(value);
                searchTimer.current = null;
              }, 250);
            }}
          />
        </label>
        <div className="seg">
          {filterButton('all', type, '全部类型', setType)}
          {filterButton('clone', type, '克隆', setType)}
          {filterButton('origin', type, '原声', setType)}
          {filterButton('public', type, '公共', setType)}
        </div>
        <div className="seg">
          {filterButton('all', status, '全部状态', setStatus)}
          {filterButton('verified', status, '已校验', setStatus)}
          {filterButton('pending', status, '校验中', setStatus)}
          {filterButton('failed', status, '解析失败', setStatus)}
        </div>
        <button
          aria-label="上传原声"
          className="btn btn-outline btn-sm"
          type="button"
          onClick={onAddVoice}
        >
          <StudioIcon name="upload" /> 上传原声
        </button>
        <span className="spacer" />
        <span className="toolbar-count">共 {filtered.length} 条</span>
      </div>

      <div className="library-list-title">
        <h3>声音列表</h3>
        <span className="tag tag-soft">{filtered.length}</span>
      </div>

      {loading ? (
        <div className="empty-state">
          <StudioIcon name="refresh" />
          <div className="empty-state-title">正在加载声音</div>
        </div>
      ) : loadError ? (
        <div className="empty-state">
          <StudioIcon name="warning" />
          <div className="empty-state-title">声音加载失败</div>
          <button
            className="btn btn-outline btn-sm"
            type="button"
            onClick={() => void loadVoices()}
          >
            重新加载
          </button>
        </div>
      ) : pageData.length > 0 ? (
        <div className="voice-list">
          {pageData.map((voice) => (
            <VoiceCard
              deleting={deletingVoiceId === voice.id}
              draft={editingId === voice.id ? draft : voice.script}
              editing={editingId === voice.id}
              expanded={expandedIds.has(voice.id)}
              key={voice.id}
              playing={playback.playingVoiceId === voice.id}
              progress={playback.progressByVoice[voice.id] ?? 0}
              voice={voice}
              onCancelEdit={() => cancelEdit(voice.id)}
              onDraftChange={setDraft}
              onEdit={() => editVoice(voice)}
              onDelete={() => deleteVoice(voice)}
              onPlayToggle={() => {
                expand(voice.id);
                if (playback.playingVoiceId === voice.id) playback.stop();
                else playback.play(voice, 0);
              }}
              onSaveEdit={() => saveEdit(voice.id)}
              onRetry={() => void retryTranscription(voice)}
              onResync={() => resyncTranscription(voice)}
              onSeek={(percent) => {
                expand(voice.id);
                playback.play(voice, percent);
              }}
              onToggle={() => toggleExpanded(voice.id)}
            />
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <StudioIcon name="volume" />
          <div className="empty-state-title">没有匹配的声音</div>
          <div className="empty-state-desc">试试调整筛选或上传原声音</div>
        </div>
      )}

      {pageCount > 1 && (
        <div className="pagination">
          {currentPage > 1 && (
            <button
              aria-label="上一页"
              type="button"
              onClick={() => setPage(currentPage - 1)}
            >
              <StudioIcon name="left" />
            </button>
          )}
          {Array.from({ length: pageCount }, (_, index) => index + 1).map(
            (number) => (
              <button
                aria-current={currentPage === number ? 'page' : undefined}
                className={currentPage === number ? 'active' : ''}
                key={number}
                type="button"
                onClick={() => setPage(number)}
              >
                {number}
              </button>
            ),
          )}
          <span className="pg-info">
            {from}-{to} / {filtered.length}
          </span>
        </div>
      )}
    </div>
  );
};

export default VoiceLibraryView;
