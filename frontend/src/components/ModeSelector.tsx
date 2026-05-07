import type { OrchestrationMode } from '../api/types';

interface ModeSelectorProps {
  value: OrchestrationMode;
  onChange: (mode: OrchestrationMode) => void;
  disabled?: boolean;
}

const MODE_SELECT_ID = 'orchestration-mode';

export function ModeSelector({ value, onChange, disabled }: ModeSelectorProps) {
  return (
    <div className="mode-selector">
      <label htmlFor={MODE_SELECT_ID}>Orchestration mode:</label>
      <select
        id={MODE_SELECT_ID}
        value={value}
        onChange={(e) => onChange(e.target.value as OrchestrationMode)}
        disabled={disabled}
      >
        <option value="convo_commerce">
          Approach A: Convo Commerce as Orchestrator
        </option>
        <option value="adk_orchestrator">
          Approach B: ADK Agent as Orchestrator
        </option>
      </select>
    </div>
  );
}
