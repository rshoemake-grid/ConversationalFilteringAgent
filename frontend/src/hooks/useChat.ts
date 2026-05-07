import { useState, useCallback, useRef, useEffect } from 'react';
import { sendChatMessage } from '../api/chatApi';
import { useVoiceOutput } from './useVoiceOutput';
import type { Message, OrchestrationMode, ChatResponse, SuggestedAnswer } from '../api/types';
import {
  isSuggestedAnswerExcluded,
  suggestedAnswerDisplayLabel,
  suggestedAnswerSubmitValue,
} from '../utils/suggestedAnswerDisplay';

function createUserMessage(text: string, imageUri?: string, imageBase64?: string): Message {
  return {
    id: `u-${crypto.randomUUID()}`,
    role: 'user',
    content: text || (imageUri ? '[Image]' : ''),
    imageUri,
    imageBase64,
  };
}

function formatProductCount(shown: number, total?: number | null, isApproximate?: boolean): string {
  if (total != null && total >= 0) {
    const prefix = isApproximate ? 'at least ' : '';
    return shown === 1
      ? `Showing 1 of ${prefix}${total} product`
      : `Showing ${shown} of ${prefix}${total} products`;
  }
  return shown === 1 ? 'Showing 1 product' : `Showing ${shown} products`;
}

function createAssistantMessage(response: { text?: string; products?: Message['products']; refinedQuery?: string; source?: Message['source']; queryType?: string; suggestedAnswers?: SuggestedAnswer[]; rawResponse?: string | null; productTotalSize?: number; productTotalSizeIsApproximate?: boolean; productNextPageToken?: string | null; productFilter?: string | null; clarifyingQuestion?: string | null }): Message {
  let text = (response.text ?? '').trim();
  const products = response.products ?? [];
  const refinedQuery = response.refinedQuery ?? '';
  const total = response.productTotalSize;
  const isApprox = response.productTotalSizeIsApproximate ?? false;
  // When we have products but text is "Searching for:" placeholder, show count instead
  if (products.length > 0 && text.startsWith('Searching for:')) {
    text = formatProductCount(products.length, total, isApprox);
  }
  // Normalize "I found N products matching your request" to "Showing X of Y products" when we have total
  else if (products.length > 0 && total != null && total >= 0 && /^I found \d+ product(s)? matching your request\.$/.test(text)) {
    text = formatProductCount(products.length, total, isApprox);
  }
  // When no products and text is placeholder or empty, show explicit no-results message
  else if (products.length === 0 && refinedQuery && (text === '' || text.startsWith('Searching for:'))) {
    text = 'No products found.';
  }
  const hasContent = text.length > 0 || products.length > 0;
  // Use backend suggestedAnswers, or fallback to extracting from rawResponse (unless [] was explicitly passed)
  let suggestedAnswers = Array.isArray(response.suggestedAnswers) ? response.suggestedAnswers : undefined;
  if (suggestedAnswers === undefined && response.rawResponse) {
    const extracted = extractSuggestedAnswersFromRaw(response.rawResponse);
    if (extracted.length > 0) {
      suggestedAnswers = extracted.map((v) => {
        const sa: SuggestedAnswer = {
          displayText: looksLikeBrandCode(v) ? toTitleCase(v) : v,
          value: v,
        };
        return { ...sa, displayText: suggestedAnswerDisplayLabel(sa) };
      });
    }
  }
  if ((!suggestedAnswers || suggestedAnswers.length === 0) && text && text.includes('?')) {
    suggestedAnswers = [{ displayText: 'Any', value: 'ANY' }];
  }
  suggestedAnswers = normalizeSuggestedAnswerList(suggestedAnswers);
  return {
    id: `a-${crypto.randomUUID()}`,
    role: 'assistant',
    content: hasContent ? text : "I didn't understand your response.",
    products,
    source: response.source,
    queryType: response.queryType,
    suggestedAnswers,
    refinedQuery: response.refinedQuery ?? undefined,
    productTotalSize: response.productTotalSize ?? undefined,
    productTotalSizeIsApproximate: response.productTotalSizeIsApproximate ?? undefined,
    productNextPageToken: response.productNextPageToken ?? undefined,
    productFilter: response.productFilter ?? undefined,
    clarifyingQuestion: response.clarifyingQuestion ?? undefined,
  };
}

function createErrorMessage(err: unknown): Message {
  return {
    id: `e-${crypto.randomUUID()}`,
    role: 'assistant',
    content: err instanceof Error ? err.message : 'Unknown error',
    isError: true,
  };
}

/** Extract raw base64 from data URL (data:image/...;base64,XXXX) */
function toRawBase64(dataUrl: string): string {
  const idx = dataUrl.indexOf(',');
  return idx >= 0 ? dataUrl.slice(idx + 1) : dataUrl;
}

function looksLikeBrandCode(s: string): boolean {
  return /^[A-Z0-9]+$/.test(s) && s.length >= 2 && s.length <= 30;
}

function toTitleCase(s: string): string {
  if (!s || s.length === 0) return s;
  if (s.length === 1) return s.toUpperCase();
  return s[0].toUpperCase() + s.slice(1).toLowerCase();
}

function normalizeSuggestedAnswerList(list: SuggestedAnswer[] | undefined): SuggestedAnswer[] | undefined {
  if (!list?.length) return list;
  return list.map((sa) => ({
    ...sa,
    displayText: suggestedAnswerDisplayLabel(sa),
  }));
}

/** Last non-empty refinedQuery from assistant messages. Used for no-preference recovery when INTENT_REFINEMENT has no refinedQuery. */
function getLastNonEmptyRefinedQuery(messages: Message[]): string | undefined {
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i];
    if (m.role === 'assistant' && !m.isError && m.refinedQuery?.trim()) {
      return m.refinedQuery.trim();
    }
  }
  return undefined;
}

/**
 * Text sent as previousAssistantText for storage / no-products recovery.
 * When the assistant showed products, `content` is often only "Showing N of M products" and the
 * real follow-up (e.g. "What type of stock do you prefer?") lives in `clarifyingQuestion`. Sending
 * only `content` makes the backend re-ask "Showing 20 of 4712" after a failed pick.
 */
function previousAssistantTextForContext(m: Message | undefined): string | undefined {
  if (!m) return undefined;
  const cq = m.clarifyingQuestion?.trim();
  if (cq) return cq;
  return m.content?.trim();
}
function extractValueFromSuggestedAnswer(item: unknown): string | null {
  if (typeof item === 'string' && item.trim()) return item.trim();
  if (item && typeof item === 'object') {
    const obj = item as Record<string, unknown>;
    const pav = obj.productAttributeValue ?? obj.product_attribute_value;
    if (pav && typeof pav === 'object') {
      const v = (pav as Record<string, unknown>).value;
      if (v != null) return String(v).trim();
    }
    const t = obj.text ?? obj.answer;
    if (t != null) return String(t).trim();
  }
  return null;
}

/** Extract suggestedAnswers from raw GCP API JSON when backend doesn't provide them */
function extractSuggestedAnswersFromRaw(rawJson: string | null | undefined): string[] {
  if (!rawJson?.trim()) return [];
  const results: string[] = [];
  const parseChunk = (obj: unknown): void => {
    if (!obj || typeof obj !== 'object') return;
    const m = obj as Record<string, unknown>;
    const cfr = m.conversationalFilteringResult ?? m.conversational_filtering_result;
    if (cfr && typeof cfr === 'object') {
      const cfrObj = cfr as Record<string, unknown>;
      const sa = cfrObj.suggestedAnswers ?? cfrObj.suggested_answers;
      if (Array.isArray(sa)) {
        for (const item of sa) {
          const v = extractValueFromSuggestedAnswer(item);
          if (v) results.push(v);
        }
      }
      const fq = cfrObj.followupQuestion ?? cfrObj.followup_question;
      if (fq && typeof fq === 'object') {
        const fqObj = fq as Record<string, unknown>;
        const fqSa = fqObj.suggestedAnswers ?? fqObj.suggested_answers;
        if (Array.isArray(fqSa)) {
          for (const item of fqSa) {
            const v = extractValueFromSuggestedAnswer(item);
            if (v) results.push(v);
          }
        }
      }
    }
    const csr = m.conversationalSearchResult ?? m.conversational_search_result;
    if (csr && typeof csr === 'object') {
      const csrObj = csr as Record<string, unknown>;
      const sa = csrObj.suggestedAnswers ?? csrObj.suggested_answers;
      if (Array.isArray(sa)) {
        for (const item of sa) {
          const v = extractValueFromSuggestedAnswer(item);
          if (v) results.push(v);
        }
      }
    }
  };
  try {
    const parsed = JSON.parse(rawJson);
    if (Array.isArray(parsed)) {
      for (const chunk of parsed) parseChunk(chunk);
    } else {
      parseChunk(parsed);
    }
    // Also try NDJSON (newline-delimited)
    if (results.length === 0 && rawJson.includes('\n')) {
      for (const line of rawJson.split('\n')) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        try {
          parseChunk(JSON.parse(trimmed));
        } catch {
          // ignore
        }
      }
    }
  } catch {
    // ignore
  }
  return results;
}

const DEFAULT_MAX_SUGGESTED_ANSWERS = 8;
const MAX_SUGGESTED_ANSWERS_CAP = 50;
const DEFAULT_PRODUCT_PAGE_SIZE = 20;

export function useChat() {
  const [mode, setMode] = useState<OrchestrationMode>('convo_commerce');
  const [maxSuggestedAnswers, setMaxSuggestedAnswers] = useState(DEFAULT_MAX_SUGGESTED_ANSWERS);
  const [productPageSize, setProductPageSize] = useState(DEFAULT_PRODUCT_PAGE_SIZE);
  const [messages, setMessages] = useState<Message[]>([]);
  const [rawResponseHistory, setRawResponseHistory] = useState<Array<{ rawResponse?: string | null; fallbackResponse?: ChatResponse | null }>>([]);
  const [input, setInput] = useState('');
  const [pendingImage, setPendingImage] = useState<string | null>(null);
  const [voiceOutputEnabled, setVoiceOutputEnabled] = useState(false);
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>();
  const [sessionId] = useState(() => `session-${crypto.randomUUID()}`);
  const { speak, stop: stopSpeaking, isSpeaking } = useVoiceOutput();

  const inputRef = useRef(input);
  const conversationIdRef = useRef<string | undefined>(undefined);
  /** Values from suggested answers that were tried and resulted in the same assistant message (no products) */
  const failedSuggestedValuesRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    inputRef.current = input;
  }, [input]);
  useEffect(() => {
    conversationIdRef.current = conversationId;
  }, [conversationId]);

  const sendMessage = useCallback(
    async (
      messageText: string,
      options?: {
        imageBase64?: string;
        removeErrorId?: string;
        previousAssistantText?: string;
        previousSuggestedAnswers?: SuggestedAnswer[];
        previousRefinedQuery?: string;
        productPageToken?: string;
        previousProductFilter?: string;
        appendProductsToMessageId?: string;
      }
    ) => {
      if (loading) return;
      const hasText = messageText != null && messageText.trim().length > 0;
      const hasImage = options?.imageBase64 != null && options.imageBase64.length > 0;
      const hasLoadMore = options?.productPageToken != null && options.productPageToken.length > 0;
      if (!hasText && !hasImage && !hasLoadMore) return;

      if (options?.removeErrorId) {
        setMessages((prev) => prev.filter((m) => m.id !== options.removeErrorId));
      }
      setLoading(true);

      const currentConversationId = conversationIdRef.current;

      try {
        const response = await sendChatMessage({
          mode,
          message: hasText ? messageText : '',
          conversationId: currentConversationId,
          sessionId,
          imageBase64: options?.imageBase64,
          maxSuggestedAnswers: maxSuggestedAnswers > 0 ? maxSuggestedAnswers : undefined,
          previousAssistantText: options?.previousAssistantText,
          previousSuggestedAnswers: options?.previousSuggestedAnswers,
          previousRefinedQuery: options?.previousRefinedQuery,
          productPageToken: options?.productPageToken,
          previousProductFilter: options?.previousProductFilter,
          productPageSize: productPageSize > 0 ? productPageSize : undefined,
        });
        if (response.conversationId != null && response.conversationId !== '') {
          conversationIdRef.current = response.conversationId;
          setConversationId(response.conversationId);
        }
        setMessages((prev) => {
          const lastMsg = prev[prev.length - 1];
          const lastUserContent = lastMsg?.role === 'user' ? lastMsg.content?.trim() : null;
          const prevAssistant = [...prev].reverse().find((m) => m.role === 'assistant' && !m.isError);
          const prevAssistantContent = prevAssistant?.content?.trim() ?? '';
          const responseText = (response.text ?? '').trim();

          let failedSet = failedSuggestedValuesRef.current;
          const noProducts = (response.products?.length ?? 0) === 0;
          const isDidNotUnderstand = responseText.includes("I didn't understand your response.");
          if (isDidNotUnderstand) {
            failedSuggestedValuesRef.current = new Set();
            failedSet = new Set();
          } else if (lastUserContent) {
            if (responseText === prevAssistantContent || (noProducts && responseText.includes('No products found'))) {
              failedSet = new Set(failedSet);
              failedSet.add(lastUserContent);
              failedSuggestedValuesRef.current = failedSet;
            } else if (prevAssistantContent && responseText !== prevAssistantContent && !noProducts) {
              failedSuggestedValuesRef.current = new Set();
              failedSet = new Set();
            }
          } else if (prevAssistantContent && responseText !== prevAssistantContent) {
            failedSuggestedValuesRef.current = new Set();
            failedSet = new Set();
          }

          const filterExcluded = (list: SuggestedAnswer[]) =>
            list.filter((sa) => !isSuggestedAnswerExcluded(sa, failedSet));

          let suggestedAnswers = response.suggestedAnswers?.length ? filterExcluded(response.suggestedAnswers) : undefined;
          if (!suggestedAnswers?.length && response.rawResponse) {
            const extracted = extractSuggestedAnswersFromRaw(response.rawResponse);
            if (extracted.length > 0) {
              const asSuggested = extracted.map((v) => {
                const sa: SuggestedAnswer = {
                  displayText: looksLikeBrandCode(v) ? toTitleCase(v) : v,
                  value: v,
                };
                return { ...sa, displayText: suggestedAnswerDisplayLabel(sa) };
              });
              suggestedAnswers = filterExcluded(asSuggested);
            }
          }
          suggestedAnswers = normalizeSuggestedAnswerList(suggestedAnswers ?? []) ?? [];

          const filteredResponse = {
            ...response,
            suggestedAnswers,
          };
          if (options?.appendProductsToMessageId && response.products && response.products.length > 0) {
            return prev.map((m) => {
              if (m.id !== options.appendProductsToMessageId || m.role !== 'assistant') return m;
              const merged = { ...m, products: [...(m.products ?? []), ...response.products!] };
              if (response.productNextPageToken != null) merged.productNextPageToken = response.productNextPageToken;
              if (response.productTotalSize != null) merged.productTotalSize = response.productTotalSize;
              if (response.productTotalSizeIsApproximate != null) merged.productTotalSizeIsApproximate = response.productTotalSizeIsApproximate;
              if (response.productFilter != null) merged.productFilter = response.productFilter;
              return merged;
            });
          }
          return [...prev, createAssistantMessage(filteredResponse)];
        });
        setRawResponseHistory((prev) => [...prev, { rawResponse: response.rawResponse ?? null, fallbackResponse: response }]);
        if (voiceOutputEnabled && (response.text?.trim() || response.clarifyingQuestion?.trim())) {
          const fullText = [response.text?.trim(), response.clarifyingQuestion?.trim()].filter(Boolean).join(' ');
          speak(fullText);
        }
      } catch (err) {
        setMessages((prev) => [...prev, createErrorMessage(err)]);
      } finally {
        setLoading(false);
      }
    },
    [loading, mode, maxSuggestedAnswers, productPageSize, sessionId, voiceOutputEnabled, speak]
  );

  const handleSend = useCallback(() => {
    const text = inputRef.current.trim();
    const imageData = pendingImage;
    const hasText = text.length > 0;
    const hasImage = imageData != null && imageData.length > 0;
    if ((!hasText && !hasImage) || loading) return;

    const displayUri = hasImage && imageData
      ? (imageData.startsWith('data:') ? imageData : `data:image/jpeg;base64,${imageData}`)
      : undefined;
    const rawBase64 = displayUri ? toRawBase64(displayUri) : undefined;

    const lastAssistant = [...messages].reverse().find((m) => m.role === 'assistant' && !m.isError);

    setInput('');
    setPendingImage(null);
    setMessages((prev) => [
      ...prev,
      createUserMessage(text || '[Image]', displayUri, rawBase64),
    ]);
    sendMessage(hasText ? text : '', {
      imageBase64: rawBase64,
      previousAssistantText: previousAssistantTextForContext(lastAssistant),
      previousSuggestedAnswers: lastAssistant?.suggestedAnswers,
      previousRefinedQuery: getLastNonEmptyRefinedQuery(messages) ?? lastAssistant?.refinedQuery,
    });
  }, [loading, pendingImage, sendMessage, messages]);

  const handleVoiceResult = useCallback(
    (transcript: string) => {
      const t = transcript.trim();
      if (!t || loading) return;
      // Filter false positives: "Attached"/"attach" often transcribed when no image is uploaded
      if (/^(attached|attach)$/i.test(t)) return;
      const lastAssistant = [...messages].reverse().find((m) => m.role === 'assistant' && !m.isError);
      setMessages((prev) => [...prev, createUserMessage(t)]);
      sendMessage(t, {
        previousAssistantText: previousAssistantTextForContext(lastAssistant),
        previousSuggestedAnswers: lastAssistant?.suggestedAnswers,
        previousRefinedQuery: getLastNonEmptyRefinedQuery(messages) ?? lastAssistant?.refinedQuery,
      });
    },
    [loading, sendMessage, messages]
  );

  const handleSuggestedAnswer = useCallback(
    (answer: SuggestedAnswer) => {
      const submit = suggestedAnswerSubmitValue(answer);
      const label = suggestedAnswerDisplayLabel(answer);
      if (!submit || loading) return;
      const lastAssistant = [...messages].reverse().find((m) => m.role === 'assistant' && !m.isError);
      setMessages((prev) => [...prev, createUserMessage(label)]);
      sendMessage(submit, {
        previousAssistantText: previousAssistantTextForContext(lastAssistant),
        previousSuggestedAnswers: lastAssistant?.suggestedAnswers,
        previousRefinedQuery: getLastNonEmptyRefinedQuery(messages) ?? lastAssistant?.refinedQuery,
      });
    },
    [loading, sendMessage, messages]
  );

  const handleImageSelect = useCallback((dataUrl: string) => {
    setPendingImage(dataUrl);
  }, []);

  const clearPendingImage = useCallback(() => {
    setPendingImage(null);
  }, []);

  const handleRetry = useCallback(
    (messageText: string, errorId: string, imageBase64?: string) => {
      const lastAssistant = [...messages].reverse().find((m) => m.role === 'assistant' && !m.isError);
      sendMessage(messageText, {
        removeErrorId: errorId,
        imageBase64,
        previousAssistantText: previousAssistantTextForContext(lastAssistant),
        previousSuggestedAnswers: lastAssistant?.suggestedAnswers,
        previousRefinedQuery: getLastNonEmptyRefinedQuery(messages) ?? lastAssistant?.refinedQuery,
      });
    },
    [sendMessage, messages]
  );

  /** Resend the last user message to get fresh suggested answers; clears failed-suggested set so previously-excluded options may reappear */
  const handleGetMoreSuggestions = useCallback(() => {
    const lastUser = [...messages].reverse().find((m) => m.role === 'user');
    if (!lastUser || loading) return;
    const lastUserIdx = messages.findIndex((m) => m === lastUser);
    const prevAssistant =
      lastUserIdx >= 0 ? messages.slice(0, lastUserIdx).reverse().find((m) => m.role === 'assistant' && !m.isError) : undefined;
    failedSuggestedValuesRef.current = new Set();
    const text = lastUser.content?.trim() ?? '';
    const imageBase64 = lastUser.imageBase64;
    if (text || imageBase64) {
      setMessages((prev) => [...prev, createUserMessage(text || '[Image]', lastUser.imageUri ?? undefined, imageBase64)]);
      sendMessage(text, {
        imageBase64,
        previousAssistantText: previousAssistantTextForContext(prevAssistant),
        previousSuggestedAnswers: prevAssistant?.suggestedAnswers,
        previousRefinedQuery: getLastNonEmptyRefinedQuery(messages) ?? prevAssistant?.refinedQuery,
      });
    }
  }, [messages, loading, sendMessage]);

  const handleDismissError = useCallback((messageId: string) => {
    setMessages((prev) => prev.filter((m) => m.id !== messageId));
  }, []);

  const handleLoadMore = useCallback(
    (msg: Message) => {
      if (!msg.productNextPageToken || loading) return;
      const refined = msg.refinedQuery ?? getLastNonEmptyRefinedQuery(messages);
      if (!refined?.trim()) return;
      sendMessage('', {
        productPageToken: msg.productNextPageToken,
        previousRefinedQuery: refined,
        previousProductFilter: msg.productFilter ?? undefined,
        appendProductsToMessageId: msg.id,
      });
    },
    [loading, sendMessage, messages]
  );

  const startNewConversation = useCallback(() => {
    setMessages([]);
    setRawResponseHistory([]);
    conversationIdRef.current = undefined;
    setConversationId(undefined);
    setInput('');
    setPendingImage(null);
    failedSuggestedValuesRef.current = new Set();
    stopSpeaking();
  }, [stopSpeaking]);

  return {
    mode,
    setMode,
    maxSuggestedAnswers,
    setMaxSuggestedAnswers,
    maxSuggestedAnswersCap: MAX_SUGGESTED_ANSWERS_CAP,
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
  };
}
