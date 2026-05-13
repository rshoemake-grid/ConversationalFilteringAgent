import { describe, it, expect } from 'vitest';
import type { Message } from '../api/types';
import { shouldHideSuggestedAnswersForMessage, shouldHideSuggestedAnswersForPage } from './suggestionVisibility';

function assistantMsg(partial: Partial<Message> & Pick<Message, 'products'>): Message {
  return {
    id: 'a1',
    role: 'assistant',
    content: 'Hello',
    ...partial,
  };
}

describe('shouldHideSuggestedAnswersForPage', () => {
  it('returns false when no products', () => {
    expect(
      shouldHideSuggestedAnswersForPage(assistantMsg({ products: [] }), 20)
    ).toBe(false);
  });

  it('returns true when total is known and within page size', () => {
    expect(
      shouldHideSuggestedAnswersForPage(
        assistantMsg({ products: [{ id: 'p1', title: 'A', description: '', price: '' }], productTotalSize: 3 }),
        20
      )
    ).toBe(true);
  });

  it('returns false when total is known but exceeds page size', () => {
    expect(
      shouldHideSuggestedAnswersForPage(
        assistantMsg({ products: [{ id: 'p1', title: 'A', description: '', price: '' }], productTotalSize: 50 }),
        20
      )
    ).toBe(false);
  });

  it('returns false when total is unknown even if one page shown', () => {
    expect(
      shouldHideSuggestedAnswersForPage(
        assistantMsg({ products: [{ id: 'p1', title: 'A', description: '', price: '' }] }),
        20
      )
    ).toBe(false);
  });

  it('returns false when page size is invalid', () => {
    expect(
      shouldHideSuggestedAnswersForPage(
        assistantMsg({ products: [{ id: 'p1', title: 'A', description: '', price: '' }], productTotalSize: 2 }),
        0
      )
    ).toBe(false);
  });
});

describe('shouldHideSuggestedAnswersForMessage', () => {
  it('returns false when a clarifying question is present even if catalog fits one page', () => {
    expect(
      shouldHideSuggestedAnswersForMessage(
        assistantMsg({
          products: [{ id: 'p1', title: 'A', description: '', price: '' }],
          productTotalSize: 3,
          clarifyingQuestion: 'Which size?',
        }),
        20
      )
    ).toBe(false);
  });

  it('matches shouldHideSuggestedAnswersForPage when there is no clarifying question', () => {
    const msg = assistantMsg({
      products: [{ id: 'p1', title: 'A', description: '', price: '' }],
      productTotalSize: 3,
    });
    expect(shouldHideSuggestedAnswersForMessage(msg, 20)).toBe(
      shouldHideSuggestedAnswersForPage(msg, 20)
    );
  });
});
