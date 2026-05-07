const MIN_PAGE_SIZE = 1;
const MAX_PAGE_SIZE = 100;

interface PageSizeControlProps {
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
}

export function PageSizeControl({
  value,
  onChange,
  disabled = false,
}: PageSizeControlProps) {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = parseInt(e.target.value, 10);
    if (!Number.isNaN(v) && v >= MIN_PAGE_SIZE && v <= MAX_PAGE_SIZE) {
      onChange(v);
    }
  };

  return (
    <label className="page-size-control" title="Products per page (changeable in real time)">
      <span className="page-size-control__label">Page size</span>
      <input
        type="number"
        min={MIN_PAGE_SIZE}
        max={MAX_PAGE_SIZE}
        value={value}
        onChange={handleChange}
        disabled={disabled}
        className="page-size-control__input"
        aria-label="Products per page"
      />
    </label>
  );
}
