// Client for driver batch dispatch endpoints (/api/driver/batches).
const API_BASE_URL = 'http://localhost:8080';

const batchRequest = async (path, method = 'GET', body) => {
  const token = localStorage.getItem('token');
  const response = await fetch(`${API_BASE_URL}/api/driver/batches${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });

  if (!response.ok) {
    const details = await response.json().catch(() => ({}));
    throw new Error(details.message || 'The driver batch request failed. Please try again.');
  }
  return response.json();
};

export const getSuggestedBatches = () => batchRequest('/suggestions');

export const claimBatch = (batchId) => batchRequest(`/${batchId}/claim`, 'POST');

export const advanceBatchStep = (batchId) => batchRequest(`/${batchId}/step`, 'POST');

export const getActiveBatch = () => batchRequest('/active');

export const getBatchById = (batchId) => batchRequest(`/${batchId}`);
