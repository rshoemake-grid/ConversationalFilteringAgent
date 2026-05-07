/**
 * Voice input (Web Speech API) and speech synthesis are only enabled for Google Chrome
 * in this app, for predictable behavior across browsers.
 */

export const VOICE_INPUT_CHROME_ONLY_TITLE =
  'Voice input requires Google Chrome. Please use Chrome for this feature.';

export const VOICE_OUTPUT_CHROME_ONLY_TITLE =
  'Spoken replies require Google Chrome. Please use Chrome for this feature.';

/**
 * True for Google Chrome desktop (Chromium + Google vendor, excluding Edge/Opera)
 * and Chrome on iOS (CriOS).
 */
export function isGoogleChrome(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent;
  if (ua.includes('Edg/') || ua.includes('OPR/')) return false;
  if (ua.includes('CriOS/')) return true;
  if (ua.includes('Chrome/') && navigator.vendor === 'Google Inc.') return true;
  return false;
}
