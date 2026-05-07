import { useRef, useCallback, useEffect } from 'react';

interface ImageInputProps {
  onSelect: (dataUrl: string) => void;
  disabled?: boolean;
}

export function ImageInput({ onSelect, disabled }: ImageInputProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (disabled) return;
    const handlePaste = (e: ClipboardEvent) => {
      const item = e.clipboardData?.items?.[0];
      if (!item || item.kind !== 'file') return;
      const file = item.getAsFile();
      if (!file?.type.startsWith('image/')) return;
      e.preventDefault();
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result;
        if (typeof result === 'string') onSelect(result);
      };
      reader.readAsDataURL(file);
    };
    document.addEventListener('paste', handlePaste);
    return () => document.removeEventListener('paste', handlePaste);
  }, [disabled, onSelect]);

  const handleFile = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (!file || !file.type.startsWith('image/')) return;
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result;
        if (typeof result === 'string') onSelect(result);
      };
      reader.readAsDataURL(file);
      e.target.value = '';
    },
    [onSelect]
  );

  const handleClick = useCallback(() => {
    inputRef.current?.click();
  }, []);

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        onChange={handleFile}
        className="image-input__hidden"
        aria-hidden="true"
      />
      <button
        type="button"
        onClick={handleClick}
        disabled={disabled}
        className="image-input"
        aria-label="Attach image"
        title="Attach image"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <rect width="18" height="18" x="3" y="3" rx="2" ry="2" />
          <circle cx="9" cy="9" r="2" />
          <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21" />
        </svg>
      </button>
    </>
  );
}
