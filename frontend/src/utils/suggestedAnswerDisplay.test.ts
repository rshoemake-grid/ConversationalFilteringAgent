import { describe, expect, it } from 'vitest';
import {
  isSuggestedAnswerExcluded,
  suggestedAnswerDisplayLabel,
  suggestedAnswerSubmitValue,
} from './suggestedAnswerDisplay';

describe('suggestedAnswerDisplay', () => {
  it('maps storage codes when display equals value', () => {
    expect(suggestedAnswerDisplayLabel({ displayText: 'S', value: 'S' })).toBe('Ambient');
    expect(suggestedAnswerDisplayLabel({ displayText: 'R', value: 'R' })).toBe('Refrigerated');
    expect(suggestedAnswerDisplayLabel({ displayText: 'N', value: 'N' })).toBe('Non-refrigerated');
  });

  it('formats pack/UOM tokens with double underscore', () => {
    expect(suggestedAnswerDisplayLabel({ displayText: '3__LB', value: '3__LB' })).toBe('3 lb');
    expect(suggestedAnswerDisplayLabel({ displayText: '12__OZ', value: '12__OZ' })).toBe('12 oz');
    expect(suggestedAnswerDisplayLabel({ displayText: '4', value: '4' })).toBe('4');
  });

  it('keeps distinct displayText from API', () => {
    expect(suggestedAnswerDisplayLabel({ displayText: 'Ambient', value: 'S' })).toBe('Ambient');
  });

  it('submit uses canonical value', () => {
    expect(suggestedAnswerSubmitValue({ displayText: 'Ambient', value: 'S' })).toBe('S');
  });

  it('excludes by label or value in failed set', () => {
    const failed = new Set(['Ambient']);
    expect(isSuggestedAnswerExcluded({ displayText: 'S', value: 'S' }, failed)).toBe(true);
    expect(isSuggestedAnswerExcluded({ displayText: 'R', value: 'R' }, failed)).toBe(false);
  });
});
