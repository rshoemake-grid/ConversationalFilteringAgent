import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MaxSuggestedAnswersControl } from './MaxSuggestedAnswersControl'

describe('MaxSuggestedAnswersControl', () => {
  it('renders label and input with value', () => {
    render(
      <MaxSuggestedAnswersControl value={8} onChange={() => {}} max={50} />
    )
    expect(screen.getByLabelText('Max suggested answers')).toBeInTheDocument()
    expect(screen.getByDisplayValue('8')).toBeInTheDocument()
  })

  it('calls onChange when value changes', async () => {
    const onChange = vi.fn()
    render(
      <MaxSuggestedAnswersControl value={8} onChange={onChange} max={50} />
    )
    const input = screen.getByLabelText('Max suggested answers')
    fireEvent.change(input, { target: { value: '12' } })
    expect(onChange).toHaveBeenCalledWith(12)
  })

  it('respects min and max attributes', () => {
    render(
      <MaxSuggestedAnswersControl value={10} onChange={() => {}} max={50} />
    )
    const input = screen.getByLabelText('Max suggested answers')
    expect(input).toHaveAttribute('min', '1')
    expect(input).toHaveAttribute('max', '50')
  })

  it('disables input when disabled prop is true', () => {
    render(
      <MaxSuggestedAnswersControl
        value={8}
        onChange={() => {}}
        max={50}
        disabled
      />
    )
    expect(screen.getByLabelText('Max suggested answers')).toBeDisabled()
  })
})
