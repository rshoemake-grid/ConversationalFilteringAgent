import { describe, it, expect, vi, afterEach } from 'vitest';
import { isGoogleChrome } from './chromeVoiceSupport';

function stubNavigator(ua: string, vendor: string) {
  vi.stubGlobal('navigator', {
    userAgent: ua,
    vendor,
  });
}

describe('isGoogleChrome', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns true for Chrome desktop UA', () => {
    stubNavigator(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Google Inc.'
    );
    expect(isGoogleChrome()).toBe(true);
  });

  it('returns false for Edge (Chromium)', () => {
    stubNavigator(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0',
      'Google Inc.'
    );
    expect(isGoogleChrome()).toBe(false);
  });

  it('returns false for Firefox', () => {
    stubNavigator(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0',
      ''
    );
    expect(isGoogleChrome()).toBe(false);
  });

  it('returns false for Safari', () => {
    stubNavigator(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15',
      'Apple Computer, Inc.'
    );
    expect(isGoogleChrome()).toBe(false);
  });

  it('returns true for Chrome on iOS (CriOS)', () => {
    stubNavigator(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/120.0.6099.119 Mobile/15E148 Safari/604.1',
      'Apple Computer, Inc.'
    );
    expect(isGoogleChrome()).toBe(true);
  });

  it('returns false for Opera', () => {
    stubNavigator(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 OPR/106.0.0.0',
      'Google Inc.'
    );
    expect(isGoogleChrome()).toBe(false);
  });
});
