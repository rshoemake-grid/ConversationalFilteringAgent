import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

/** jsdom/Node sometimes exposes incomplete localStorage; tests need full Storage API for orchestration persistence. */
function memoryLocalStorage(): Storage {
  const m = new Map<string, string>()
  return {
    get length() {
      return m.size
    },
    clear: () => m.clear(),
    getItem: (key: string) => (m.has(key) ? m.get(key)! : null),
    setItem: (key: string, value: string) => void m.set(key, String(value)),
    removeItem: (key: string) => void m.delete(key),
    key: (i: number) => [...m.keys()][i] ?? null,
  } as Storage
}

beforeEach(() => {
  vi.stubGlobal('localStorage', memoryLocalStorage())
})

afterEach(() => cleanup())
