interface VoiceOutputToggleProps {
  enabled: boolean;
  onToggle: (enabled: boolean) => void;
  isSpeaking?: boolean;
  onStop?: () => void;
  disabled?: boolean;
  /** When the browser is not Google Chrome; used as hover hint and should pair with disabled */
  chromeOnlyTitle?: string;
}

export function VoiceOutputToggle({
  enabled,
  onToggle,
  isSpeaking = false,
  onStop,
  disabled,
  chromeOnlyTitle,
}: VoiceOutputToggleProps) {
  const defaultTitle = enabled
    ? 'Voice output on — assistant responses will be spoken'
    : 'Voice output off — click to hear responses';
  const mainTitle = chromeOnlyTitle ?? defaultTitle;
  const mainAriaLabel = chromeOnlyTitle
    ? 'Voice output requires Google Chrome'
    : enabled
      ? 'Voice output on (click to turn off)'
      : 'Voice output off (click to turn on)';

  return (
    <div className="voice-output-toggle" role="group" aria-label="Voice output">
      <button
        type="button"
        onClick={() => onToggle(!enabled)}
        disabled={disabled}
        className={`voice-output-toggle__btn ${enabled && !chromeOnlyTitle ? 'voice-output-toggle__btn--on' : ''}`}
        aria-label={mainAriaLabel}
        aria-pressed={chromeOnlyTitle ? false : enabled}
        title={mainTitle}
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
          <path d="M11 5L6 9H2v6h4l5 4V5z" />
          <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
          <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
        </svg>
      </button>
      {isSpeaking && onStop && (
        <button
          type="button"
          onClick={onStop}
          className="voice-output-toggle__stop"
          aria-label="Stop speaking"
          title="Stop speaking"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <rect x="6" y="6" width="12" height="12" rx="2" />
          </svg>
        </button>
      )}
    </div>
  );
}
