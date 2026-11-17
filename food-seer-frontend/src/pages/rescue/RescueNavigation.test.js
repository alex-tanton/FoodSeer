import React from 'react';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { MemoryRouter } from 'react-router-dom';
import Navigation from '../../components/Navigation';
import { getCurrentUser, getMyNotifications } from '../../services/api';

jest.mock('../../services/api', () => ({
  logout: jest.fn(),
  getCurrentUser: jest.fn(),
  getMyNotifications: jest.fn(),
}));

const notice = (id, kind, acknowledged = false) => ({ id, kind, acknowledged, message: 'm' });

const renderNav = () =>
  render(
    <MemoryRouter initialEntries={['/orders']}>
      <Navigation />
    </MemoryRouter>
  );

describe('Navigation with rescue deals', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getCurrentUser.mockResolvedValue({ username: 'ann', role: 'ROLE_CUSTOMER' });
  });

  test('an unread rescue deal does not raise the food safety banner', async () => {
    getMyNotifications.mockResolvedValue([notice(1, 'RESCUE')]);
    renderNav();

    expect(await screen.findByText('♻️ Rescue Deals (1)')).toBeInTheDocument();
    expect(screen.getByText('🔔 Safety Notices')).toBeInTheDocument();
    expect(screen.queryByText(/unread food safety/)).not.toBeInTheDocument();
  });

  test('safety notices still raise the banner and badge', async () => {
    getMyNotifications.mockResolvedValue([
      notice(1, 'SAFETY'),
      notice(2, undefined),
      notice(3, 'RESCUE', true),
    ]);
    renderNav();

    expect(await screen.findByText('🔔 Safety Notices (2)')).toBeInTheDocument();
    expect(screen.getByText(/You have 2 unread food safety/)).toBeInTheDocument();
    expect(screen.getByText('♻️ Rescue Deals')).toBeInTheDocument();
  });

  test('staff do not load customer notices', async () => {
    getCurrentUser.mockResolvedValue({ username: 'sam', role: 'ROLE_STAFF' });
    renderNav();

    expect(await screen.findByText('STAFF')).toBeInTheDocument();
    expect(getMyNotifications).not.toHaveBeenCalled();
    expect(screen.queryByText(/Rescue Deals/)).not.toBeInTheDocument();
  });
});
