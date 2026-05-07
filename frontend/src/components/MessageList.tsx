import { memo, useEffect, useRef } from 'react';
import type { Message, SuggestedAnswer } from '../api/types';
import { ProductCard } from './ProductCard';
import { suggestedAnswerDisplayLabel } from '../utils/suggestedAnswerDisplay';

interface MessageListProps {
  messages: Message[];
  loading?: boolean;
  maxSuggestedAnswers?: number;
  onRetry?: (messageText: string, errorId: string, imageBase64?: string) => void;
  onDismissError?: (messageId: string) => void;
  onSuggestedAnswer?: (answer: SuggestedAnswer) => void;
  onGetMoreSuggestions?: () => void;
  onLoadMore?: (msg: Message) => void;
}

function MessageListComponent({
  messages,
  loading = false,
  maxSuggestedAnswers,
  onRetry,
  onDismissError,
  onSuggestedAnswer,
  onGetMoreSuggestions,
  onLoadMore,
}: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = bottomRef.current;
    if (el && typeof el.scrollIntoView === 'function') {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, loading]);

  return (
    <div className="message-list" role="log" aria-live="polite" aria-busy={loading}>
      {messages.length === 0 && (
        <div className="message-list__empty" aria-label="No messages yet">
          <p>Send a message to get started. Try asking about products or store hours.</p>
        </div>
      )}
      {messages.map((msg, idx) => {
        const hasProducts = Boolean(msg.products && msg.products.length > 0);
        const showTopRoleRow =
          msg.role === 'user' ||
          msg.isError ||
          (msg.role === 'assistant' && !hasProducts);

        return (
        <div
          key={msg.id}
          className={`message message--${msg.role}${msg.isError ? ' message--error' : ''}`}
          role={msg.isError ? 'alert' : undefined}
        >
          {showTopRoleRow && (
          <div className="message__role">
            {msg.role === 'user'
              ? 'You'
              : msg.isError
                ? 'Error'
                : 'Assistant'}
            {msg.role === 'assistant' && !msg.isError && msg.source === 'agent' && (
              <span className="message__source" title="Conversational Commerce agent">
                {' '}(agent)
              </span>
            )}
            {msg.role === 'assistant' && !msg.isError && msg.queryType && (
              <span className="message__query-type" title="Query classification">
                {' '}{msg.queryType}
              </span>
            )}
          </div>
          )}
          {msg.imageUri && msg.role === 'user' && (
            <div className="message__media">
              <img src={msg.imageUri} alt="Attached" className="message__image" />
            </div>
          )}
          {!(msg.products && msg.products.length > 0 && /^Showing \d+ (of (at least )?\d+ )?products?$/.test(msg.content?.trim() ?? '')) && (
            <div className="message__content">{msg.content}</div>
          )}
          {msg.isError && (onRetry || onDismissError) && (
            <div className="message__actions">
              {onRetry && (
                <button
                  type="button"
                  className="message__action message__action--retry"
                  onClick={() => {
                    const prevUser = messages[idx - 1];
                    if (prevUser?.role === 'user')
                      onRetry(prevUser.content, msg.id, prevUser.imageBase64);
                  }}
                  aria-label="Retry sending message"
                >
                  Retry
                </button>
              )}
              {onDismissError && (
                <button
                  type="button"
                  className="message__action message__action--dismiss"
                  onClick={() => onDismissError(msg.id)}
                  aria-label="Dismiss error"
                >
                  Dismiss
                </button>
              )}
            </div>
          )}
          {msg.products && msg.products.length > 0 && (
            <div className="message__products">
              <h4>Products</h4>
              <div className="message__product-count-section">
                <span className="message__product-count-source">Application</span>
                <p className="message__product-count" aria-live="polite">
                  {msg.productTotalSize != null && msg.productTotalSize >= 0
                    ? `Showing ${msg.products.length} of ${msg.productTotalSizeIsApproximate ? 'at least ' : ''}${msg.productTotalSize} ${msg.productTotalSize === 1 ? 'product' : 'products'}`
                    : `Showing ${msg.products.length} ${msg.products.length === 1 ? 'product' : 'products'}`}
                </p>
              </div>
              {msg.role === 'assistant' && !msg.isError && (
                <div className="message__role message__role--product-grid">
                  Assistant
                  {msg.source === 'agent' && (
                    <span className="message__source" title="Conversational Commerce agent">
                      {' '}(agent)
                    </span>
                  )}
                  {msg.queryType && (
                    <span className="message__query-type" title="Query classification">
                      {' '}{msg.queryType}
                    </span>
                  )}
                </div>
              )}
              <div className="product-grid">
                {msg.products.map((p, idx) => (
                  <ProductCard key={p.id || `p-${idx}`} product={p} index={idx} />
                ))}
              </div>
              {msg.productNextPageToken && onLoadMore && (
                <button
                  type="button"
                  className="message__load-more"
                  onClick={() => onLoadMore(msg)}
                  disabled={loading}
                  aria-label="Load more products"
                >
                  Load more
                </button>
              )}
              {msg.clarifyingQuestion && (
                <div className="message__clarifying-block">
                  <p className="message__clarifying-question" aria-live="polite">
                    {msg.clarifyingQuestion}
                  </p>
                  {msg.suggestedAnswers && msg.suggestedAnswers.length > 0 && onSuggestedAnswer && (
                    <div className="message__suggested-answers message__suggested-answers--after-clarifying">
                      {(maxSuggestedAnswers != null
                        ? msg.suggestedAnswers.slice(0, maxSuggestedAnswers)
                        : msg.suggestedAnswers
                      ).map((answer, i) => (
                        <button
                          key={i}
                          type="button"
                          className="message__suggested-answer"
                          onClick={() => onSuggestedAnswer(answer)}
                        >
                          {suggestedAnswerDisplayLabel(answer)}
                        </button>
                      ))}
                      {onGetMoreSuggestions &&
                        msg.role === 'assistant' &&
                        !msg.isError &&
                        msg.suggestedAnswers &&
                        msg.suggestedAnswers.length > 0 &&
                        msg.id === [...messages].reverse().find((m) => m.role === 'assistant' && m.suggestedAnswers?.length)?.id && (
                          <button
                            type="button"
                            className="message__get-more-suggestions"
                            onClick={onGetMoreSuggestions}
                            disabled={loading}
                            aria-label="Get more suggested answers"
                          >
                            Get more suggestions
                          </button>
                        )}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
          {msg.suggestedAnswers && msg.suggestedAnswers.length > 0 && onSuggestedAnswer && !msg.clarifyingQuestion && (
            <div className="message__suggested-answers">
              {(maxSuggestedAnswers != null
                ? msg.suggestedAnswers.slice(0, maxSuggestedAnswers)
                : msg.suggestedAnswers
              ).map((answer, i) => (
                <button
                  key={i}
                  type="button"
                  className="message__suggested-answer"
                  onClick={() => onSuggestedAnswer(answer)}
                >
                  {suggestedAnswerDisplayLabel(answer)}
                </button>
              ))}
              {onGetMoreSuggestions &&
                msg.role === 'assistant' &&
                !msg.isError &&
                msg.suggestedAnswers &&
                msg.suggestedAnswers.length > 0 &&
                msg.id === [...messages].reverse().find((m) => m.role === 'assistant' && m.suggestedAnswers?.length)?.id && (
                  <button
                    type="button"
                    className="message__get-more-suggestions"
                    onClick={onGetMoreSuggestions}
                    disabled={loading}
                    aria-label="Get more suggested answers"
                  >
                    Get more suggestions
                  </button>
                )}
            </div>
          )}
        </div>
        );
      })}
      {loading && (
        <div className="message message--assistant message--loading" role="status" aria-live="polite">
          <div className="message__role">Assistant</div>
          <div className="message__content message__content--loading">
            <span className="message__spinner" aria-hidden="true" />
            <span>Searching...</span>
          </div>
        </div>
      )}
      <div ref={bottomRef} aria-hidden="true" />
    </div>
  );
}

export const MessageList = memo(MessageListComponent);
