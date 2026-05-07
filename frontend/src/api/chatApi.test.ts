import { describe, it, expect, vi, beforeEach } from 'vitest'
import { sendChatMessage } from './chatApi'

describe('chatApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('sends POST to /api/chat with request body', async () => {
    const mockFetch = vi.mocked(fetch)
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ text: 'Hello', conversationId: 'c1' }),
    } as Response)

    const result = await sendChatMessage({
      mode: 'convo_commerce',
      message: 'hi',
    })

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/chat',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode: 'convo_commerce', message: 'hi' }),
      })
    )
    expect(result.text).toBe('Hello')
    expect(result.conversationId).toBe('c1')
  })

  it('throws on non-ok response', async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 500,
      text: () => Promise.resolve('Server error'),
    } as Response)

    await expect(sendChatMessage({ mode: 'adk_orchestrator', message: 'x' }))
      .rejects.toThrow()
  })

  it('parses RFC 7807 JSON error and throws with detail', async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 400,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      text: () =>
        Promise.resolve(
          JSON.stringify({ title: 'Validation Error', detail: 'message: must not be blank' })
        ),
    } as Response)

    await expect(sendChatMessage({ mode: 'convo_commerce', message: '' })).rejects.toThrow(
      'message: must not be blank'
    )
  })

  it('parses RFC 7807 JSON error and uses title when detail is missing', async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 400,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      text: () => Promise.resolve(JSON.stringify({ title: 'Validation Error' })),
    } as Response)

    await expect(sendChatMessage({ mode: 'convo_commerce', message: 'hi' })).rejects.toThrow(
      'Validation Error'
    )
  })

  it('falls back to plain text when response is not JSON', async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 500,
      headers: new Headers({ 'Content-Type': 'text/plain' }),
      text: () => Promise.resolve('Internal Server Error'),
    } as Response)

    await expect(sendChatMessage({ mode: 'convo_commerce', message: 'hi' })).rejects.toThrow(
      'Internal Server Error'
    )
  })

  it('falls back to HTTP status when response body is empty', async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 502,
      headers: new Headers(),
      text: () => Promise.resolve(''),
    } as Response)

    await expect(sendChatMessage({ mode: 'convo_commerce', message: 'hi' })).rejects.toThrow(
      'HTTP 502'
    )
  })

  it('throws helpful message when fetch fails with network error', async () => {
    vi.mocked(fetch).mockRejectedValue(new Error('Failed to fetch'))

    await expect(sendChatMessage({ mode: 'convo_commerce', message: 'hi' })).rejects.toThrow(
      'Cannot reach the API'
    )
  })
})
