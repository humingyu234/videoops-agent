import React, {
  type ChangeEvent,
  type KeyboardEvent,
  useEffect,
  useRef,
  useState,
} from 'react';
import { portraitApi } from '@/services/ai-video/portrait/api';
import {
  isSupportedPortraitImage,
  PORTRAIT_IMAGE_ACCEPT,
  PORTRAIT_IMAGE_FORMAT_MESSAGE,
} from '../utils/portraitImageFile';
import type { AvatarSpaceSource } from './model';

interface AvatarSpaceViewProps {
  initialAvatar?: AvatarSpaceSource;
  onBack: () => void;
  onToast: (message: string, type?: 'success' | 'error') => void;
}

type SpaceIconName =
  | 'arrow'
  | 'attach'
  | 'close'
  | 'glasses'
  | 'hair'
  | 'info'
  | 'palette'
  | 'send'
  | 'shirt'
  | 'spark';

interface ResolvedAvatar {
  image: string;
  name: string;
}

type ChatMessage =
  | { id: string; role: 'upload'; image: string; name: string }
  | { id: string; role: 'user'; text: string }
  | { id: string; role: 'ai'; text: string };

const QUICK_STARTS: Array<{
  description: string;
  icon: SpaceIconName;
  title: string;
}> = [
  { icon: 'glasses', title: '换配饰', description: '眼镜、帽子、耳环等' },
  { icon: 'hair', title: '改发型', description: '短发、长发、卷发' },
  { icon: 'shirt', title: '换服装', description: '西装、休闲、礼服' },
  { icon: 'palette', title: '调风格', description: '商务、年轻、国风' },
];

const FOLLOWUPS_INIT = ['换成短发', '戴圆框眼镜', '风格更商务', '肤色更白一点'];
const FOLLOWUPS_MORE = [
  '换个深色背景',
  '穿西装外套',
  '微笑表情',
  '风格更年轻',
  '加上珍珠耳环',
  '换成侧脸角度',
];

const iconPaths: Record<SpaceIconName, React.ReactNode> = {
  arrow: <path d="M5 12h14M13 6l6 6-6 6" />,
  attach: (
    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
  ),
  close: <path d="M18 6 6 18M6 6l12 12" />,
  glasses: (
    <>
      <circle cx="6.5" cy="14" r="2.5" />
      <circle cx="17.5" cy="14" r="2.5" />
      <path d="M9 14h6" />
    </>
  ),
  hair: (
    <path d="M12 3c-4 0-7 3-7 7v4M12 3c4 0 7 3 7 7v4M7 14c0 2 2 4 5 4s5-2 5-4" />
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="10" />
      <path d="M12 16v-4M12 8h.01" />
    </>
  ),
  palette: (
    <>
      <circle cx="12" cy="12" r="9" />
      <circle cx="8" cy="10" r="1.2" />
      <circle cx="12" cy="8" r="1.2" />
      <circle cx="16" cy="10" r="1.2" />
    </>
  ),
  send: <path d="M12 19V5M5 12l7-7 7 7" />,
  shirt: <path d="m20 7-4-3-4 2-4-2-4 3 2 4h2v8h8v-8h2Z" />,
  spark: (
    <>
      <path d="M12 2a3 3 0 0 1 3 3v1a3 3 0 0 1-3 3 3 3 0 0 1-3-3V5a3 3 0 0 1 3-3ZM5 21c0-4 3-7 7-7s7 3 7 7" />
      <path d="m19 8 1 2 2 1-2 1-1 2-1-2-2-1 2-1Z" />
    </>
  ),
};

const SpaceIcon: React.FC<{ name: SpaceIconName }> = ({ name }) => (
  <svg
    aria-hidden="true"
    fill="none"
    stroke="currentColor"
    strokeLinecap="round"
    strokeLinejoin="round"
    strokeWidth="1.7"
    viewBox="0 0 24 24"
  >
    {iconPaths[name]}
  </svg>
);

const makeId = () => Math.random().toString(36).slice(2, 10);

const createLoadedMessages = (avatar: ResolvedAvatar): ChatMessage[] => [
  {
    id: makeId(),
    role: 'upload',
    image: avatar.image,
    name: avatar.name,
  },
  {
    id: makeId(),
    role: 'ai',
    text: `已加载形象「${avatar.name}」。现在告诉我你想怎么改吧！比如发型、眼镜、服装、风格…`,
  },
];

const AvatarMark: React.FC<{ hidden?: boolean; user?: boolean }> = ({
  hidden,
  user,
}) => (
  <div
    aria-hidden="true"
    className={`avatar-space-message-avatar ${user ? 'is-user' : 'is-ai'}`}
    style={hidden ? { visibility: 'hidden' } : undefined}
  >
    {user ? '我' : <SpaceIcon name="spark" />}
  </div>
);

const AvatarSpaceView: React.FC<AvatarSpaceViewProps> = ({
  initialAvatar,
  onBack,
  onToast,
}) => {
  const [sourceAvatar, setSourceAvatar] = useState<ResolvedAvatar>();
  const [sourceLoading, setSourceLoading] = useState(false);
  const [sourceError, setSourceError] = useState('');
  const [accessAttempt, setAccessAttempt] = useState(0);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [activity, setActivity] = useState<'typing'>();
  const [lightboxImage, setLightboxImage] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const textAreaRef = useRef<HTMLTextAreaElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const timersRef = useRef<number[]>([]);
  const sourceRequestRef = useRef(0);
  const sourceRevisionRef = useRef(0);

  const isGenerating = Boolean(activity);
  const lastMessage = messages.at(-1);
  const showFollowups =
    !isGenerating && sourceAvatar && lastMessage?.role === 'ai';
  const followups =
    messages.filter((message) => message.role === 'user').length <= 1
      ? FOLLOWUPS_INIT
      : FOLLOWUPS_MORE;

  const schedule = (
    callback: () => void,
    delay: number,
    revision = sourceRevisionRef.current,
  ) => {
    const timer = window.setTimeout(() => {
      timersRef.current = timersRef.current.filter((item) => item !== timer);
      if (sourceRevisionRef.current === revision) callback();
    }, delay);
    timersRef.current.push(timer);
    return timer;
  };

  const scrollToBottom = () => {
    window.requestAnimationFrame(() => {
      const scroll = scrollRef.current;
      if (scroll) scroll.scrollTop = scroll.scrollHeight;
    });
  };

  useEffect(() => {
    scrollToBottom();
  }, [activity, messages]);

  useEffect(() => {
    if (!initialAvatar) return undefined;

    const requestRevision = sourceRequestRef.current + 1;
    sourceRequestRef.current = requestRevision;
    sourceRevisionRef.current += 1;
    timersRef.current.forEach((timer) => {
      window.clearTimeout(timer);
    });
    timersRef.current = [];
    setActivity(undefined);
    setSourceError('');

    if (initialAvatar.kind === 'local') {
      const avatar = { image: initialAvatar.image, name: initialAvatar.name };
      setSourceAvatar(avatar);
      setMessages(createLoadedMessages(avatar));
      setSourceLoading(false);
      return undefined;
    }

    setSourceAvatar(undefined);
    setMessages([]);
    setSourceLoading(true);
    void portraitApi
      .accessUrl(initialAvatar.portraitId)
      .then(({ url }) => {
        if (sourceRequestRef.current !== requestRevision) return;
        const avatar = { image: url, name: initialAvatar.name };
        setSourceAvatar(avatar);
        setMessages(createLoadedMessages(avatar));
        setSourceLoading(false);
      })
      .catch(() => {
        if (sourceRequestRef.current !== requestRevision) return;
        setSourceLoading(false);
        setSourceError('形象图片加载失败，请重新加载');
      });

    return () => {
      if (sourceRequestRef.current === requestRevision) {
        sourceRequestRef.current += 1;
      }
    };
  }, [accessAttempt, initialAvatar]);

  useEffect(
    () => () => {
      timersRef.current.forEach((timer) => {
        window.clearTimeout(timer);
      });
    },
    [],
  );

  useEffect(() => {
    const close = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') setLightboxImage('');
    };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, []);

  const updateInput = (value: string) => {
    setInput(value);
    const field = textAreaRef.current;
    if (field) {
      field.style.height = 'auto';
      field.style.height = `${Math.min(field.scrollHeight, 160)}px`;
    }
  };

  const handleSuggestion = (text: string) => {
    if (!sourceAvatar) {
      onToast('请先上传一张形象');
      return;
    }
    updateInput(text.startsWith('帮我') ? text : `帮我${text}`);
    textAreaRef.current?.focus();
  };

  const handleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (!isSupportedPortraitImage(file)) {
      onToast(PORTRAIT_IMAGE_FORMAT_MESSAGE, 'error');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      onToast('图片不能超过 10MB', 'error');
      return;
    }

    sourceRevisionRef.current += 1;
    const sourceRevision = sourceRevisionRef.current;
    sourceRequestRef.current += 1;
    timersRef.current.forEach((timer) => {
      window.clearTimeout(timer);
    });
    timersRef.current = [];
    setActivity(undefined);

    const reader = new FileReader();
    reader.onload = () => {
      if (sourceRevisionRef.current !== sourceRevision) return;
      const image = String(reader.result ?? '');
      const name = file.name.replace(/\.[^.]+$/, '').slice(0, 20) || '我的形象';
      const avatar = { image, name };
      setSourceAvatar(avatar);
      setMessages([{ id: makeId(), role: 'upload', image, name }]);

      schedule(
        () => {
          setActivity('typing');
          schedule(
            () => {
              setActivity(undefined);
              setMessages((current) => [
                ...current,
                {
                  id: makeId(),
                  role: 'ai',
                  text: `已加载形象「${name}」。现在告诉我你想怎么改吧！比如发型、眼镜、服装、风格…`,
                },
              ]);
            },
            800,
            sourceRevision,
          );
        },
        200,
        sourceRevision,
      );
      textAreaRef.current?.focus();
    };
    reader.onerror = () => {
      if (sourceRevisionRef.current !== sourceRevision) return;
      onToast('图片读取失败，请重新选择', 'error');
    };
    reader.readAsDataURL(file);
  };

  const sendMessage = () => {
    const prompt = input.trim();
    if (!prompt || isGenerating || !sourceAvatar) return;

    setMessages((current) => [
      ...current,
      { id: makeId(), role: 'user', text: prompt },
    ]);
    updateInput('');
    setActivity('typing');

    const sourceRevision = sourceRevisionRef.current;
    schedule(
      () => {
        setMessages((current) => [
          ...current,
          {
            id: makeId(),
            role: 'ai',
            text: '形象修改功能建设中，本次描述未提交，也不会生成或保存业务结果。',
          },
        ]);
        setActivity(undefined);
      },
      800,
      sourceRevision,
    );
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      sendMessage();
    }
  };

  return (
    <section className="avatar-space-view">
      <header className="avatar-space-topbar">
        <button
          aria-label="返回形象列表"
          className="avatar-space-back"
          type="button"
          onClick={onBack}
        >
          <svg
            aria-hidden="true"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            viewBox="0 0 24 24"
          >
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
        </button>
        <div className="avatar-space-topbar-copy">
          <div className="avatar-space-title">形象空间</div>
          <div className="avatar-space-subtitle">
            上传形象 · 对话式修改功能建设中
          </div>
        </div>
        <div className="avatar-space-status">演示</div>
      </header>

      <div ref={scrollRef} className="avatar-space-chat-scroll">
        <div className="avatar-space-chat-inner">
          {sourceLoading ? (
            <div className="avatar-space-welcome" role="status">
              <div className="avatar-space-welcome-icon">
                <SpaceIcon name="spark" />
              </div>
              <h1>正在加载形象</h1>
              <p>正在获取安全访问地址…</p>
            </div>
          ) : sourceError ? (
            <div className="avatar-space-welcome" role="alert">
              <div className="avatar-space-welcome-icon">
                <SpaceIcon name="info" />
              </div>
              <h1>形象图片加载失败</h1>
              <p>{sourceError}</p>
              <button
                className="avatar-space-button is-primary"
                type="button"
                onClick={() => setAccessAttempt((attempt) => attempt + 1)}
              >
                重新加载
              </button>
            </div>
          ) : messages.length === 0 && !sourceAvatar ? (
            <div className="avatar-space-welcome">
              <div className="avatar-space-welcome-icon">
                <SpaceIcon name="spark" />
              </div>
              <h1>形象空间</h1>
              <p>上传一张形象，预览对话式修改界面（功能建设中）</p>
              <div className="avatar-space-quick-cards">
                {QUICK_STARTS.map((quickStart) => (
                  <button
                    key={quickStart.title}
                    className="avatar-space-quick-card"
                    type="button"
                    onClick={() => handleSuggestion(quickStart.title)}
                  >
                    <span className="avatar-space-quick-icon">
                      <SpaceIcon name={quickStart.icon} />
                    </span>
                    <span className="avatar-space-quick-copy">
                      <strong>{quickStart.title}</strong>
                      <small>{quickStart.description}</small>
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <>
              {messages.map((message) => {
                if (message.role === 'upload') {
                  return (
                    <div
                      key={message.id}
                      className="avatar-space-message is-user"
                    >
                      <AvatarMark user />
                      <div className="avatar-space-message-body">
                        <strong className="avatar-space-message-author">
                          你
                        </strong>
                        <button
                          aria-label="放大上传形象"
                          className="avatar-space-upload-image"
                          type="button"
                          onClick={() => setLightboxImage(message.image)}
                        >
                          <img alt="上传形象" src={message.image} />
                        </button>
                        <div className="avatar-space-upload-foot">
                          <span>{message.name}</span>
                          <span className="avatar-space-muted-tag">
                            原始形象
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                }

                return (
                  <div
                    key={message.id}
                    className={`avatar-space-message is-${message.role}`}
                  >
                    <AvatarMark user={message.role === 'user'} />
                    <div className="avatar-space-message-body">
                      <strong className="avatar-space-message-author">
                        {message.role === 'ai' ? '形象设计师' : '你'}
                      </strong>
                      <div className="avatar-space-message-content">
                        <p>{message.text}</p>
                      </div>
                    </div>
                  </div>
                );
              })}

              {activity === 'typing' && (
                <div className="avatar-space-message is-ai">
                  <AvatarMark />
                  <div className="avatar-space-message-body">
                    <strong className="avatar-space-message-author">
                      形象设计师
                    </strong>
                    <div
                      aria-label="形象设计师正在输入"
                      className="avatar-space-typing"
                      role="status"
                    >
                      <span />
                      <span />
                      <span />
                    </div>
                  </div>
                </div>
              )}

              {showFollowups && (
                <div className="avatar-space-message is-ai avatar-space-followup-row">
                  <AvatarMark hidden />
                  <div className="avatar-space-message-body">
                    <div className="avatar-space-followups">
                      {followups.map((followup) => (
                        <button
                          key={followup}
                          className="avatar-space-followup"
                          type="button"
                          onClick={() => {
                            updateInput(followup);
                            textAreaRef.current?.focus();
                          }}
                        >
                          <SpaceIcon name="arrow" />
                          {followup}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <div className="avatar-space-input-wrap">
        <div className="avatar-space-input-bar">
          {sourceAvatar && (
            <div className="avatar-space-current-row">
              <span className="avatar-space-current-chip">
                <img alt="当前形象" src={sourceAvatar.image} />
                <span>当前形象：</span>
                <strong>{sourceAvatar.name}</strong>
              </span>
            </div>
          )}
          <div className="avatar-space-input-inner">
            <button
              aria-label="上传形象"
              className="avatar-space-attach"
              title="上传形象"
              type="button"
              onClick={() => fileInputRef.current?.click()}
            >
              <SpaceIcon name="attach" />
            </button>
            <textarea
              ref={textAreaRef}
              aria-label="形象修改描述"
              className="avatar-space-textarea"
              placeholder="描述你想要的修改…"
              rows={1}
              value={input}
              onChange={(event) => updateInput(event.target.value)}
              onKeyDown={handleKeyDown}
            />
            <button
              aria-label="发送修改描述"
              className="avatar-space-send"
              disabled={!input.trim() || isGenerating || !sourceAvatar}
              type="button"
              onClick={sendMessage}
            >
              <SpaceIcon name="send" />
            </button>
          </div>
          <div className="avatar-space-input-hint">
            <SpaceIcon name="info" />
            点击左侧回形针上传形象 · Enter 发送 · Shift+Enter 换行
          </div>
        </div>
      </div>

      <input
        ref={fileInputRef}
        aria-label="选择形象图片"
        accept={PORTRAIT_IMAGE_ACCEPT}
        hidden
        type="file"
        onChange={handleFile}
      />

      {lightboxImage && (
        <div
          aria-label="图片大图预览"
          className="avatar-space-lightbox"
          role="dialog"
          onClick={() => setLightboxImage('')}
        >
          <button
            aria-label="关闭大图预览"
            className="avatar-space-lightbox-close"
            type="button"
            onClick={() => setLightboxImage('')}
          >
            <SpaceIcon name="close" />
          </button>
          <img
            alt="形象大图预览"
            src={lightboxImage}
            onClick={(event) => event.stopPropagation()}
          />
        </div>
      )}
    </section>
  );
};

export default AvatarSpaceView;
