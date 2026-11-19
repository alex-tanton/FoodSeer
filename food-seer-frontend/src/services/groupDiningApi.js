// Client for group dining sessions (/api/group).
const API_BASE_URL = 'http://localhost:8080';

const groupRequest = async (path, method = 'GET', body) => {
  const token = localStorage.getItem('token');
  const response = await fetch(`${API_BASE_URL}/api/group${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });

  if (!response.ok) {
    const details = await response.json().catch(() => ({}));
    throw new Error(details.message || 'The group dining request failed. Please try again.');
  }
  return response.json();
};

export const createGroupSession = (sessionName) =>
  groupRequest('/sessions', 'POST', { sessionName });

export const joinGroupSession = (code, profile) =>
  groupRequest(`/sessions/${code}/join`, 'POST', profile);

export const getGroupSession = (code) =>
  groupRequest(`/sessions/${code}`);

export const getHarmonizedMenu = (code) =>
  groupRequest(`/sessions/${code}/menu`);

export const addCartItem = (code, item) =>
  groupRequest(`/sessions/${code}/items`, 'POST', item);

export const removeCartItem = (code, itemId) =>
  groupRequest(`/sessions/${code}/items/${itemId}`, 'DELETE');

export const getSplitBill = (code) =>
  groupRequest(`/sessions/${code}/split-bill`);

export const checkoutGroupSession = (code) =>
  groupRequest(`/sessions/${code}/checkout`, 'POST');
