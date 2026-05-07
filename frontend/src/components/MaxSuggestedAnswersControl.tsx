interface MaxSuggestedAnswersControlProps {
  value: number;
  onChange: (value: number) => void;
  max: number;
  disabled?: boolean;
}

export function MaxSuggestedAnswersControl({
  value,
  onChange,
  max,
  disabled = false,
}: MaxSuggestedAnswersControlProps) {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = parseInt(e.target.value, 10);
    if (!Number.isNaN(v) && v >= 1 && v <= max) {
      onChange(v);
    }
  };

  return (
    <label className="max-suggested-control" title="Max suggested answers shown (changeable in real time)">
      <span className="max-suggested-control__label">Max suggestions</span>
      <input
        type="number"
        min={1}
        max={max}
        value={value}
        onChange={handleChange}
        disabled={disabled}
        className="max-suggested-control__input"
        aria-label="Max suggested answers"
      />
    </label>
  );
}
