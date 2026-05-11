import { describe, expect, it } from 'vitest';
import { stripSuggestionEchoLinesFromAssistantText } from './stripSuggestionEchoes';
import type { SuggestedAnswer } from '../api/types';

describe('stripSuggestionEchoLinesFromAssistantText', () => {
  const storageAnswers: SuggestedAnswer[] = [
    { displayText: 'Ambient', value: 'S' },
    { displayText: 'Refrigerated', value: 'R' },
    { displayText: 'Dry storage', value: 'D' },
  ];

  it('keeps the question and removes bullet and duplicate plain labels', () => {
    const raw = `What type of stock do you prefer?
* Ambient
* Refrigerated
* Dry storage
Ambient
Refrigerated
Dry storage`;
    expect(stripSuggestionEchoLinesFromAssistantText(raw, storageAnswers)).toBe(
      'What type of stock do you prefer?'
    );
  });

  it('removes numbered option echoes', () => {
    const text = `Pick one:\n1. Ambient\n2. Frozen`;
    const answers: SuggestedAnswer[] = [
      { displayText: 'Ambient', value: 'A' },
      { displayText: 'Frozen', value: 'F' },
    ];
    expect(stripSuggestionEchoLinesFromAssistantText(text, answers)).toBe('Pick one:');
  });

  it('does not remove lines that only mention a label inside a sentence', () => {
    const text = 'Ambient storage works well for rice.';
    expect(stripSuggestionEchoLinesFromAssistantText(text, storageAnswers)).toBe(text);
  });

  it('is a no-op without suggested answers', () => {
    const text = '* Ambient\n* Cold';
    expect(stripSuggestionEchoLinesFromAssistantText(text, [])).toBe(text);
  });
});
