import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import RescueDeals from './RescueDeals';
import { reserveSurplusOffer } from '../../services/api';
import { getRescueDeals } from '../../services/rescueApi';

jest.mock('../../services/api', () => ({
  reserveSurplusOffer: jest.fn(),
}));

jest.mock('../../services/rescueApi', () => ({
  ...jest.requireActual('../../services/rescueApi'),
  getRescueDeals: jest.fn(),
}));

const hour = 60 * 60 * 1000;

const deal = (overrides = {}) => ({
  alertId: 1,
  reason: 'you ordered this twice · 50% off · 25 min left',
  score: 0.6,
  alertedAt: new Date().toISOString(),
  reserved: false,
  offer: {
    id: 5,
    foodName: 'PASTA',
    ingredients: 'Pasta, eggs',
    allergens: ['WHEAT', 'EGGS'],
    calories: 500,
    originalPrice: 12,
    discountedPrice: 6,
    remainingPortions: 3,
    pickupLocation: 'Front desk',
    pickupStart: new Date(Date.now() - hour).toISOString(),
    pickupEnd: new Date(Date.now() + 2 * hour).toISOString(),
    expiresAt: new Date(Date.now() + hour).toISOString(),
  },
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <RescueDeals />
    </MemoryRouter>
  );

describe('RescueDeals', () => {
  beforeEach(() => jest.clearAllMocks());

  test('shows each deal with the reason it was picked', async () => {
    getRescueDeals.mockResolvedValue([deal()]);
    renderPage();

    expect(await screen.findByText('PASTA')).toBeInTheDocument();
    expect(screen.getByText('you ordered this twice · 50% off · 25 min left')).toBeInTheDocument();
    expect(screen.getByText('$12.00')).toBeInTheDocument();
    expect(screen.getByText('$6.00')).toBeInTheDocument();
    expect(screen.getByText(/WHEAT, EGGS/)).toBeInTheDocument();
  });

  test('explains the empty state', async () => {
    getRescueDeals.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/No rescue deals for you right now/)).toBeInTheDocument();
  });

  test('one tap reserves one portion at the earliest valid pickup time', async () => {
    getRescueDeals
      .mockResolvedValueOnce([deal()])
      .mockResolvedValueOnce([deal({ reserved: true })]);
    reserveSurplusOffer.mockResolvedValue({
      id: 42,
      totalPrice: 6,
      pickupAt: new Date(Date.now() + 2 * 60 * 1000).toISOString(),
    });
    renderPage();

    userEvent.click(await screen.findByText('Reserve 1 portion of PASTA'));

    await waitFor(() => expect(reserveSurplusOffer).toHaveBeenCalledTimes(1));
    const [offerId, body] = reserveSurplusOffer.mock.calls[0];
    expect(offerId).toBe(5);
    expect(body.quantity).toBe(1);
    expect(new Date(body.pickupAt).getTime()).toBeGreaterThan(Date.now());
    expect(await screen.findByText(/Reserved! Reservation #42/)).toBeInTheDocument();
    expect(await screen.findByText(/You reserved this/)).toBeInTheDocument();
  });

  test('shows the server message when a reservation fails', async () => {
    getRescueDeals.mockResolvedValue([deal()]);
    reserveSurplusOffer.mockRejectedValue(new Error('This offer has expired.'));
    renderPage();

    userEvent.click(await screen.findByText('Reserve 1 portion of PASTA'));
    expect(await screen.findByText('This offer has expired.')).toBeInTheDocument();
  });

  test('does not call the server when the pickup window has closed', async () => {
    const closed = deal();
    closed.offer.pickupStart = new Date(Date.now() - 3 * hour).toISOString();
    closed.offer.pickupEnd = new Date(Date.now() - hour).toISOString();
    getRescueDeals.mockResolvedValue([closed]);
    renderPage();

    userEvent.click(await screen.findByText('Reserve 1 portion of PASTA'));
    expect(await screen.findByText(/pickup window for this deal has closed/)).toBeInTheDocument();
    expect(reserveSurplusOffer).not.toHaveBeenCalled();
  });

  test('already reserved deals have no reserve button', async () => {
    getRescueDeals.mockResolvedValue([deal({ reserved: true })]);
    renderPage();
    expect(await screen.findByText(/You reserved this/)).toBeInTheDocument();
    expect(screen.queryByText('Reserve 1 portion of PASTA')).not.toBeInTheDocument();
  });

  test('shows a loading error', async () => {
    getRescueDeals.mockRejectedValue(new Error('The rescue request failed. Please try again.'));
    renderPage();
    expect(await screen.findByText(/rescue request failed/)).toBeInTheDocument();
  });
});
