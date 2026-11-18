import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import RescueManage from './RescueManage';
import { getRescueStats, previewRescueMatches, sendRescueAlerts } from '../../services/rescueApi';

jest.mock('../../services/rescueApi', () => ({
  getRescueStats: jest.fn(),
  previewRescueMatches: jest.fn(),
  sendRescueAlerts: jest.fn(),
}));

const stat = (overrides = {}) => ({
  offerId: 5,
  foodName: 'PASTA',
  expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
  remainingPortions: 3,
  alerted: 4,
  alertedWhoReserved: 2,
  portionsRescued: 3,
  portionsCollected: 1,
  revenueRecovered: 18,
  customerSavings: 18,
  ...overrides,
});

const preview = {
  offerId: 5,
  foodName: 'PASTA',
  alertBudget: 1,
  ranked: [
    { username: 'ann', score: 0.62, reason: 'you ordered this twice · 50% off', willAlert: true },
    { username: 'bob', score: 0.1, reason: '50% off', willAlert: false },
  ],
  excluded: [
    {
      username: 'eggy',
      reason: 'ALLERGEN',
      description: 'Conflicts with saved dietary restrictions',
    },
  ],
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <RescueManage />
    </MemoryRouter>
  );

describe('RescueManage', () => {
  beforeEach(() => jest.clearAllMocks());

  test('shows totals across offers', async () => {
    getRescueStats.mockResolvedValue([stat(), stat({ offerId: 6, foodName: 'SOUP' })]);
    renderPage();

    const totals = await screen.findByLabelText('Rescue totals');
    expect(within(totals).getByText('6')).toBeInTheDocument();
    expect(within(totals).getAllByText('$36.00')).toHaveLength(2);
    expect(within(totals).getByText('8')).toBeInTheDocument();
  });

  test('previews who would be alerted and who was excluded and why', async () => {
    getRescueStats.mockResolvedValue([stat()]);
    previewRescueMatches.mockResolvedValue(preview);
    renderPage();

    userEvent.click(await screen.findByText('Preview matches for PASTA'));

    expect(await screen.findByText('Would alert 1 of 2 eligible customers')).toBeInTheDocument();
    expect(screen.getByText('you ordered this twice · 50% off')).toBeInTheDocument();
    expect(screen.getByText('62%')).toBeInTheDocument();
    expect(screen.getByText('Over budget')).toBeInTheDocument();
    expect(screen.getByText('Conflicts with saved dietary restrictions')).toBeInTheDocument();
    expect(previewRescueMatches).toHaveBeenCalledWith(5);
  });

  test('sending alerts reports who was alerted and reloads stats', async () => {
    getRescueStats.mockResolvedValueOnce([stat({ alerted: 0 })]).mockResolvedValueOnce([stat()]);
    sendRescueAlerts.mockResolvedValue({ offerId: 5, alertBudget: 9, alerted: ['ann', 'bob'] });
    renderPage();

    userEvent.click(await screen.findByText('Send rescue alerts for PASTA'));

    expect(await screen.findByText('Alerted 2 customers: ann, bob.')).toBeInTheDocument();
    await waitFor(() => expect(getRescueStats).toHaveBeenCalledTimes(2));
  });

  test('says when nobody new can be alerted', async () => {
    getRescueStats.mockResolvedValue([stat()]);
    sendRescueAlerts.mockResolvedValue({ offerId: 5, alertBudget: 9, alerted: [] });
    renderPage();

    userEvent.click(await screen.findByText('Send rescue alerts for PASTA'));
    expect(await screen.findByText('Nobody new to alert for this offer.')).toBeInTheDocument();
  });

  test('closed offers show results but no actions', async () => {
    getRescueStats.mockResolvedValue([stat({ remainingPortions: 0 })]);
    renderPage();

    expect(await screen.findByText(/Closed or sold out/)).toBeInTheDocument();
    expect(screen.queryByText('Send rescue alerts for PASTA')).not.toBeInTheDocument();
  });

  test('shows server errors', async () => {
    getRescueStats.mockResolvedValue([stat()]);
    previewRescueMatches.mockRejectedValue(new Error('Surplus offer not found.'));
    renderPage();

    userEvent.click(await screen.findByText('Preview matches for PASTA'));
    expect(await screen.findByText('Surplus offer not found.')).toBeInTheDocument();
  });

  test('points staff to publishing when there are no offers', async () => {
    getRescueStats.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/No surplus offers yet/)).toBeInTheDocument();
  });
});
