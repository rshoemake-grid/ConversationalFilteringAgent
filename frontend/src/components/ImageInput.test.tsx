import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ImageInput } from './ImageInput';

describe('ImageInput', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders attach image button', () => {
    render(<ImageInput onSelect={vi.fn()} />);
    expect(screen.getByRole('button', { name: /Attach image/i })).toBeInTheDocument();
  });

  it('calls onSelect when image file is selected', async () => {
    const onSelect = vi.fn();
    render(<ImageInput onSelect={onSelect} />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    expect(input).toBeInTheDocument();

    const file = new File(['image content'], 'test.png', { type: 'image/png' });
    await userEvent.upload(input, file);

    await vi.waitFor(() => {
      expect(onSelect).toHaveBeenCalledWith(expect.stringContaining('data:image/png;base64'));
    });
  });

  it('does not call onSelect when non-image file is selected', async () => {
    const onSelect = vi.fn();
    render(<ImageInput onSelect={onSelect} />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;

    const file = new File(['text content'], 'test.txt', { type: 'text/plain' });
    await userEvent.upload(input, file);

    expect(onSelect).not.toHaveBeenCalled();
  });

  it('disables button when disabled prop is true', () => {
    render(<ImageInput onSelect={vi.fn()} disabled />);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('has hidden file input with image accept', () => {
    render(<ImageInput onSelect={vi.fn()} />);
    const input = document.querySelector('input.image-input__hidden');
    expect(input).toHaveAttribute('accept', 'image/*');
  });
});
