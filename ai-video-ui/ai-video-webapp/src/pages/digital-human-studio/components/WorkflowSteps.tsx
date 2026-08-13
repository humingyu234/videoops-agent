import React from 'react';
import { STEPS } from '../model';
import StudioIcon from './StudioIcon';

interface WorkflowStepsProps {
  current: number;
  onChange: (step: number) => void;
  onBlocked: () => void;
}

const WorkflowSteps: React.FC<WorkflowStepsProps> = ({
  current,
  onChange,
  onBlocked,
}) => (
  <div className="steps-bar" data-testid="workflow-steps">
    {STEPS.map((name, index) => (
      <React.Fragment key={name}>
        <button
          className={`step ${index < current ? 'done' : ''} ${
            index === current ? 'active' : ''
          }`}
          type="button"
          onClick={() => (index <= current ? onChange(index) : onBlocked())}
        >
          <span className="step-dot">
            {index < current ? <StudioIcon name="check" /> : index + 1}
          </span>
          <span className="step-name">{name}</span>
        </button>
        {index < STEPS.length - 1 && (
          <span className={`step-line ${index < current ? 'done' : ''}`} />
        )}
      </React.Fragment>
    ))}
  </div>
);

export default WorkflowSteps;
