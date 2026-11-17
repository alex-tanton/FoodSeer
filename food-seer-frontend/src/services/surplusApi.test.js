import {
  getSurplusOffers,
  publishSurplusOffer,
  reserveSurplusOffer,
  getSurplusReservations,
  cancelSurplusReservation,
  collectSurplusReservation,
} from './api';

describe('surplus API', () => {
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
    [() => getSurplusOffers(), '/offers', 'GET', undefined],
    [() => getSurplusOffers(true), '/offers/manage', 'GET', undefined],
    [() => publishSurplusOffer({ foodId: 1 }), '/offers', 'POST', { foodId: 1 }],
    [
      () => reserveSurplusOffer(2, { quantity: 3 }),
      '/offers/2/reservations',
      'POST',
      { quantity: 3 },
    ],
    [() => getSurplusReservations(), '/reservations', 'GET', undefined],
    [() => cancelSurplusReservation(3), '/reservations/3/cancel', 'POST', undefined],
    [() => collectSurplusReservation(3), '/reservations/3/collect', 'POST', undefined],
  ])('sends authenticated marketplace request %#', async (call, path, method, body) => {
    await expect(call()).resolves.toEqual([]);
    expect(fetch).toHaveBeenCalledWith(`http://localhost:8080/api/surplus${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer test-token' },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  });

  test('preserves server conflict messages', async () => {
    fetch.mockResolvedValue({ ok: false, json: async () => ({ message: 'Sold out.' }) });
    await expect(reserveSurplusOffer(1, { quantity: 1 })).rejects.toThrow('Sold out.');
  });

  test('handles non-JSON error responses', async () => {
    fetch.mockResolvedValue({
      ok: false,
      json: async () => {
        throw new Error('HTML');
      },
    });
    await expect(getSurplusOffers()).rejects.toThrow('Please try again.');
  });
});
