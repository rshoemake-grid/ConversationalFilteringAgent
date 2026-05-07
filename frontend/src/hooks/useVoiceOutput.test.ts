import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useVoiceOutput } from './useVoiceOutput';

const mockSpeak = vi.fn();
const mockCancel = vi.fn();

class MockSpeechSynthesisUtterance {
  text = '';
  constructor(text: string) {
    this.text = text;
  }
}

describe('useVoiceOutput', () => {
  beforeEach(() => {
    mockSpeak.mockClear();
    mockCancel.mockClear();
    Object.defineProperty(globalThis, 'SpeechSynthesisUtterance', {
      value: MockSpeechSynthesisUtterance,
      writable: true,
    });
    const globalObj = typeof window !== 'undefined' ? window : globalThis;
    Object.defineProperty(globalObj as object, 'speechSynthesis', {
      value: { speak: mockSpeak, cancel: mockCancel },
      writable: true,
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns speak, stop, and isSpeaking', () => {
    const { result } = renderHook(() => useVoiceOutput());
    expect(result.current.speak).toBeDefined();
    expect(typeof result.current.speak).toBe('function');
    expect(result.current.stop).toBeDefined();
    expect(result.current.isSpeaking).toBe(false);
  });

  it('calls speechSynthesis.speak when speak is invoked with text', () => {
    const { result } = renderHook(() => useVoiceOutput());
    act(() => {
      result.current.speak('Hello world');
    });
    expect(mockSpeak).toHaveBeenCalled();
    const utterance = mockSpeak.mock.calls[0][0];
    expect(utterance.text).toBe('Hello world');
  });

  it('does not speak when text is empty', () => {
    const { result } = renderHook(() => useVoiceOutput());
    act(() => {
      result.current.speak('');
    });
    act(() => {
      result.current.speak('   ');
    });
    expect(mockSpeak).not.toHaveBeenCalled();
  });

  it('calls speechSynthesis.cancel when stop is invoked', () => {
    const { result } = renderHook(() => useVoiceOutput());
    act(() => {
      result.current.stop();
    });
    expect(mockCancel).toHaveBeenCalled();
  });

  it('chunks long text for TTS (avoids Chrome truncation)', () => {
    const { result } = renderHook(() => useVoiceOutput());
    const longText = 'Hello. '.repeat(50); // 350 chars
    act(() => {
      result.current.speak(longText);
    });
    expect(mockSpeak).toHaveBeenCalled();
    const firstUtterance = mockSpeak.mock.calls[0][0];
    expect(firstUtterance.text.length).toBeLessThanOrEqual(250);
  });
});
