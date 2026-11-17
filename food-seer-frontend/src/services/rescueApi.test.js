import {
  getRescueDeals,
  previewRescueMatches,
  sendRescueAlerts,
  earliestPickup,
} from './rescueApi';

describe('rescue API', () => {
  beforeEach(() => {
    localStorage.setItem('token', 'test-token');
    global.fetch = jest.fn().mockResolvedValue({ ok: true, json: async () => [] });
  });

  afterEach(() => {
    localStorage.clear();
    jest.restoreAllMocks();
    delete global.fetch;
  });

  test.each([
    [() => getRescueDeals(), '/deals', 'GET'],
    [() => previewRescueMatches(4), '/offers/4/matches', 'GET'],
    [() => sendRescueAlerts(4), '/offers/4/notify', 'POST'],
  ])('sends authenticated rescue request %#', async (call, path, method) => {
    await expect(call()).resolves.toEqual([]);
    expect(fetch).toHaveBeenCalledWith(`http://localhost:8080/api/rescue${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer test-token' },
    });
  });

  test('omits the auth header when signed out', async () => {
    localStorage.clear();
    await getRescueDeals();
    expect(fetch.mock.calls[0][1].headers).toEqual({ 'Content-Type': 'application/json' });
  });

  test('preserves server error messages', async () => {
    fetch.mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'Surplus offer not found.' }),
    });
    await expect(previewRescueMatches(9)).rejects.toThrow('Surplus offer not found.');
  });

  test('falls back to a generic message for non-JSON errors', async () => {
    fetch.mockResolvedValue({
      ok: false,
      json: async () => {
        throw new Error('HTML');
      },
    });
    await expect(getRescueDeals()).rejects.toThrow('Please try again.');
  });
});

describe('earliestPickup', () => {
  const now = new Date('2026-11-17T18:00:00Z');

  test('uses the window start when it is later than two minutes from now', () => {
    const offer = { pickupStart: '2026-11-17T19:00:00Z', pickupEnd: '2026-11-17T20:00:00Z' };
    expect(earliestPickup(offer, now)).toBe('2026-11-17T19:00:00.000Z');
  });

  test('uses two minutes from now once the window is open', () => {
    const offer = { pickupStart: '2026-11-17T17:00:00Z', pickupEnd: '2026-11-17T20:00:00Z' };
    expect(earliestPickup(offer, now)).toBe('2026-11-17T18:02:00.000Z');
  });

  test('falls back to the window end when it closes within two minutes', () => {
    const offer = { pickupStart: '2026-11-17T17:00:00Z', pickupEnd: '2026-11-17T18:01:00Z' };
    expect(earliestPickup(offer, now)).toBe('2026-11-17T18:01:00.000Z');
  });

  test('returns null once the window has closed', () => {
    const offer = { pickupStart: '2026-11-17T16:00:00Z', pickupEnd: '2026-11-17T17:00:00Z' };
    expect(earliestPickup(offer, now)).toBeNull();
  });
});
