import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ModeSelector } from './ModeSelector'

describe('ModeSelector', () => {
  it('renders mode options', () => {
    render(<ModeSelector value="convo_commerce" onChange={() => {}} />)
    expect(screen.getByRole('combobox')).toBeInTheDocument()
    expect(screen.getByText(/Approach A/)).toBeInTheDocument()
    expect(screen.getByText(/Approach B/)).toBeInTheDocument()
  })

  it('calls onChange when selection changes', async () => {
    const onChange = vi.fn()
    render(<ModeSelector value="convo_commerce" onChange={onChange} />)
    await userEvent.selectOptions(screen.getByRole('combobox'), 'adk_orchestrator')
    expect(onChange).toHaveBeenCalledWith('adk_orchestrator')
  })

  it('disables combobox when disabled prop is true', () => {
    render(<ModeSelector value="convo_commerce" onChange={() => {}} disabled />)
    expect(screen.getByRole('combobox')).toBeDisabled()
  })
})
