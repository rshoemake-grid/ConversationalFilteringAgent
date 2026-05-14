export type OrchestrationMode = 'convo_commerce' | 'adk_orchestrator';

export interface ProductDto {
  id: string;
  title: string;
  description: string;
  price: string;
  imageUri?: string;
  /** UPC/GTIN for product lookup */
  gtin?: string;
  /** Short product id (final component of name) */
  productId?: string;
  categories?: string[];
  brands?: string[];
  uri?: string;
  availability?: string;
  sizes?: string[];
  materials?: string[];
  /** Custom attributes (key -> value) */
  attributes?: Record<string, unknown>;
  /** True when details were fetched via Product.Get to fill missing fields */
  detailsFetched?: boolean;
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  products?: ProductDto[];
  /** Quick-reply options from GCP suggestedAnswers: displayText for UI, value sent to API */
  suggestedAnswers?: SuggestedAnswer[];
  isError?: boolean;
  /** Image data URL for display (e.g. data:image/jpeg;base64,...) */
  imageUri?: string;
  /** Raw base64 for retry when message had image */
  imageBase64?: string;
  /** "agent" = Conversational Commerce or ADK agent, "app" = app-generated fallback */
  source?: 'agent' | 'app';
  /** GCP query classification, e.g. SIMPLE_PRODUCT_SEARCH, GENERAL_QUESTION */
  queryType?: string;
  /** Refined query from last turn (for RETAIL_IRRELEVANT recovery when user says Any/no preference) */
  refinedQuery?: string;
  /** Total products matching search (GCP estimate); for "Showing X–Y of Z" */
  productTotalSize?: number;
  /** True when productTotalSize is approximated (pages×pageSize) from raw search */
  productTotalSizeIsApproximate?: boolean;
  /** @deprecated Not used for Retail listing; backend merges pages on first search only */
  productNextPageToken?: string;
  /** Retail filter from last response (context; no Retail load-more) */
  productFilter?: string;
  /** Clarifying question shown after products (e.g. "Would you like 12oz or 24oz?") */
  clarifyingQuestion?: string;
}

export interface ChatRequest {
  mode: OrchestrationMode;
  message: string;
  conversationId?: string;
  sessionId?: string;
  /** Optional base64-encoded image (with or without data URL prefix) for visual search */
  imageBase64?: string;
  /** Max suggested answers to request (null/omit = no limit). Display is also sliced client-side for real-time control. */
  maxSuggestedAnswers?: number;
  /** Previous assistant text for no-products / storage fallback (prefer the clarifying question, not count-only lines) */
  previousAssistantText?: string;
  /** Previous assistant suggested answers (for no-products fallback) */
  previousSuggestedAnswers?: SuggestedAnswer[];
  /** Previous refined query (for RETAIL_IRRELEVANT recovery when user says Any/no preference) */
  previousRefinedQuery?: string;
  /** @deprecated Ignored for Retail; do not use for Google paging */
  productPageToken?: string;
  /** Filter from previous response (context only) */
  previousProductFilter?: string;
  /** Prior assistant productTotalSize; backend may keep broader catalog count when narrowing */
  previousProductTotalSize?: number;
  /** Echo prior assistant productTotalSizeIsApproximate with previousProductTotalSize */
  previousProductTotalSizeIsApproximate?: boolean;
  /** Hint for UI/heuristics only; does not cap initial catalog or trigger Retail paging */
  productPageSize?: number;
  /**
   * Echo the last VAISR product grid for in-memory refinement (follow-up turns).
   * Omitted on the first catalog search.
   */
  productPool?: ProductDto[];
  /** When productPool is sent, allow Vertex semantic reranking (default true). */
  useSemanticReranking?: boolean;
}

export interface ChatResponse {
  text: string;
  conversationId?: string;
  refinedQuery?: string;
  products?: ProductDto[];
  /** "agent" = Conversational Commerce or ADK agent, "app" = app-generated fallback */
  source?: 'agent' | 'app';
  /** GCP query classification, e.g. SIMPLE_PRODUCT_SEARCH, GENERAL_QUESTION */
  queryType?: string;
  /** Raw JSON response from GCP API (REST transport only) */
  rawResponse?: string;
  /** Quick-reply options from GCP suggestedAnswers: displayText for UI, value sent to API */
  suggestedAnswers?: SuggestedAnswer[];
  /** Total products matching search (GCP estimate) */
  productTotalSize?: number;
  /** True when productTotalSize is approximated */
  productTotalSizeIsApproximate?: boolean;
  /** @deprecated Rarely set; no Retail listing continuation in this app */
  productNextPageToken?: string;
  /** Retail filter for the initial merged listing (session / merge context) */
  productFilter?: string;
  /** Clarifying question shown after products */
  clarifyingQuestion?: string;
}

/** Display text for UI; value is sent to API when user selects */
export interface SuggestedAnswer {
  displayText: string;
  value: string;
}
