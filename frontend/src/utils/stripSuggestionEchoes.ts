import type { SuggestedAnswer } from '../api/types';
import { suggestedAnswerDisplayLabel } from './suggestedAnswerDisplay';

/**
 * Removes lines that duplicate suggested-answer labels (bullets, numbers, or plain repeats)
 * so the message body does not mirror what the UI already shows as chips.
 */
export function stripSuggestionEchoLinesFromAssistantText(
  text: string,
  answers: SuggestedAnswer[]
): string {
  if (!text.trim() || !answers?.length) return text;

  const labelSet = new Set<string>();
  for (const sa of answers) {
    const display = suggestedAnswerDisplayLabel(sa).trim().toLowerCase();
    const raw = (sa.value ?? '').trim().toLowerCase();
    if (display) labelSet.add(display);
    if (raw && raw !== display) labelSet.add(raw);
  }

  const isEchoLine = (line: string): boolean => {
    const t = line.trim();
    if (!t) return false;

    const bullet = /^[-*•]\s+(.+)$/.exec(t);
    if (bullet && labelSet.has(bullet[1].trim().toLowerCase())) return true;

    const numbered = /^\d+[.)]\s+(.+)$/.exec(t);
    if (numbered && labelSet.has(numbered[1].trim().toLowerCase())) return true;

    return labelSet.has(t.toLowerCase());
  };

  const lines = text.split(/\r?\n/);
  const kept = lines.filter((line) => !isEchoLine(line));
  return kept.join('\n').replace(/\n{3,}/g, '\n\n').trimEnd();
}
