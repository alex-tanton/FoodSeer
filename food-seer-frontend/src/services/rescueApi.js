// Client for surplus rescue matching (/api/rescue). Kept apart from api.js so
// the rescue feature stays in its own files; reservations reuse the
// marketplace's reserveSurplusOffer from api.js.
const API_BASE_URL = 'http://localhost:8080';

const rescueRequest = async (path, method = 'GET') => {
  const token = localStorage.getItem('token');
  const response = await fetch(`${API_BASE_URL}/api/rescue${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!response.ok) {
    const details = await response.json().catch(() => ({}));
    throw new Error(details.message || 'The rescue request failed. Please try again.');
  }
  return response.json();
};

export const getRescueDeals = () => rescueRequest('/deals');
export const previewRescueMatches = (offerId) => rescueRequest(`/offers/${offerId}/matches`);
export const sendRescueAlerts = (offerId) => rescueRequest(`/offers/${offerId}/notify`, 'POST');
export const getRescueStats = () => rescueRequest('/stats');

// Earliest pickup the marketplace accepts: in the future, inside the pickup
// window. Returns null when the window has already closed.
export const earliestPickup = (offer, now = new Date()) => {
  const start = new Date(offer.pickupStart);
  const end = new Date(offer.pickupEnd);
  const soon = new Date(now.getTime() + 2 * 60 * 1000);
  const pickup = soon > start ? soon : start;
  if (pickup <= end) {
    return pickup.toISOString();
  }
  return end > now ? end.toISOString() : null;
};
