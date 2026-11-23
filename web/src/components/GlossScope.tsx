import { createContext, useEffect, useState, type ReactNode } from 'react';

/**
 * Which occurrence of each insurance term on screen carries the "?". Every `<Term>` registers its element under its
 * explanation; the first one in document order shows the mark and the rest stay plain (still tappable, with the same
 * explanation). A whole page shares one scope, and a dialog opens its own, since the page behind it is inert.
 *
 * A shown mention ignores copies under `display: none` (the ledger's desktop table on a phone, its phone list on a
 * desktop), and the scope re-checks when the window is resized, since that switches which layout is shown.
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

  /**
   * True when `el` is the first mention of `key`. A shown mention ignores hidden ones, so it isn't robbed by a copy
   * under `display: none`. A hidden mention just goes by document order: its mark can't be seen, and a whole hidden
   * group (a closed `<dialog>`, which has its own scope) keeps the right answer when it opens, with no re-check needed.
   */
  isFirst(key: string, el: HTMLElement): boolean {
    const set = this.terms.get(key);
    if (!set) return true;
    const elShown = shown(el);
    for (const other of set) {
      if (other === el || (elShown && !shown(other))) continue;
      if (other.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING) return false;
    }
    return true;
  }

  /** Re-checks every mention, e.g. after a resize switched which layout is shown. */
  refresh(): void {
    this.emit();
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(): void {
    this.listeners.forEach((l) => l());
  }
}

/** Laid out on screen: an element under `display: none` has no boxes. */
function shown(el: HTMLElement): boolean {
  return el.getClientRects().length > 0;
}

/** A registry whose mentions never take the mark: for copies a sighted user doesn't see (see `GlossSilent`). */
export class SilentRegistry extends GlossRegistry {
  override isFirst(_key: string, _el: HTMLElement): boolean {
    return false;
  }
}

export const GlossContext = createContext<GlossRegistry | null>(null);

/** Starts a new "first mention" scope: the page, or a dialog over it. */
export function GlossScope({ children }: { children: ReactNode }) {
  const [registry] = useState(() => new GlossRegistry());
  useEffect(() => {
    const refresh = () => registry.refresh();
    window.addEventListener('resize', refresh);
    return () => window.removeEventListener('resize', refresh);
  }, [registry]);
  return <GlossContext.Provider value={registry}>{children}</GlossContext.Provider>;
}

/**
 * Terms inside never carry the "?" and are not a keyboard stop: for screen-reader-only copies of text that is also shown
 * (the timeline's sr-only list), which still have a 1px box and would otherwise take the mark from the visible copy.
 */
export function GlossSilent({ children }: { children: ReactNode }) {
  const [registry] = useState(() => new SilentRegistry());
  return <GlossContext.Provider value={registry}>{children}</GlossContext.Provider>;
}
