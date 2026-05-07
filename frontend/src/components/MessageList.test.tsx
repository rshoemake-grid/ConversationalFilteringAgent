import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MessageList } from './MessageList'

describe('MessageList', () => {
  it('shows empty state when no messages', () => {
    render(<MessageList messages={[]} />)
    expect(screen.getByLabelText('No messages yet')).toBeInTheDocument()
    expect(screen.getByText(/Send a message to get started/)).toBeInTheDocument()
  })

  it('renders user message', () => {
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: 'Hello there' },
        ]}
      />
    )
    expect(screen.getByText('You')).toBeInTheDocument()
    expect(screen.getByText('Hello there')).toBeInTheDocument()
  })

  it('renders user message with image when imageUri is present', () => {
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: '[Image]', imageUri: 'data:image/png;base64,abc123' },
        ]}
      />
    )
    const img = screen.getByRole('img', { name: 'Attached' })
    expect(img).toHaveAttribute('src', 'data:image/png;base64,abc123')
  })

  it('renders user message with image', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'user',
            content: '[Image]',
            imageUri: 'data:image/png;base64,abc123',
          },
        ]}
      />
    )
    const img = screen.getByRole('img', { name: 'Attached' })
    expect(img).toHaveAttribute('src', 'data:image/png;base64,abc123')
  })

  it('renders user message with attached image', () => {
    const dataUrl = 'data:image/png;base64,iVBORw0KGgo='
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: '[Image]', imageUri: dataUrl },
        ]}
      />
    )
    const img = screen.getByRole('img', { name: 'Attached' })
    expect(img).toHaveAttribute('src', dataUrl)
  })

  it('renders user message with attached image', () => {
    const dataUrl = 'data:image/png;base64,iVBORw0KGgo='
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: 'Find similar', imageUri: dataUrl },
        ]}
      />
    )
    expect(screen.getByText('You')).toBeInTheDocument()
    const img = screen.getByRole('img', { name: 'Attached' })
    expect(img).toHaveAttribute('src', dataUrl)
  })

  it('renders assistant message', () => {
    render(
      <MessageList
        messages={[
          { id: '2', role: 'assistant', content: 'Hi! How can I help?' },
        ]}
      />
    )
    expect(screen.getByText('Assistant')).toBeInTheDocument()
    expect(screen.getByText('Hi! How can I help?')).toBeInTheDocument()
  })

  it('labels assistant messages as Assistant even when source is app', () => {
    render(
      <MessageList
        messages={[
          { id: '1', role: 'assistant', content: "I'm here to help with your shopping.", source: 'app' },
        ]}
      />
    )
    expect(screen.getByText('Assistant')).toBeInTheDocument()
    expect(screen.queryByText('Application')).not.toBeInTheDocument()
  })

  it('orders Products, then Application + count, then Assistant above the grid', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'Showing 2 of 100 products',
            source: 'app',
            queryType: 'SIMPLE_PRODUCT_SEARCH',
            products: [
              { id: 'p1', title: 'Item 1', description: 'Desc', price: '$10', imageUri: 'http://img' },
              { id: 'p2', title: 'Item 2', description: 'Desc', price: '$12', imageUri: 'http://img' },
            ],
            productTotalSize: 100,
          },
        ]}
      />
    )
    const messageEl = screen.getByText('Item 1').closest('.message')
    expect(messageEl?.querySelector(':scope > .message__role')).toBeNull()

    const productsSection = screen.getByText('Products').closest('.message__products')
    expect(productsSection).not.toBeNull()
    const assistantRow = productsSection!.querySelector('.message__role--product-grid')
    expect(assistantRow).not.toBeNull()
    expect(assistantRow).toHaveTextContent('Assistant')
    expect(assistantRow).toHaveTextContent('SIMPLE_PRODUCT_SEARCH')

    const appLabel = productsSection!.querySelector('.message__product-count-source')
    expect(appLabel).not.toBeNull()
    expect(appLabel).toHaveTextContent('Application')
    expect(screen.getByText(/Showing 2 of 100 products/)).toBeInTheDocument()

    const children = Array.from(productsSection!.children)
    const h4Idx = children.findIndex((el) => el.tagName === 'H4')
    const countIdx = children.findIndex((el) => el.classList.contains('message__product-count-section'))
    const assistantIdx = children.findIndex((el) => el.classList.contains('message__role--product-grid'))
    const gridIdx = children.findIndex((el) => el.classList.contains('product-grid'))
    expect(h4Idx).toBe(0)
    expect(countIdx).toBeGreaterThan(h4Idx)
    expect(assistantIdx).toBeGreaterThan(countIdx)
    expect(gridIdx).toBeGreaterThan(assistantIdx)
  })

  it('labels agent responses as Assistant (agent)', () => {
    render(
      <MessageList
        messages={[
          { id: '1', role: 'assistant', content: 'Here are some options.', source: 'agent' },
        ]}
      />
    )
    expect(screen.getByText('Assistant')).toBeInTheDocument()
    expect(screen.getByText('(agent)')).toBeInTheDocument()
  })

  it('renders multiple messages in order', () => {
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: 'Show me shoes' },
          { id: '2', role: 'assistant', content: 'Here are some options.' },
        ]}
      />
    )
    expect(screen.getByText('Show me shoes')).toBeInTheDocument()
    expect(screen.getByText('Here are some options.')).toBeInTheDocument()
  })

  it('shows product count even when productTotalSize is unknown', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'Here are some options',
            products: [
              { id: 'p1', title: 'Item 1', description: 'Desc', price: '$10', imageUri: 'http://img' },
              { id: 'p2', title: 'Item 2', description: 'Desc', price: '$12', imageUri: 'http://img' },
            ],
          },
        ]}
      />
    )
    expect(screen.getByText(/Showing 2 products/)).toBeInTheDocument()
  })

  it('shows product count with total when productTotalSize is provided', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'Here are some options',
            products: [
              { id: 'p1', title: 'Item 1', description: 'Desc', price: '$10', imageUri: 'http://img' },
            ],
            productTotalSize: 25,
          },
        ]}
      />
    )
    expect(screen.getByText(/Showing 1 of 25 products/)).toBeInTheDocument()
  })

  it('shows clarifying question and suggested answers after products when present', () => {
    const onSuggestedAnswer = vi.fn()
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'I found 1 product matching your request.',
            products: [
              { id: 'p1', title: 'Nike Run', description: 'Running shoes', price: '$99', imageUri: 'http://img' },
            ],
            clarifyingQuestion: 'Would you like 12oz or 24oz?',
            suggestedAnswers: [
              { displayText: '12oz', value: '12oz' },
              { displayText: '24oz', value: '24oz' },
            ],
          },
        ]}
        onSuggestedAnswer={onSuggestedAnswer}
      />
    )
    expect(screen.getByText('Products')).toBeInTheDocument()
    expect(screen.getByText('Nike Run')).toBeInTheDocument()
    expect(screen.getByText('Would you like 12oz or 24oz?')).toBeInTheDocument()
    expect(screen.getByText('12oz')).toBeInTheDocument()
    expect(screen.getByText('24oz')).toBeInTheDocument()
    // Clarifying question and suggested answers should appear after products in DOM order
    const productsSection = screen.getByText('Products').closest('.message__products')
    expect(productsSection).toContainElement(screen.getByText('Would you like 12oz or 24oz?'))
    expect(productsSection).toContainElement(screen.getByText('12oz'))
    expect(productsSection).toContainElement(screen.getByText('24oz'))
  })

  it('renders product cards when products are present', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'Here are some shoes',
            products: [
              {
                id: 'p1',
                title: 'Nike Run',
                description: 'Running shoes',
                price: '$99',
                imageUri: 'http://example.com/img.png',
              },
            ],
          },
        ]}
      />
    )
    expect(screen.getByText('Products')).toBeInTheDocument()
    expect(screen.getAllByText('Nike Run').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Running shoes').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('$99').length).toBeGreaterThanOrEqual(1)
    const img = screen.getByRole('img', { name: 'Nike Run' })
    expect(img).toHaveAttribute('src', 'http://example.com/img.png')
  })

  it('renders suggested answers when present and calls onSuggestedAnswer with the answer when clicked', async () => {
    const onSuggestedAnswer = vi.fn()
    const running = { displayText: 'Running shoes', value: 'Running shoes' }
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'What type of shoes?',
            suggestedAnswers: [
              running,
              { displayText: 'Casual shoes', value: 'Casual shoes' },
              { displayText: 'Boots', value: 'Boots' },
            ],
          },
        ]}
        onSuggestedAnswer={onSuggestedAnswer}
      />
    )
    expect(screen.getByText('Running shoes')).toBeInTheDocument()
    expect(screen.getByText('Casual shoes')).toBeInTheDocument()
    expect(screen.getByText('Boots')).toBeInTheDocument()
    await userEvent.click(screen.getByText('Running shoes'))
    expect(onSuggestedAnswer).toHaveBeenCalledWith(running)
  })

  it('renders storage codes as readable labels and places suggestions after the product grid when both exist', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'Showing 2 of 2 products',
            products: [{ id: 'p1', title: 'Milk', description: '', price: '$1' }],
            suggestedAnswers: [
              { displayText: 'S', value: 'S' },
              { displayText: 'R', value: 'R' },
            ],
          },
        ]}
        onSuggestedAnswer={() => {}}
      />
    )
    const productsHeading = screen.getByText('Products')
    const ambient = screen.getByRole('button', { name: 'Ambient' })
    expect(productsHeading.compareDocumentPosition(ambient) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('slices suggested answers when maxSuggestedAnswers is set', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'Options',
            suggestedAnswers: [
              { displayText: 'A', value: 'A' },
              { displayText: 'B', value: 'B' },
              { displayText: 'C', value: 'C' },
              { displayText: 'D', value: 'D' },
            ],
          },
        ]}
        maxSuggestedAnswers={2}
        onSuggestedAnswer={() => {}}
      />
    )
    expect(screen.getByText('A')).toBeInTheDocument()
    expect(screen.getByText('B')).toBeInTheDocument()
    expect(screen.queryByText('C')).not.toBeInTheDocument()
    expect(screen.queryByText('D')).not.toBeInTheDocument()
  })

  it('shows Get more suggestions button on last assistant message with suggestions', async () => {
    const onGetMoreSuggestions = vi.fn()
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: 'shoes' },
          {
            id: '2',
            role: 'assistant',
            content: 'What type?',
            suggestedAnswers: [
              { displayText: 'Running', value: 'Running' },
            ],
          },
        ]}
        onSuggestedAnswer={() => {}}
        onGetMoreSuggestions={onGetMoreSuggestions}
      />
    )
    const btn = screen.getByRole('button', { name: /Get more suggested answers/i })
    expect(btn).toBeInTheDocument()
    await userEvent.click(btn)
    expect(onGetMoreSuggestions).toHaveBeenCalledTimes(1)
  })

  it('does not render suggested answers when onSuggestedAnswer is not provided', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'What type?',
            suggestedAnswers: [{ displayText: 'Option A', value: 'Option A' }],
          },
        ]}
      />
    )
    expect(screen.getByText('What type?')).toBeInTheDocument()
    expect(screen.queryByText('Option A')).not.toBeInTheDocument()
  })

  it('uses key fallback when product id is missing', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'Products',
            products: [
              { id: '', title: 'No ID Product', description: '', price: '' },
            ],
          },
        ]}
      />
    )
    expect(screen.getAllByText('No ID Product').length).toBeGreaterThanOrEqual(1)
  })

  it('does not render products section when products array is empty', () => {
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'assistant',
            content: 'No results',
            products: [],
          },
        ]}
      />
    )
    expect(screen.queryByText('Products')).not.toBeInTheDocument()
  })

  it('sets aria-busy when loading', () => {
    const { container } = render(
      <MessageList messages={[]} loading={true} />
    )
    const list = container.querySelector('[role="log"]')
    expect(list).toHaveAttribute('aria-busy', 'true')
  })

  it('shows loading spinner when loading', () => {
    render(<MessageList messages={[]} loading={true} />)
    expect(screen.getByText('Searching...')).toBeInTheDocument()
    expect(document.querySelector('.message__spinner')).toBeInTheDocument()
  })

  it('shows loading spinner after the user message in document order', () => {
    render(
      <MessageList
        messages={[{ id: '1', role: 'user', content: 'Hello' }]}
        loading={true}
      />
    )
    const userBubble = screen.getByText('Hello')
    const status = screen.getByRole('status')
    expect(userBubble.compareDocumentPosition(status) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('renders error message with Retry and Dismiss buttons', () => {
    const onRetry = vi.fn()
    const onDismissError = vi.fn()
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: 'hello' },
          { id: '2', role: 'assistant', content: 'Network error', isError: true },
        ]}
        onRetry={onRetry}
        onDismissError={onDismissError}
      />
    )
    expect(screen.getByText('Network error')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Retry sending message/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Dismiss error/i })).toBeInTheDocument()
  })

  it('calls onRetry with message text and error id when Retry clicked', async () => {
    const onRetry = vi.fn()
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: 'retry me' },
          { id: 'e1', role: 'assistant', content: 'Failed', isError: true },
        ]}
        onRetry={onRetry}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /Retry sending message/i }))
    expect(onRetry).toHaveBeenCalledWith('retry me', 'e1', undefined)
  })

  it('calls onRetry with imageBase64 when retrying image message', async () => {
    const onRetry = vi.fn()
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: '[Image]', imageUri: 'data:image/jpeg;base64,xyz', imageBase64: 'xyz' },
          { id: 'e1', role: 'assistant', content: 'Failed', isError: true },
        ]}
        onRetry={onRetry}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /Retry sending message/i }))
    expect(onRetry).toHaveBeenCalledWith('[Image]', 'e1', 'xyz')
  })

  it('calls onRetry with imageBase64 when retrying image message', async () => {
    const onRetry = vi.fn()
    render(
      <MessageList
        messages={[
          {
            id: '1',
            role: 'user',
            content: '[Image]',
            imageUri: 'data:image/png;base64,abc',
            imageBase64: 'abc',
          },
          { id: 'e1', role: 'assistant', content: 'Failed', isError: true },
        ]}
        onRetry={onRetry}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /Retry sending message/i }))
    expect(onRetry).toHaveBeenCalledWith('[Image]', 'e1', 'abc')
  })

  it('calls onRetry with imageBase64 when retrying image message', async () => {
    const onRetry = vi.fn()
    const base64 = 'abc123'
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: '[Image]', imageBase64: base64 },
          { id: 'e1', role: 'assistant', content: 'Failed', isError: true },
        ]}
        onRetry={onRetry}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /Retry sending message/i }))
    expect(onRetry).toHaveBeenCalledWith('[Image]', 'e1', base64)
  })

  it('calls onDismissError when Dismiss clicked', async () => {
    const onDismissError = vi.fn()
    render(
      <MessageList
        messages={[
          { id: '1', role: 'user', content: 'hi' },
          { id: 'e1', role: 'assistant', content: 'Error', isError: true },
        ]}
        onDismissError={onDismissError}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /Dismiss error/i }))
    expect(onDismissError).toHaveBeenCalledWith('e1')
  })

  it('sets aria-busy false when not loading', () => {
    const { container } = render(
      <MessageList messages={[]} loading={false} />
    )
    const list = container.querySelector('[role="log"]')
    expect(list).toHaveAttribute('aria-busy', 'false')
  })
})
