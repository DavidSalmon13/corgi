import { createContext, useState, type ReactNode } from 'react';

/**
 * Which occurrence of each insurance term on screen carries the "?". Every `<Term>` registers its element under its
 * explanation; the first one in document order shows the mark and the rest stay plain (still tappable, with the same
 * explanation). A whole page shares one scope, and a dialog opens its own, since the page behind it is inert.
 */
export class GlossRegistry {
  private readonly terms = new Map<string, Set<HTMLElement>>();
  private readonly listeners = new Set<() => void>();

  add(key: string, el: HTMLElement): void {
    let set = this.terms.get(key);
    if (!set) {
      set = new Set();
      this.terms.set(key, set);
    }
    set.add(el);
    this.emit();
  }

  remove(key: string, el: HTMLElement): void {
    const set = this.terms.get(key);
    if (!set) return;
    set.delete(el);
    if (set.size === 0) this.terms.delete(key);
    this.emit();
  }

  /** True when `el` is the first registered occurrence of `key` in the document. */
  isFirst(key: string, el: HTMLElement): boolean {
    const set = this.terms.get(key);
    if (!set) return true;
    for (const other of set) {
      if (other !== el && other.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING) return false;
    }
    return true;
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(): void {
    this.listeners.forEach((l) => l());
  }
}

export const GlossContext = createContext<GlossRegistry | null>(null);

/** Starts a new "first mention" scope: the page, or a dialog over it. */
export function GlossScope({ children }: { children: ReactNode }) {
  const [registry] = useState(() => new GlossRegistry());
  return <GlossContext.Provider value={registry}>{children}</GlossContext.Provider>;
}
