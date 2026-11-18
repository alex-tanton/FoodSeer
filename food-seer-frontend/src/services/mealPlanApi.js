// Client for the weekly meal planner (/api/meal-plans). Planning, safety checks,
// the budget and the optional AI request reader all run on the server.
const API_BASE_URL = 'http://localhost:8080';

const planRequest = async (path, method = 'GET', body) => {
  const token = localStorage.getItem('token');
  const response = await fetch(`${API_BASE_URL}/api/meal-plans${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (response.status === 204) {
    return null;
  }
  if (!response.ok) {
    const details = await response.json().catch(() => ({}));
    const error = new Error(details.message || 'The meal plan request failed. Please try again.');
    error.status = response.status;
    throw error;
  }
  return response.json();
};

/**
 * Builds and saves a new plan, replacing the current one.
 *
 * @param {object} options
 * @param {number} [options.days] 1 to 7
 * @param {number} [options.weeklyBudget] dollars; blank for no limit
 * @param {string} [options.request] e.g. "skip breakfast, pizza on Friday"
 * @param {boolean} [options.useAi] let the local LLM read the request
 */
export const createMealPlan = ({ days = 7, weeklyBudget, request, useAi = false } = {}) =>
  planRequest('', 'POST', {
    days,
    weeklyBudget: weeklyBudget ? Number(weeklyBudget) : null,
    request: request && request.trim() ? request.trim() : null,
    useAi,
  });

/** The current plan, or null when the customer has none yet. */
export const getMealPlan = async () => {
  try {
    return await planRequest('/current');
  } catch (error) {
    if (error.status === 404) {
      return null;
    }
    throw error;
  }
};

export const swapMeal = (date, meal) => planRequest('/current/swap', 'POST', { date, meal });

export const orderMeal = (date, meal) => planRequest('/current/order', 'POST', { date, meal });

export const deleteMealPlan = () => planRequest('/current', 'DELETE');
