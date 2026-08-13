import React from 'react';
import StudioIcon from './StudioIcon';

interface StepFooterProps {
  step: number;
  nextLabel?: string;
  nextEnabled?: boolean;
  extra?: React.ReactNode;
  onPrevious: () => void;
  onNext: () => void;
  onFinish: () => void;
}

const StepFooter: React.FC<StepFooterProps> = ({
  step,
  nextLabel = '下一步',
  nextEnabled = true,
  extra,
  onPrevious,
  onNext,
  onFinish,
}) => (
  <div className="step-footer">
    {step > 0 ? (
      <button className="btn btn-outline" type="button" onClick={onPrevious}>
        <StudioIcon name="left" /> 上一步
      </button>
    ) : (
      <span />
    )}
    <div className="step-footer-actions">
      {extra}
      {step < 6 ? (
        <button
          className="btn btn-primary"
          type="button"
          disabled={!nextEnabled}
          onClick={onNext}
        >
          {nextLabel} <StudioIcon name="right" />
        </button>
      ) : (
        <button className="btn btn-primary" type="button" onClick={onFinish}>
          <StudioIcon name="check" /> 完成创作
        </button>
      )}
    </div>
  </div>
);

export default StepFooter;
