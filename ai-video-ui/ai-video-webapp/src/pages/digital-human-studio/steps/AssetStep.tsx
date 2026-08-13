import React, { useRef } from 'react';
import StepFooter from '../components/StepFooter';
import StudioIcon from '../components/StudioIcon';
import { AVATARS, type StudioState, VOICES } from '../model';

interface AssetStepProps {
  state: StudioState;
  update: (patch: Partial<StudioState>) => void;
  onAddAvatar: () => void;
  onAddVoice: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onFinish: () => void;
  onToast: (message: string) => void;
}

const voiceTypeLabel = {
  clone: '克隆',
  origin: '原声音',
  public: '公共',
} as const;

const AssetStep: React.FC<AssetStepProps> = ({
  state,
  update,
  onAddAvatar,
  onAddVoice,
  onPrevious,
  onNext,
  onFinish,
  onToast,
}) => {
  const avatarTrackRef = useRef<HTMLDivElement>(null);
  const selectedAvatar = AVATARS.find(
    (avatar) => avatar.id === state.selectedAvatar,
  );
  const selectedVoice = VOICES.find(
    (voice) => voice.id === state.selectedVoice,
  );

  const selectAvatar = (avatar: (typeof AVATARS)[number]) => {
    if (avatar.status === 'pending') {
      onToast('该形象仍在校验中');
      return;
    }
    update({
      selectedAvatar: avatar.id,
      portraitImage: null,
      videoGenerationIntent: null,
      videoJob: null,
    });
  };

  const selectVoice = (voice: (typeof VOICES)[number]) => {
    update({
      selectedVoice: voice.id,
      referenceAudio: null,
      voiceGenerationIntent: null,
      voiceJob: null,
      videoGenerationIntent: null,
      videoJob: null,
    });
  };

  const scrollAvatars = (direction: -1 | 1) => {
    avatarTrackRef.current?.scrollBy({
      behavior: 'smooth',
      left: direction * 196,
    });
  };

  return (
    <>
      <div className="asset-selection-grid">
        <section className="asset-selection-section">
          <div className="section-head">
            <div>
              <h2 className="section-title">人物形象</h2>
              <div className="section-sub">从形象库选择，或直接新增</div>
            </div>
            <button
              aria-label="新增形象"
              className="btn btn-outline btn-sm"
              type="button"
              onClick={onAddAvatar}
            >
              <StudioIcon name="upload" /> 新增形象
            </button>
          </div>

          <div className="asset-avatar-scroller">
            <button
              aria-label="上一个形象"
              className="asset-scroller-button"
              type="button"
              onClick={() => scrollAvatars(-1)}
            >
              <StudioIcon name="left" />
            </button>
            <div className="asset-avatar-track" ref={avatarTrackRef}>
              {AVATARS.map((avatar) => (
                <button
                  aria-pressed={state.selectedAvatar === avatar.id}
                  aria-label={`选择形象 ${avatar.name}`}
                  className="asset-avatar-card"
                  key={avatar.id}
                  type="button"
                  onClick={() => selectAvatar(avatar)}
                >
                  {state.selectedAvatar === avatar.id && (
                    <span className="asset-badge selected">
                      <StudioIcon name="check" /> 已选
                    </span>
                  )}
                  <span
                    className="asset-avatar-thumb"
                    style={{ background: avatar.style }}
                  >
                    <StudioIcon name="user" />
                    <span className="asset-avatar-gender">
                      {avatar.gender === 'female' ? '女' : '男'}
                    </span>
                  </span>
                  <span className="asset-avatar-body">
                    <span className="asset-avatar-name">{avatar.name}</span>
                    <span className="asset-avatar-meta">{avatar.scenes}</span>
                  </span>
                </button>
              ))}
            </div>
            <button
              aria-label="下一个形象"
              className="asset-scroller-button"
              type="button"
              onClick={() => scrollAvatars(1)}
            >
              <StudioIcon name="right" />
            </button>
          </div>
        </section>

        <section className="asset-selection-section">
          <div className="section-head">
            <div>
              <h2 className="section-title">参考声音</h2>
              <div className="section-sub">
                原声音将用于克隆，需绑定当前文案
              </div>
            </div>
            <button
              aria-label="新增原声音"
              className="btn btn-outline btn-sm"
              type="button"
              onClick={onAddVoice}
            >
              <StudioIcon name="upload" /> 新增原声音
            </button>
          </div>

          <fieldset aria-label="声音类型" className="asset-voice-chips">
            <button className="q-chip" type="button">
              全部
            </button>
            <button
              aria-pressed="true"
              className="q-chip selected"
              type="button"
            >
              原声音
            </button>
            <button className="q-chip" type="button">
              克隆声音
            </button>
          </fieldset>

          <div className="asset-voice-list">
            {VOICES.map((voice) => {
              const isSelected = state.selectedVoice === voice.id;
              return (
                <div
                  className={`asset-voice-row${isSelected ? ' selected' : ''}`}
                  key={voice.id}
                >
                  <button
                    aria-pressed={isSelected}
                    aria-label={`选择声音 ${voice.name}`}
                    className="asset-voice-select"
                    type="button"
                    onClick={() => selectVoice(voice)}
                  >
                    <span className="asset-list-thumb">
                      <StudioIcon name="volume" />
                    </span>
                    <span className="asset-list-main">
                      <span className="asset-list-name">
                        {voice.name}
                        {isSelected && <StudioIcon name="check" />}
                      </span>
                      <span className="asset-list-sub">
                        {voice.meta} · {voice.dur}
                      </span>
                    </span>
                    <span className="asset-list-tags">
                      <span
                        className={`tag ${
                          voice.type === 'origin'
                            ? 'tag-warn'
                            : voice.type === 'clone'
                              ? 'tag-success'
                              : 'tag-soft'
                        }`}
                      >
                        {voiceTypeLabel[voice.type]}
                      </span>
                      <span
                        className={`tag ${
                          voice.status === 'verified'
                            ? 'tag-success'
                            : 'tag-warn'
                        }`}
                      >
                        {voice.status === 'verified' ? '已校验' : '校验中'}
                      </span>
                    </span>
                  </button>
                  <button
                    aria-label={`试听 ${voice.name}`}
                    className="icon-btn asset-voice-play"
                    title="试听"
                    type="button"
                    onClick={() => onToast(`试听：${voice.name}`)}
                  >
                    <StudioIcon name="play" />
                  </button>
                </div>
              );
            })}
          </div>
        </section>
      </div>

      <div className="assets-summary-bar">
        <div className="assets-summary-item">
          <span className="assets-summary-icon">
            <StudioIcon name="user" />
          </span>
          <div>
            <small>形象</small>
            <b>
              {selectedAvatar?.name ?? '未选择'}
              {selectedAvatar && (
                <span className="tag tag-success">
                  {selectedAvatar.status === 'verified' ? '已校验' : '校验中'}
                </span>
              )}
            </b>
          </div>
        </div>
        <div className="assets-summary-divider" />
        <div className="assets-summary-item">
          <span className="assets-summary-icon">
            <StudioIcon name="volume" />
          </span>
          <div>
            <small>声音</small>
            <b>
              {selectedVoice?.name ?? '未选择'}
              {selectedVoice && (
                <span
                  className={`tag ${
                    selectedVoice.type === 'origin' ? 'tag-warn' : 'tag-success'
                  }`}
                >
                  {selectedVoice.type === 'origin' ? '原声音' : '克隆声音'}
                </span>
              )}
            </b>
          </div>
        </div>
      </div>

      <StepFooter
        step={2}
        nextLabel="去生成声音"
        nextEnabled={Boolean(
          selectedAvatar?.status === 'verified' && selectedVoice,
        )}
        extra={
          <span className="tag tag-warn">
            {selectedVoice?.type === 'origin'
              ? '原声音需先克隆'
              : '克隆声音可直接试听'}
          </span>
        }
        onPrevious={onPrevious}
        onNext={onNext}
        onFinish={onFinish}
      />
    </>
  );
};

export { AssetStep as LegacyAssetStep };
export { default } from '../asset-selection/AssetSelectionStep';
