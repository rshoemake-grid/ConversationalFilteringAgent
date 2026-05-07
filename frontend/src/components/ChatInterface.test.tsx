import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChatInterface } from './ChatInterface'
import * as chatApi from '../api/chatApi'

vi.mock('../api/chatApi', () => ({
  sendChatMessage: vi.fn(),
}))

const CHROME_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

describe('ChatInterface', () => {
  beforeEach(() => {
    vi.mocked(chatApi.sendChatMessage).mockReset()
    vi.stubGlobal('crypto', {
      randomUUID: () => 'test-uuid-123',
    })
    vi.stubGlobal('navigator', {
      ...globalThis.navigator,
      userAgent: CHROME_UA,
      vendor: 'Google Inc.',
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders header and mode selector', () => {
    render(<ChatInterface />)
    expect(screen.getByRole('heading', { name: /Conversational Filtering Agent/i })).toBeInTheDocument()
    expect(screen.getByRole('combobox')).toBeInTheDocument()
  })

  it('sends message on button click', async () => {
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'Hello!',
      conversationId: 'conv-1',
    })

    render(<ChatInterface />)
    const input = screen.getByLabelText('Chat message')
    await userEvent.type(input, 'hi')
    await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

    expect(chatApi.sendChatMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        mode: 'convo_commerce',
        message: 'hi',
      })
    )
    expect(screen.getByText('hi')).toBeInTheDocument()
    expect(screen.getByText('Hello!')).toBeInTheDocument()
  })

  it('sends message on Enter key', async () => {
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'Response',
      conversationId: 'c1',
    })

    render(<ChatInterface />)
    const input = screen.getByLabelText('Chat message')
    await userEvent.type(input, 'hello{Enter}')

    expect(chatApi.sendChatMessage).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'hello' })
    )
  })

  it('does not send when input is empty', async () => {
    render(<ChatInterface />)
    await userEvent.click(screen.getByRole('button', { name: /Send message/i }))
    expect(chatApi.sendChatMessage).not.toHaveBeenCalled()
  })

  it('shows loading state while sending', async () => {
    let resolvePromise!: (value: { text: string; conversationId: string }) => void
    vi.mocked(chatApi.sendChatMessage).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve
        })
    )

    render(<ChatInterface />)
    await userEvent.type(screen.getByLabelText('Chat message'), 'hi')
    await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Sending message' })).toBeInTheDocument()
    })
    expect(screen.getByLabelText('Chat message')).toBeDisabled()

    resolvePromise!({ text: 'Done', conversationId: 'c1' })
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Send message/i })).toBeInTheDocument()
    })
  })

  it('displays error message when API fails', async () => {
    vi.mocked(chatApi.sendChatMessage).mockRejectedValue(new Error('Network error'))

    render(<ChatInterface />)
    await userEvent.type(screen.getByLabelText('Chat message'), 'hi')
    await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

    expect(screen.getByText(/Network error/)).toBeInTheDocument()
  })

  it('disables mode selector and send while loading', async () => {
    let resolvePromise!: (value: { text: string; conversationId: string }) => void
    vi.mocked(chatApi.sendChatMessage).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve
        })
    )

    render(<ChatInterface />)
    await userEvent.type(screen.getByLabelText('Chat message'), 'hi')
    await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

    await waitFor(() => {
      expect(screen.getByRole('combobox')).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Sending message' })).toBeDisabled()
    })

    resolvePromise!({ text: 'Done', conversationId: 'c1' })
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Send message/i })).toBeInTheDocument()
    })
  })

  it('retries failed message when Retry is clicked', async () => {
    vi.mocked(chatApi.sendChatMessage)
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce({ text: 'Success!', conversationId: 'c1' })

    render(<ChatInterface />)
    await userEvent.type(screen.getByLabelText('Chat message'), 'hello')
    await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

    expect(screen.getByText(/Network error/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Retry sending message/i }))

    await waitFor(() => {
      expect(screen.getByText('Success!')).toBeInTheDocument()
    })
    expect(chatApi.sendChatMessage).toHaveBeenCalledTimes(2)
  })

  it('dismisses error when Dismiss is clicked', async () => {
    vi.mocked(chatApi.sendChatMessage).mockRejectedValue(new Error('Network error'))

    render(<ChatInterface />)
    await userEvent.type(screen.getByLabelText('Chat message'), 'hi')
    await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

    expect(screen.getByText(/Network error/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Dismiss error/i }))

    expect(screen.queryByText(/Network error/)).not.toBeInTheDocument()
    expect(screen.getByText('hi')).toBeInTheDocument()
  })

  it('toggles raw output panel visibility', async () => {
    render(<ChatInterface />)
    expect(screen.getByText('Raw API Response')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Hide raw output/i }))
    expect(screen.queryByText('Raw API Response')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Show raw output/i }))
    expect(screen.getByText('Raw API Response')).toBeInTheDocument()
  })

  it('sends with selected mode when changed', async () => {
    vi.mocked(chatApi.sendChatMessage).mockResolvedValue({
      text: 'OK',
      conversationId: 'c1',
    })

    render(<ChatInterface />)
    await userEvent.selectOptions(screen.getByRole('combobox'), 'adk_orchestrator')
    await userEvent.type(screen.getByLabelText('Chat message'), 'search')
    await userEvent.click(screen.getByRole('button', { name: /Send message/i }))

    expect(chatApi.sendChatMessage).toHaveBeenCalledWith(
      expect.objectContaining({ mode: 'adk_orchestrator' })
    )
  })
})
