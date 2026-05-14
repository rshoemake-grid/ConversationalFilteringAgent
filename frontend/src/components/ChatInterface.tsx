import { useRef, useEffect, useState, useMemo } from 'react';
import { isGoogleChrome, VOICE_OUTPUT_CHROME_ONLY_TITLE } from '../utils/chromeVoiceSupport';
import { ModeSelector } from './ModeSelector';
import { MaxSuggestedAnswersControl } from './MaxSuggestedAnswersControl';
import { PageSizeControl } from './PageSizeControl';
import { MessageList } from './MessageList';
import { VoiceInput } from './VoiceInput';
import { ImageInput } from './ImageInput';
import { VoiceOutputToggle } from './VoiceOutputToggle';
import { RawResponsePanel } from './RawResponsePanel';
import { useChat } from '../hooks/useChat';

export function ChatInterface() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [showRawOutput, setShowRawOutput] = useState(true);
  const {
    mode,
    setMode,
    maxSuggestedAnswers,
    setMaxSuggestedAnswers,
    maxSuggestedAnswersCap,
    productPageSize,
    setProductPageSize,
    messages,
    rawResponseHistory,
    input,
    setInput,
    pendingImage,
    loading,
    voiceOutputEnabled,
    setVoiceOutputEnabled,
    isSpeaking,
    stopSpeaking,
    handleSend,
    handleVoiceResult,
    handleSuggestedAnswer,
    handleImageSelect,
    clearPendingImage,
    handleRetry,
    handleDismissError,
    handleGetMoreSuggestions,
    handleLoadMore,
    startNewConversation,
    hasActiveRetailFilter,
    activeRetailSessionFilter,
    clearRetailFilters,
  } = useChat();

  const voiceRequiresChrome = useMemo(() => !isGoogleChrome(), []);

  useEffect(() => {
    if (!loading && inputRef.current) {
      inputRef.current.focus();
    }
  }, [loading]);

  return (
    <div className="chat-layout">
      <div className="chat-interface">
        <header className="chat-header">
        <h1>Conversational Filtering Agent</h1>
        <div className="chat-header__controls">
          <button
            type="button"
            onClick={startNewConversation}
            disabled={loading}
            className="chat-header__new-conversation"
            aria-label="Start new conversation"
            title="Start new conversation"
          >
            New conversation
          </button>
          <button
            type="button"
            onClick={clearRetailFilters}
            disabled={loading || !hasActiveRetailFilter}
            className="chat-header__toggle-raw"
            aria-label="Clear Retail filters"
            title={
              hasActiveRetailFilter && activeRetailSessionFilter
                ? `Clear accumulated Retail catalog filters (current: ${activeRetailSessionFilter.slice(0, 120)}${activeRetailSessionFilter.length > 120 ? '…' : ''}). The next message runs a fresh catalog search instead of reusing the last product grid.`
                : 'No Retail catalog filters are applied in this session.'
            }
          >
            Clear Retail filters
          </button>
          <ModeSelector
            value={mode}
            onChange={setMode}
            disabled={loading}
          />
          <MaxSuggestedAnswersControl
            value={maxSuggestedAnswers}
            onChange={setMaxSuggestedAnswers}
            max={maxSuggestedAnswersCap}
            disabled={loading}
          />
          <PageSizeControl
            value={productPageSize}
            onChange={setProductPageSize}
            disabled={loading}
          />
          <VoiceOutputToggle
            enabled={voiceRequiresChrome ? false : voiceOutputEnabled}
            onToggle={setVoiceOutputEnabled}
            isSpeaking={voiceRequiresChrome ? false : isSpeaking}
            onStop={voiceRequiresChrome ? undefined : stopSpeaking}
            disabled={loading || voiceRequiresChrome}
            chromeOnlyTitle={voiceRequiresChrome ? VOICE_OUTPUT_CHROME_ONLY_TITLE : undefined}
          />
          <button
            type="button"
            onClick={() => setShowRawOutput((v) => !v)}
            className="chat-header__toggle-raw"
            aria-label={showRawOutput ? 'Hide raw output' : 'Show raw output'}
            title={showRawOutput ? 'Hide raw API response panel' : 'Show raw API response panel'}
          >
            {showRawOutput ? 'Hide raw output' : 'Show raw output'}
          </button>
        </div>
      </header>

      <MessageList
        messages={messages}
        loading={loading}
        maxSuggestedAnswers={maxSuggestedAnswers}
        productPageSize={productPageSize}
        onRetry={handleRetry}
        onDismissError={handleDismissError}
        onSuggestedAnswer={handleSuggestedAnswer}
        onGetMoreSuggestions={handleGetMoreSuggestions}
        onLoadMore={handleLoadMore}
      />

      <div className="chat-input-area">
        <div className="chat-input-area__row">
          {pendingImage && (
            <div className="chat-input-area__preview">
              <img src={pendingImage.startsWith('data:') ? pendingImage : `data:image/jpeg;base64,${pendingImage}`} alt="Preview" className="chat-input-area__preview-img" />
              <button type="button" onClick={clearPendingImage} className="chat-input-area__preview-remove" aria-label="Remove image">×</button>
            </div>
          )}
          <div className="chat-input-area__inputs">
            <input
              ref={inputRef}
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
              placeholder="Type your message..."
              disabled={loading}
              aria-label="Chat message"
              aria-busy={loading}
            />
            <div className="chat-input-area__buttons">
              <VoiceInput onResult={handleVoiceResult} disabled={loading} />
              <ImageInput onSelect={handleImageSelect} disabled={loading} />
              <button
                type="button"
                className="chat-input-area__send"
                onClick={handleSend}
                disabled={loading}
                aria-label={loading ? 'Sending message' : 'Send message'}
              >
                {loading ? 'Sending...' : 'Send'}
              </button>
            </div>
          </div>
        </div>
      </div>
      </div>
      {showRawOutput && (
        <aside className="raw-response-sidebar">
          <RawResponsePanel history={rawResponseHistory} />
        </aside>
      )}
    </div>
  );
}
