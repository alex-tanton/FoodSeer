import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import SafetyNotices from '../SafetyNotices';
import { getMyNotifications, acknowledgeNotification } from '../../services/api';

jest.mock('../../services/api', () => ({
  getMyNotifications: jest.fn(),
  acknowledgeNotification: jest.fn(),
}));

const rescueNotice = {
  id: 7,
  incidentId: null,
  orderId: null,
  message:
    'Rescue deal: PASTA for $6.00 instead of $12.00 at Front desk. Why you: 50% off · 25 min left.',
  createdAt: '2026-11-17T18:00:00',
  acknowledged: false,
  acknowledgedAt: null,
  remedy: null,
  kind: 'RESCUE',
};

const safetyNotice = {
  id: 1,
  incidentId: 3,
  orderId: 8,
  message: 'Food safety notice: your order #8 (COOKIES) may have been exposed to PEANUT.',
  createdAt: '2026-10-12T12:00:00',
  acknowledged: false,
  acknowledgedAt: null,
  remedy: 'Full refund',
  kind: 'SAFETY',
};

const renderNotices = () =>
  render(
    <MemoryRouter initialEntries={['/notices']}>
      <Routes>
        <Route path="/notices" element={<SafetyNotices />} />
        <Route path="/rescue" element={<div>Rescue deals page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('Rescue deals in the notices list', () => {
  beforeEach(() => jest.clearAllMocks());

  test('labels a rescue deal without order or remedy text', async () => {
    getMyNotifications.mockResolvedValue([rescueNotice]);
    renderNotices();

    expect(await screen.findByText('♻️ Rescue deal')).toBeInTheDocument();
    expect(screen.getByText(/PASTA for \$6.00/)).toBeInTheDocument();
    expect(screen.queryByText(/will follow up with an offer/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Order #/)).not.toBeInTheDocument();
  });

  test('keeps safety notices unchanged next to rescue deals', async () => {
    getMyNotifications.mockResolvedValue([rescueNotice, safetyNotice]);
    renderNotices();

    expect(await screen.findByText('Order #8')).toBeInTheDocument();
    expect(screen.getByText('Full refund')).toBeInTheDocument();
    expect(screen.getByText('♻️ Rescue deal')).toBeInTheDocument();
  });

  test('opens the rescue deals page', async () => {
    getMyNotifications.mockResolvedValue([rescueNotice]);
    renderNotices();

    userEvent.click(await screen.findByText('See rescue deals'));
    expect(await screen.findByText('Rescue deals page')).toBeInTheDocument();
  });

  test('dismissing a deal acknowledges it', async () => {
    getMyNotifications
      .mockResolvedValueOnce([rescueNotice])
      .mockResolvedValueOnce([{ ...rescueNotice, acknowledged: true }]);
    acknowledgeNotification.mockResolvedValue({ ...rescueNotice, acknowledged: true });
    renderNotices();

    userEvent.click(await screen.findByText('Dismiss'));
    await waitFor(() => expect(acknowledgeNotification).toHaveBeenCalledWith(7));
    expect(await screen.findByText('✓ Read')).toBeInTheDocument();
  });
});
