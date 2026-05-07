import { useState, useCallback, useRef, useEffect } from 'react';

/** Max chars per utterance; Chrome truncates ~200–300 for some voices. */
const CHUNK_SIZE = 200;

/**
 * Splits text into chunks at sentence boundaries when possible.
 * Falls back to space boundaries if a sentence exceeds CHUNK_SIZE.
 */
function chunkForTts(text: string): string[] {
  const trimmed = text.trim();
  if (!trimmed) return [];

  const chunks: string[] = [];
  let remaining = trimmed;

  while (remaining.length > 0) {
    if (remaining.length <= CHUNK_SIZE) {
      chunks.push(remaining);
      break;
    }
    const candidate = remaining.slice(0, CHUNK_SIZE);
    const lastPunct = candidate.search(/[.!?]\s*$/);
    const lastSpace = candidate.lastIndexOf(' ');
    const splitAt =
      lastPunct >= 0 ? lastPunct + 1 : lastSpace >= 0 ? lastSpace + 1 : CHUNK_SIZE;
    chunks.push(remaining.slice(0, splitAt).trim());
    remaining = remaining.slice(splitAt).trim();
  }

  return chunks.filter((c) => c.length > 0);
}

/**
 * Hook for text-to-speech output using the Web Speech API (SpeechSynthesis).
 * Provides speak/stop and isSpeaking state for voice streaming mode.
 * Chunks long text to avoid Chrome truncation (~200–300 chars).
 */
export function useVoiceOutput() {
  const [isSpeaking, setIsSpeaking] = useState(false);
  const queueRef = useRef<string[]>([]);
  const cancelledRef = useRef(false);

  const stop = useCallback(() => {
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      window.speechSynthesis.cancel();
      cancelledRef.current = true;
      queueRef.current = [];
      setIsSpeaking(false);
    }
  }, []);

  const speakNext = useCallback(() => {
    if (queueRef.current.length === 0 || cancelledRef.current) {
      setIsSpeaking(false);
      return;
    }
    const chunk = queueRef.current.shift()!;
    const utterance = new SpeechSynthesisUtterance(chunk);
    utterance.lang = 'en-US';
    utterance.rate = 0.95;
    utterance.onstart = () => setIsSpeaking(true);
    utterance.onend = () => speakNext();
    utterance.onerror = () => speakNext();
    window.speechSynthesis!.speak(utterance);
  }, []);

  const speak = useCallback(
    (text: string) => {
      const t = (text || '').trim();
      if (!t) return;

      if (typeof window === 'undefined' || !window.speechSynthesis) return;

      stop();
      cancelledRef.current = false;
      queueRef.current = chunkForTts(t);
      if (queueRef.current.length > 0) {
        speakNext();
      }
    },
    [stop, speakNext]
  );

  useEffect(() => {
    return () => {
      if (typeof window !== 'undefined' && window.speechSynthesis) {
        window.speechSynthesis.cancel();
      }
    };
  }, []);

  return { speak, stop, isSpeaking };
}
