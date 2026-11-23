import {
  createGroupSession,
  joinGroupSession,
  getGroupSession,
  getHarmonizedMenu,
  addCartItem,
  removeCartItem,
  getSplitBill,
  checkoutGroupSession,
} from './groupDiningApi';

describe('groupDiningApi', () => {
  beforeEach(() => {
    localStorage.setItem('token', 'test-token');
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ success: true }),
    });
  });

  afterEach(() => {
    localStorage.clear();
    jest.restoreAllMocks();
    delete global.fetch;
  });

  test.each([
    [() => createGroupSession('Lunch Room'), '/sessions', 'POST'],
    [() => joinGroupSession('CODE26', { displayName: 'Leo' }), '/sessions/CODE26/join', 'POST'],
    [() => getGroupSession('CODE26'), '/sessions/CODE26', 'GET'],
    [() => getHarmonizedMenu('CODE26'), '/sessions/CODE26/menu', 'GET'],
    [() => addCartItem('CODE26', { foodId: 10, quantity: 1 }), '/sessions/CODE26/items', 'POST'],
    [() => removeCartItem('CODE26', 42), '/sessions/CODE26/items/42', 'DELETE'],
    [() => getSplitBill('CODE26'), '/sessions/CODE26/split-bill', 'GET'],
    [() => checkoutGroupSession('CODE26'), '/sessions/CODE26/checkout', 'POST'],
  ])('sends authenticated group dining request %#', async (call, path, method) => {
    await call();
    expect(fetch).toHaveBeenCalledWith(
      `http://localhost:8080/api/group${path}`,
      expect.objectContaining({
        method,
        headers: expect.objectContaining({
          Authorization: 'Bearer test-token',
        }),
      })
    );
  });

  test('preserves backend error message', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'Session is not active' }),
    });

    await expect(getGroupSession('EXPIRED')).rejects.toThrow('Session is not active');
  });
});
