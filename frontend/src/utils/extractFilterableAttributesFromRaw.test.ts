import { describe, it, expect } from 'vitest';
import { extractFilterableAttributeNamesFromRaw } from './extractFilterableAttributesFromRaw';

describe('extractFilterableAttributeNamesFromRaw', () => {
  it('returns sorted unique names from conversationalFilteringResult suggestedAnswers', () => {
    const raw = JSON.stringify({
      conversationalFilteringResult: {
        suggestedAnswers: [
          { productAttributeValue: { name: 'attributes.brands', value: 'A' } },
          { productAttributeValue: { name: 'attributes.type', value: 'B' } },
          { productAttributeValue: { name: 'attributes.brands', value: 'C' } },
        ],
      },
    });
    expect(extractFilterableAttributeNamesFromRaw(raw)).toEqual([
      'attributes.brands',
      'attributes.type',
    ]);
  });

  it('collects from followupQuestion object and conversationalSearchResult', () => {
    const raw =
      '{"conversationalFilteringResult":{"followupQuestion":{"suggestedAnswers":[' +
      '{"productAttributeValue":{"name":"attributes.size","value":"12oz"}}]}}}\n' +
      '{"conversationalSearchResult":{"suggestedAnswers":[' +
      '{"productAttributeValue":{"name":"attributes.stockType","value":"S"}}]}}';
    expect(extractFilterableAttributeNamesFromRaw(raw)).toEqual([
      'attributes.size',
      'attributes.stockType',
    ]);
  });

  it('returns empty when no names or invalid json', () => {
    expect(extractFilterableAttributeNamesFromRaw(null)).toEqual([]);
    expect(extractFilterableAttributeNamesFromRaw('not json')).toEqual([]);
    expect(
      extractFilterableAttributeNamesFromRaw(
        JSON.stringify({
          conversationalFilteringResult: {
            suggestedAnswers: [{ productAttributeValue: { value: 'S' } }],
          },
        })
      )
    ).toEqual([]);
  });
});
