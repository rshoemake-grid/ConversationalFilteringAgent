import { useState, useCallback, useRef, useEffect } from 'react';
import { isGoogleChrome, VOICE_INPUT_CHROME_ONLY_TITLE } from '../utils/chromeVoiceSupport';

interface VoiceInputProps {
  onResult: (transcript: string) => void;
  disabled?: boolean;
}

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognition;
    webkitSpeechRecognition?: new () => SpeechRecognition;
  }
}

interface SpeechRecognition extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start(): void;
  stop(): void;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
}

interface SpeechRecognitionEvent extends Event {
  resultIndex: number;
  results: SpeechRecognitionResultList;
}

interface SpeechRecognitionErrorEvent extends Event {
  error: string;
}

const VOICE_INPUT_NO_API_TITLE = 'Voice input is not available in this browser.';

export function VoiceInput({ onResult, disabled }: VoiceInputProps) {
  const [listening, setListening] = useState(false);
  const recognitionRef = useRef<SpeechRecognition | null>(null);
  const onResultRef = useRef(onResult);
  const stopTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  onResultRef.current = onResult;

  const STOP_DELAY_MS = 400;

  const chromeOk = typeof navigator !== 'undefined' && isGoogleChrome();
  const SpeechRecognitionClass =
    chromeOk &&
    typeof window !== 'undefined' &&
    (window.SpeechRecognition || window.webkitSpeechRecognition);

  useEffect(() => {
    return () => {
      if (stopTimeoutRef.current) {
        clearTimeout(stopTimeoutRef.current);
        stopTimeoutRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!SpeechRecognitionClass) return;
    const rec = new SpeechRecognitionClass();
    rec.continuous = true;
    rec.interimResults = true;
    rec.lang = 'en-US';
    rec.onresult = (event: SpeechRecognitionEvent) => {
      let transcript = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) {
          transcript += event.results[i][0].transcript;
        }
      }
      if (transcript.trim()) {
        onResultRef.current(transcript.trim());
      }
    };
    rec.onerror = (event: SpeechRecognitionErrorEvent) => {
      if (event.error !== 'aborted') {
        setListening(false);
      }
    };
    recognitionRef.current = rec;
    return () => {
      try {
        rec.stop();
      } catch {
        // ignore
      }
    };
  }, [SpeechRecognitionClass]);

  const startListening = useCallback(() => {
    if (stopTimeoutRef.current) {
      clearTimeout(stopTimeoutRef.current);
      stopTimeoutRef.current = null;
    }
    const rec = recognitionRef.current;
    if (!rec || listening) return;
    rec.start();
    setListening(true);
  }, [listening]);

  const stopListening = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec || !listening) return;
    if (stopTimeoutRef.current) return;
    stopTimeoutRef.current = setTimeout(() => {
      stopTimeoutRef.current = null;
      rec.stop();
      setListening(false);
    }, STOP_DELAY_MS);
  }, [listening]);

  if (!chromeOk) {
    return (
      <button
        type="button"
        disabled
        className="voice-input"
        aria-label="Voice input requires Google Chrome"
        title={VOICE_INPUT_CHROME_ONLY_TITLE}
      >
        <MicIcon />
      </button>
    );
  }

  if (!SpeechRecognitionClass) {
    return (
      <button
        type="button"
        disabled
        className="voice-input"
        aria-label="Voice input not available"
        title={VOICE_INPUT_NO_API_TITLE}
      >
        <MicIcon />
      </button>
    );
  }

  return (
    <button
      type="button"
      onMouseDown={startListening}
      onMouseUp={stopListening}
      onMouseLeave={stopListening}
      onTouchStart={(e) => {
        e.preventDefault();
        startListening();
      }}
      onTouchEnd={(e) => {
        e.preventDefault();
        stopListening();
      }}
      disabled={disabled}
      className={`voice-input ${listening ? 'voice-input--active' : ''}`}
      aria-label={listening ? 'Release to stop voice input' : 'Hold to speak'}
      title="Hold to speak"
    >
      <MicIcon />
    </button>
  );
}

function MicIcon() {
  return (
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
      <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3Z" />
      <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
      <line x1="12" x2="12" y1="19" y2="23" />
      <line x1="8" x2="16" y1="23" y2="23" />
    </svg>
  );
}
