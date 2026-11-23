import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { PublicCoverage, SignedInUser, SignInRequest, SignUpRequest } from './types';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`API answered ${status}`);
  }
}

/** JSON fetch against the same-origin API; the session rides along in its httpOnly cookie. */
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, body);
  return body as T;
}

/** The signed-in person, or null when nobody is signed in (401). Any other failure is a real error. */
export async function fetchSession(): Promise<SignedInUser | null> {
  try {
    return await api<SignedInUser>('/api/auth/session');
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  }
}

export function useSession() {
  return useQuery({ queryKey: ['session'], queryFn: fetchSession, staleTime: Infinity });
}

/** The covers we offer and their annual premiums. Public: works with no session (the landing page). */
export function usePublicCoverages() {
  return useQuery({
    queryKey: ['public', 'coverages'],
    queryFn: () => api<PublicCoverage[]>('/api/public/coverages'),
    staleTime: Infinity,
    retry: 1,
  });
}

export function signIn(body: SignInRequest): Promise<SignedInUser> {
  return api<SignedInUser>('/api/auth/signin', { method: 'POST', body: JSON.stringify(body) });
}

export function signUp(body: SignUpRequest): Promise<SignedInUser> {
  return api<SignedInUser>('/api/auth/signup', { method: 'POST', body: JSON.stringify(body) });
}

export function signOut(): Promise<null> {
  return api<null>('/api/auth/session', { method: 'DELETE' });
}

/**
 * Ends the session on the server, then forgets everything cached for that person, so the next
 * person on this browser never sees it. The caller decides where to go next.
 */
export function useSignOut() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: signOut,
    onSettled: () => {
      queryClient.clear();
      queryClient.setQueryData(['session'], null);
    },
  });
}
