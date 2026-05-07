import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { VoiceOutputToggle } from './VoiceOutputToggle';

describe('VoiceOutputToggle', () => {
  it('renders voice output button', () => {
    render(
      <VoiceOutputToggle enabled={false} onToggle={() => {}} />
    );
    expect(screen.getByRole('button', { name: /Voice output off/i })).toBeInTheDocument();
  });

  it('calls onToggle when clicked', async () => {
    const onToggle = vi.fn();
    render(
      <VoiceOutputToggle enabled={false} onToggle={onToggle} />
    );
    await userEvent.click(screen.getByRole('button', { name: /Voice output off/i }));
    expect(onToggle).toHaveBeenCalledWith(true);
  });

  it('shows pressed state when enabled', () => {
    render(
      <VoiceOutputToggle enabled={true} onToggle={() => {}} />
    );
    const btn = screen.getByRole('button', { name: /Voice output on/i });
    expect(btn).toHaveAttribute('aria-pressed', 'true');
  });

  it('shows stop button when speaking and onStop provided', () => {
    render(
      <VoiceOutputToggle
        enabled={true}
        onToggle={() => {}}
        isSpeaking={true}
        onStop={() => {}}
      />
    );
    expect(screen.getByRole('button', { name: /Stop speaking/i })).toBeInTheDocument();
  });

  it('calls onStop when stop button clicked', async () => {
    const onStop = vi.fn();
    render(
      <VoiceOutputToggle
        enabled={true}
        onToggle={() => {}}
        isSpeaking={true}
        onStop={onStop}
      />
    );
    await userEvent.click(screen.getByRole('button', { name: /Stop speaking/i }));
    expect(onStop).toHaveBeenCalled();
  });

  it('disables button when disabled prop is true', () => {
    render(
      <VoiceOutputToggle enabled={false} onToggle={() => {}} disabled />
    );
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('uses Chrome-only title and label when chromeOnlyTitle is set', () => {
    render(
      <VoiceOutputToggle
        enabled={false}
        onToggle={() => {}}
        disabled
        chromeOnlyTitle="Spoken replies require Google Chrome. Please use Chrome for this feature."
      />
    );
    const btn = screen.getByRole('button', { name: /Voice output requires Google Chrome/i });
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute(
      'title',
      'Spoken replies require Google Chrome. Please use Chrome for this feature.'
    );
    expect(btn).toHaveAttribute('aria-pressed', 'false');
  });
});
