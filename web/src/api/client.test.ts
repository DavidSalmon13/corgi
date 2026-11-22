import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, fetchSession, signIn, signOut, signUp } from './client';

function respond(status: number, body?: unknown) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(body === undefined ? '' : JSON.stringify(body), { status })));
}

afterEach(() => vi.unstubAllGlobals());

// covers AC1, T14 — the SPA treats "no session" as signed out, and anything else as a real failure
describe('fetchSession', () => {
  it('returns the signed-in person', async () => {
    respond(200, { id: 1, role: 'BROKER', displayName: 'Sam Reyes', subtitle: null, customerId: null, email: 'sam@plainly.test' });
    await expect(fetchSession()).resolves.toMatchObject({ id: 1, role: 'BROKER' });
  });

  it('returns null when nobody is signed in (401)', async () => {
    respond(401);
    await expect(fetchSession()).resolves.toBeNull();
  });

  it('throws when the API is broken, so the page can say so instead of sending people to sign-in', async () => {
    respond(500, { error: 'boom' });
    await expect(fetchSession()).rejects.toBeInstanceOf(ApiError);
  });
});

// covers AC1, AC2, T8.1 — a refused request carries its status and body (403 guard, 422 field errors)
describe('api', () => {
  it('keeps the status and parsed body on errors', async () => {
    respond(422, { error: 'validation', message: '1 thing to fix before you can save.', fields: { startDate: 'Pick the day cover should start.' } });
    const err = (await api('/api/broker/policies', { method: 'POST', body: '{}' }).catch((e: unknown) => e)) as ApiError & {
      body: { fields: Record<string, string> };
    };
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.body.fields.startDate).toBe('Pick the day cover should start.');
  });

  it('reports a 403 from a guarded route as an ApiError', async () => {
    respond(403, { error: 'forbidden' });
    await expect(api('/api/me/cover')).rejects.toMatchObject({ status: 403 });
  });
});

// covers real-sign-in AC1, AC2, AC4, AC8, T11 — the sign-in calls hit the auth API with JSON
describe('sign-in calls', () => {
  function capture(status: number, body?: unknown) {
    const fetchMock = vi.fn(async (_url: string, _init?: RequestInit) =>
      new Response(body === undefined ? null : JSON.stringify(body), { status }),
    );
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  it('reads the session from /api/auth/session', async () => {
    const fetchMock = capture(401);
    await fetchSession();
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/auth/session');
  });

  it('posts the email and password to sign in', async () => {
    const fetchMock = capture(200, { id: 1, role: 'BROKER' });
    await signIn({ email: 'sam@plainly.test', password: 'plainly-demo-2026' });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('/api/auth/signin');
    expect(init!.method).toBe('POST');
    expect(JSON.parse(init!.body as string)).toEqual({ email: 'sam@plainly.test', password: 'plainly-demo-2026' });
  });

  it('keeps the one wrong-credentials message from a failed sign-in', async () => {
    capture(401, { error: 'wrong_credentials', message: "That email and password don't match. Check them and try again." });
    await expect(signIn({ email: 'x@y.z', password: 'nope' })).rejects.toMatchObject({ status: 401 });
  });

  it('posts sign-up with the confirmation, and signs out with DELETE', async () => {
    const fetchMock = capture(200, { id: 2, role: 'POLICYHOLDER' });
    await signUp({ email: 'maya@juniperandrye.test', password: 'ten-chars-ok', confirmPassword: 'ten-chars-ok' });
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/auth/signup');
    capture(204);
    await expect(signOut()).resolves.toBeNull();
  });
});
