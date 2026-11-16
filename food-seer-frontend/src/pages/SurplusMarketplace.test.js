import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import SurplusMarketplace from './SurplusMarketplace';
import * as api from '../services/api';

jest.mock('../services/api', () => ({
  getCurrentUser: jest.fn(),
  getAllFoods: jest.fn(),
  getSurplusOffers: jest.fn(),
  getSurplusReservations: jest.fn(),
  publishSurplusOffer: jest.fn(),
  reserveSurplusOffer: jest.fn(),
  cancelSurplusReservation: jest.fn(),
  collectSurplusReservation: jest.fn(),
}));

const now = new Date('2030-06-01T12:00:00Z').getTime();
const offer = {
  id: 1,
  foodName: 'SOUP',
  ingredients: 'Milk, vegetables',
  allergens: ['MILK'],
  calories: 300,
  originalPrice: 10,
  discountedPrice: 3.25,
  remainingPortions: 5,
  pickupLocation: 'Front desk',
  pickupStart: '2030-06-01T11:00:00Z',
  pickupEnd: '2030-06-01T15:00:00Z',
  expiresAt: '2030-06-01T14:00:00Z',
};
const reservation = {
  id: 7,
  offer,
  customerUsername: 'alice',
  quantity: 2,
  totalPrice: 6.5,
  pickupAt: '2030-06-01T13:00:00Z',
  status: 'RESERVED',
};

const change = (label, value) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } });

beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(Date, 'now').mockReturnValue(now);
  api.getCurrentUser.mockResolvedValue({ username: 'alice', role: 'ROLE_CUSTOMER' });
  api.getAllFoods.mockResolvedValue([{ id: 2, foodName: 'SOUP', price: 10, allergies: ['MILK'] }]);
  api.getSurplusOffers.mockResolvedValue([offer]);
  api.getSurplusReservations.mockResolvedValue([]);
});

afterEach(() => jest.restoreAllMocks());

test('customers can inspect discounts, ingredients, allergens, and pickup details', async () => {
  render(<SurplusMarketplace />);
  expect(await screen.findByText('SOUP')).toBeInTheDocument();
  expect(screen.getByText('$10.00')).toBeInTheDocument();
  expect(screen.getByText('$3.25 per portion')).toBeInTheDocument();
  expect(screen.getByText(/Milk, vegetables/)).toBeInTheDocument();
  expect(screen.getByText(/MILK/)).toBeInTheDocument();
  expect(screen.getByText(/Front desk/)).toBeInTheDocument();
  expect(screen.queryByText('Publish offer')).not.toBeInTheDocument();
  expect(api.getSurplusOffers).toHaveBeenCalledWith(false);
  expect(api.getAllFoods).not.toHaveBeenCalled();
});

test('shows an empty marketplace and reservation history', async () => {
  api.getSurplusOffers.mockResolvedValue([]);
  render(<SurplusMarketplace />);
  expect(await screen.findByText(/No surplus offers available/)).toBeInTheDocument();
  expect(screen.getByText('No reservations yet.')).toBeInTheDocument();
});

test('reserves quantities with a UTC pickup time and displays the server total', async () => {
  api.reserveSurplusOffer.mockResolvedValue(reservation);
  render(<SurplusMarketplace />);
  await screen.findByText('SOUP');
  change('Portions for SOUP', '2');
  change('Pickup time for SOUP', '2030-06-01T13:00');
  fireEvent.click(screen.getByRole('button', { name: 'Reserve SOUP' }));
  await waitFor(() =>
    expect(api.reserveSurplusOffer).toHaveBeenCalledWith(1, {
      quantity: 2,
      pickupAt: new Date('2030-06-01T13:00').toISOString(),
    })
  );
  expect(
    await screen.findByText('Reservation #7 confirmed. Pay $6.50 at pickup.')
  ).toBeInTheDocument();
  expect(api.getSurplusOffers).toHaveBeenCalledTimes(2);
});

test('conflicts show the server message and refresh stale availability', async () => {
  api.reserveSurplusOffer.mockRejectedValue(new Error('Sold out.'));
  api.getSurplusOffers.mockResolvedValueOnce([offer]).mockResolvedValue([]);
  render(<SurplusMarketplace />);
  await screen.findByText('SOUP');
  change('Pickup time for SOUP', '2030-06-01T13:00');
  fireEvent.click(screen.getByRole('button', { name: 'Reserve SOUP' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Sold out.');
  expect(await screen.findByText(/No surplus offers available/)).toBeInTheDocument();
  expect(screen.queryByText(/confirmed/)).not.toBeInTheDocument();
});

test('disables actions while a reservation request is pending', async () => {
  let complete;
  api.reserveSurplusOffer.mockReturnValue(
    new Promise((resolve) => {
      complete = resolve;
    })
  );
  render(<SurplusMarketplace />);
  await screen.findByText('SOUP');
  change('Pickup time for SOUP', '2030-06-01T13:00');
  const button = screen.getByRole('button', { name: 'Reserve SOUP' });
  fireEvent.click(button);
  await waitFor(() => expect(button).toBeDisabled());
  fireEvent.click(button);
  expect(api.reserveSurplusOffer).toHaveBeenCalledTimes(1);
  complete(reservation);
  expect(await screen.findByText(/Reservation #7 confirmed/)).toBeInTheDocument();
});

test('customers cancel reservations and see the refreshed status', async () => {
  api.getSurplusOffers.mockResolvedValue([]);
  api.getSurplusReservations
    .mockResolvedValueOnce([reservation])
    .mockResolvedValue([{ ...reservation, status: 'CANCELLED' }]);
  api.cancelSurplusReservation.mockResolvedValue({});
  render(<SurplusMarketplace />);
  fireEvent.click(await screen.findByRole('button', { name: 'Cancel reservation #7' }));
  await waitFor(() => expect(api.cancelSurplusReservation).toHaveBeenCalledWith(7));
  expect(await screen.findByText(/CANCELLED/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Cancel reservation #7' })).not.toBeInTheDocument();
});

test('cancellation is disabled after the selected pickup time', async () => {
  api.getSurplusReservations.mockResolvedValue([
    { ...reservation, pickupAt: '2030-06-01T11:00:00Z' },
  ]);
  render(<SurplusMarketplace />);
  expect(await screen.findByRole('button', { name: 'Cancel reservation #7' })).toBeDisabled();
});

test.each(['ROLE_STAFF', 'ROLE_ADMIN'])('staff publish discounted offers (%s)', async (role) => {
  api.getCurrentUser.mockResolvedValue({ role });
  api.getSurplusOffers.mockResolvedValue([]);
  api.publishSurplusOffer.mockResolvedValue(offer);
  render(<SurplusMarketplace />);
  await screen.findByText('Publish a surplus offer');
  change('Menu item', '2');
  change('Ingredients', 'Milk and vegetables');
  change('Discount price per portion ($)', '3.25');
  change('Surplus portions', '5');
  change('Pickup location', 'Desk');
  change('Pickup window starts', '2030-06-01T13:00');
  change('Pickup window ends', '2030-06-01T15:00');
  change('Booking deadline', '2030-06-01T14:00');
  fireEvent.click(screen.getByRole('button', { name: 'Publish offer' }));
  await waitFor(() =>
    expect(api.publishSurplusOffer).toHaveBeenCalledWith({
      foodId: 2,
      ingredients: 'Milk and vegetables',
      discountedPrice: 3.25,
      portions: 5,
      pickupLocation: 'Desk',
      pickupStart: new Date('2030-06-01T13:00').toISOString(),
      pickupEnd: new Date('2030-06-01T15:00').toISOString(),
      expiresAt: new Date('2030-06-01T14:00').toISOString(),
    })
  );
  expect(await screen.findByText('Surplus offer published.')).toBeInTheDocument();
  await waitFor(() => expect(screen.getByLabelText('Menu item')).toHaveValue(''));
  expect(api.getSurplusOffers).toHaveBeenCalledWith(true);
});

test('staff mark a reservation collected', async () => {
  api.getCurrentUser.mockResolvedValue({ role: 'ROLE_STAFF' });
  api.getSurplusReservations
    .mockResolvedValueOnce([reservation])
    .mockResolvedValue([{ ...reservation, status: 'COLLECTED' }]);
  api.collectSurplusReservation.mockResolvedValue({});
  render(<SurplusMarketplace />);
  fireEvent.click(await screen.findByRole('button', { name: 'Mark #7 collected' }));
  await waitFor(() => expect(api.collectSurplusReservation).toHaveBeenCalledWith(7));
  expect(await screen.findByText(/COLLECTED/)).toBeInTheDocument();
  expect(screen.getByText('Customer: alice')).toBeInTheDocument();
  expect(screen.queryByText('Reserve SOUP')).not.toBeInTheDocument();
});

test('staff cannot collect before the pickup window', async () => {
  api.getCurrentUser.mockResolvedValue({ role: 'ROLE_STAFF' });
  api.getSurplusReservations.mockResolvedValue([
    {
      ...reservation,
      offer: { ...offer, pickupStart: '2030-06-01T13:00:00Z' },
    },
  ]);
  render(<SurplusMarketplace />);
  expect(await screen.findByRole('button', { name: 'Mark #7 collected' })).toBeDisabled();
});

test('loading errors can be retried', async () => {
  api.getCurrentUser.mockRejectedValueOnce(new Error('Connection failed'));
  render(<SurplusMarketplace />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Connection failed');
  fireEvent.click(screen.getByRole('button', { name: 'Refresh marketplace' }));
  expect(await screen.findByText('SOUP')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('drivers see an access message without requesting marketplace data', async () => {
  api.getCurrentUser.mockResolvedValue({ role: 'ROLE_DRIVER' });
  render(<SurplusMarketplace />);
  expect(await screen.findByText(/available to customers, staff, and admins/)).toBeInTheDocument();
  expect(api.getSurplusOffers).not.toHaveBeenCalled();
});

test.each([
  { ...offer, expiresAt: '2030-06-01T11:00:00Z' },
  { ...offer, remainingPortions: 0 },
])('closed offers cannot be reserved', async (closed) => {
  api.getSurplusOffers.mockResolvedValue([closed]);
  render(<SurplusMarketplace />);
  expect(await screen.findByText(/Closed or sold out/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Reserve SOUP' })).not.toBeInTheDocument();
});

test('retains successful reservation confirmation when the subsequent refresh fails', async () => {
  api.reserveSurplusOffer.mockResolvedValue(reservation);
  api.getSurplusOffers.mockResolvedValueOnce([offer]).mockRejectedValue(new Error('Offline'));
  render(<SurplusMarketplace />);
  await screen.findByText('SOUP');
  change('Pickup time for SOUP', '2030-06-01T13:00');
  fireEvent.click(screen.getByRole('button', { name: 'Reserve SOUP' }));
  expect(await screen.findByText(/Reservation #7 confirmed/)).toBeInTheDocument();
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Could not refresh the marketplace: Offline'
  );
});
