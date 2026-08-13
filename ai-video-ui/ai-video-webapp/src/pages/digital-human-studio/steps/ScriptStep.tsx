import React from 'react';
import StepFooter from '../components/StepFooter';
import StudioIcon from '../components/StudioIcon';
import { INDUSTRIES, type StudioState } from '../model';
import { isQuestionnaireOtherOption } from '../questionnaireAnswers';

interface ScriptStepProps {
  state: StudioState;
  update: (patch: Partial<StudioState>) => void;
  onPrevious: () => void;
  onNext: () => void;
  onFinish: () => void;
  onToast: (message: string) => void;
}

const ScriptStep: React.FC<ScriptStepProps> = ({
  state,
  update,
  onPrevious,
  onNext,
  onFinish,
  onToast,
}) => {
  const version = state.scriptVersions[state.selectedScript];
  const body = state.scriptBodies[state.selectedScript] ?? version?.body ?? '';
  const wordCount = body.replace(/\s+/g, '').length;
  const industry =
    state.industry === 'custom'
      ? state.customIndustry
      : (INDUSTRIES.find((item) => item.id === state.industry)?.name ?? '—');
  const purpose =
    state.purpose === '__custom' ? state.customPurpose : (state.purpose ?? '—');

  const changeBody = (value: string) => {
    const scriptBodies = [...state.scriptBodies];
    scriptBodies[state.selectedScript] = value;
    update({ scriptBodies });
  };

  return (
    <>
      <div className="script-grid">
        <div className="card script-editor-card">
          <div className="tabs">
            {state.scriptVersions.map((item, index) => (
              <button
                className={`tab ${
                  state.selectedScript === index ? 'active' : ''
                }`}
                key={item.title}
                type="button"
                onClick={() => update({ selectedScript: index })}
              >
                版本{index + 1} · {item.title}
              </button>
            ))}
          </div>
          <textarea
            className="textarea script-body"
            value={body}
            onChange={(event) => changeBody(event.target.value)}
          />
          <div className="script-editor-foot">
            <div>
              <span>
                字数 <b>{wordCount}</b>
              </span>
              <span>
                预估 <b>{version ? `${version.durationSeconds} 秒` : '—'}</b>
              </span>
              <span>
                分段 <b>{body.split('\n\n').length}</b>
              </span>
            </div>
            <button
              className="btn btn-ghost btn-sm"
              type="button"
              onClick={() => {
                void navigator.clipboard?.writeText(body);
                onToast('文案已复制到剪贴板');
              }}
            >
              <StudioIcon name="copy" /> 复制
            </button>
          </div>
        </div>
        <aside className="script-recap">
          <div className="card card-tight">
            <div className="prop-title">上一步选择</div>
            <div className="prop-row">
              <span className="prop-label">行业</span>
              <span>{industry}</span>
            </div>
            <div className="prop-row">
              <span className="prop-label">视频用途</span>
              <span>{purpose}</span>
            </div>
            <div className="prop-row">
              <span className="prop-label">目标时长</span>
              <span className="numeric">{state.duration} 秒</span>
            </div>
            {state.supplement && (
              <div className="prop-stack">
                <span className="prop-label">补充要求</span>
                <span>{state.supplement}</span>
              </div>
            )}
          </div>
          <div className="card card-tight">
            <div className="prop-title">AI问卷</div>
            <div className="survey-recap">
              {state.questionnaire?.questions.length ? (
                state.questionnaire.questions.map((question, index) => {
                  const selectedValues = state.survey[question.id] ?? [];
                  return (
                    <div className="survey-recap-row" key={question.id}>
                      <div className="survey-recap-q">
                        <span>{index + 1}</span>
                        {question.title}
                      </div>
                      <div className="survey-recap-opts">
                        {(question.options ?? []).map((option) => {
                          const selected = selectedValues.includes(
                            option.value,
                          );
                          const otherAnswer =
                            state.surveyOtherAnswers[question.id]?.trim();
                          const label =
                            selected &&
                            otherAnswer &&
                            isQuestionnaireOtherOption(option)
                              ? otherAnswer
                              : option.label;
                          return (
                            <i
                              className={selected ? 'selected' : ''}
                              key={option.value}
                            >
                              {label}
                            </i>
                          );
                        })}
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="section-placeholder">未填写问卷</div>
              )}
            </div>
          </div>
        </aside>
      </div>
      <StepFooter
        step={1}
        nextLabel="确认文案，去选形象"
        nextEnabled={Boolean(version && body.trim())}
        onPrevious={onPrevious}
        onNext={onNext}
        onFinish={onFinish}
      />
    </>
  );
};

export default ScriptStep;
