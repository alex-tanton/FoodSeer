import { createMealPlan, getMealPlan, swapMeal, orderMeal, deleteMealPlan } from './mealPlanApi';

const BASE = 'http://localhost:8080/api/meal-plans';
const headers = { 'Content-Type': 'application/json', Authorization: 'Bearer t' };

describe('meal plan API', () => {
  beforeEach(() => {
    localStorage.setItem('token', 't');
    global.fetch = jest
      .fn()
      .mockResolvedValue({ ok: true, status: 200, json: async () => ({ id: 1 }) });
  });

  afterEach(() => {
    localStorage.clear();
    delete global.fetch;
  });

  test('creates a plan with cleaned fields', async () => {
    await createMealPlan({ days: 5, weeklyBudget: '150', request: '  skip lunch ', useAi: true });
    expect(fetch).toHaveBeenCalledWith(BASE, {
      method: 'POST',
      headers,
      body: JSON.stringify({ days: 5, weeklyBudget: 150, request: 'skip lunch', useAi: true }),
    });
    await createMealPlan();
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({
      days: 7,
      weeklyBudget: null,
      request: null,
      useAi: false,
    });
  });

  test('gets the current plan, and null when there is none', async () => {
    await expect(getMealPlan()).resolves.toEqual({ id: 1 });
    expect(fetch).toHaveBeenCalledWith(`${BASE}/current`, { method: 'GET', headers });
    fetch.mockResolvedValue({ ok: false, status: 404, json: async () => ({ message: 'none' }) });
    await expect(getMealPlan()).resolves.toBeNull();
    fetch.mockResolvedValue({ ok: false, status: 500, json: async () => ({}) });
    await expect(getMealPlan()).rejects.toThrow('The meal plan request failed');
  });

  test.each([
    [swapMeal, '/current/swap'],
    [orderMeal, '/current/order'],
  ])('posts the day and meal %#', async (call, path) => {
    await call('2026-11-18', 'LUNCH');
    expect(fetch).toHaveBeenCalledWith(`${BASE}${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ date: '2026-11-18', meal: 'LUNCH' }),
    });
  });

  test('delete returns null on 204 and surfaces server messages', async () => {
    fetch.mockResolvedValue({ ok: true, status: 204 });
    await expect(deleteMealPlan()).resolves.toBeNull();
    fetch.mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({ message: 'already ordered' }),
    });
    await expect(orderMeal('2026-11-18', 'LUNCH')).rejects.toThrow('already ordered');
  });
});
