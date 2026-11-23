import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import MealPlan from './MealPlan';
import {
  createMealPlan,
  getMealPlan,
  swapMeal,
  orderMeal,
  deleteMealPlan,
} from '../../services/mealPlanApi';

jest.mock('../../services/mealPlanApi', () => ({
  createMealPlan: jest.fn(),
  getMealPlan: jest.fn(),
  swapMeal: jest.fn(),
  orderMeal: jest.fn(),
  deleteMealPlan: jest.fn(),
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

const iso = (offset) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

const dish = (id, name, calories, price) => ({ id, name, calories, price, allergies: [] });

const day = (offset, overrides = {}) => ({
  date: iso(offset),
  dayName: offset === 0 ? 'Today' : `Day ${offset}`,
  target: 2000,
  plannedCalories: 1950,
  plannedCost: 40,
  onTarget: true,
  eatenCalories: offset <= 0 ? 300 : null,
  meals: [
    {
      meal: 'LUNCH',
      target: 875,
      dishes: [dish(1, 'BURRITO', 650, 11), dish(2, 'FRUIT SALAD', 130, 8)],
      calories: 780,
      cost: 19,
      ordered: false,
      orderId: null,
      orderable: offset === 0,
    },
    {
      meal: 'DINNER',
      target: 1000,
      dishes: [dish(3, 'STEAK', 679, 28)],
      calories: 679,
      cost: 28,
      ordered: offset < 0,
      orderId: offset < 0 ? 9 : null,
      orderable: false,
    },
  ],
  ...overrides,
});

const plan = (overrides = {}) => ({
  id: 1,
  startDate: iso(0),
  dailyGoal: 1700,
  dailyBurn: 300,
  dailyTarget: 2000,
  weeklyBudget: 150,
  totalCost: 141,
  budgetMet: true,
  daysOnTarget: 1,
  request: 'skip breakfast',
  understood: 'no breakfast',
  aiStatus: 'USED',
  warnings: [],
  days: [day(0), day(1, { onTarget: false, plannedCalories: 1500 })],
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <MealPlan />
    </MemoryRouter>
  );

beforeEach(() => {
  jest.clearAllMocks();
});

test('with no plan, the form builds one from days, budget, request and AI flag', async () => {
  getMealPlan.mockResolvedValue(null);
  createMealPlan.mockResolvedValue(plan());
  renderPage();
  await screen.findByText('Plan your week');
  await userEvent.selectOptions(screen.getByLabelText('Days to plan'), '5');
  await userEvent.type(screen.getByPlaceholderText('No limit'), '150');
  await userEvent.type(screen.getByLabelText('Plan request'), 'skip breakfast');
  await userEvent.click(screen.getByText('Build my plan'));
  await waitFor(() =>
    expect(createMealPlan).toHaveBeenCalledWith({
      days: 5,
      weeklyBudget: '150',
      request: 'skip breakfast',
      useAi: true,
    })
  );
  expect(await screen.findAllByTestId('plan-day')).toHaveLength(2);
  expect(screen.getByText('2000 kcal')).toBeInTheDocument();
  expect(screen.getByText(/1700 goal \+ 300 burned/)).toBeInTheDocument();
  expect(screen.getByText('of your $150 limit')).toBeInTheDocument();
  expect(screen.getByText(/Your request: no breakfast/)).toHaveTextContent(
    'Read by the AI assistant'
  );
});

test('shows meals, totals, eaten calories and only the right buttons', async () => {
  getMealPlan.mockResolvedValue(
    plan({
      days: [day(-1), day(0), day(1)],
      warnings: ['Thursday is 15% under your calorie target.'],
    })
  );
  renderPage();
  const days = await screen.findAllByTestId('plan-day');
  expect(days).toHaveLength(3);
  expect(screen.getByText(/Thursday is 15% under/)).toBeInTheDocument();

  // Yesterday: nothing to change; dinner already ordered.
  const past = within(days[0]);
  expect(past.queryByText('Swap')).not.toBeInTheDocument();
  expect(past.getByText('✓ Ordered')).toBeInTheDocument();
  expect(past.getByText('Eaten : 300 kcal')).toBeInTheDocument();

  // Today: lunch can be ordered or swapped.
  const lunch = within(screen.getByTestId(`meal-${iso(0)}-LUNCH`));
  expect(lunch.getByText('Order')).toBeInTheDocument();
  expect(lunch.getByText('Swap')).toBeInTheDocument();
  expect(lunch.getByText('burrito')).toBeInTheDocument();
  expect(lunch.getByText(/fruit salad/)).toBeInTheDocument();
  expect(within(days[1]).getByText('Eaten so far: 300 kcal')).toBeInTheDocument();

  // Tomorrow: swap only.
  const tomorrowLunch = within(screen.getByTestId(`meal-${iso(1)}-LUNCH`));
  expect(tomorrowLunch.queryByText('Order')).not.toBeInTheDocument();
  expect(tomorrowLunch.getByText('Swap')).toBeInTheDocument();
});

test('swap and order call the server with the day and meal', async () => {
  getMealPlan.mockResolvedValue(plan());
  swapMeal.mockResolvedValue(plan());
  orderMeal.mockResolvedValue(plan());
  renderPage();
  const lunch = within(await screen.findByTestId(`meal-${iso(0)}-LUNCH`));
  await userEvent.click(lunch.getByText('Swap'));
  await waitFor(() => expect(swapMeal).toHaveBeenCalledWith(iso(0), 'LUNCH'));
  await waitFor(() => expect(lunch.getByText('Order')).toBeEnabled());
  await userEvent.click(lunch.getByText('Order'));
  await waitFor(() => expect(orderMeal).toHaveBeenCalledWith(iso(0), 'LUNCH'));
});

test('a missing calorie goal points to Personal Diet', async () => {
  getMealPlan.mockResolvedValue(null);
  createMealPlan.mockRejectedValue(new Error('Set a daily calorie goal on Personal Diet first.'));
  renderPage();
  await userEvent.click(await screen.findByText('Build my plan'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Set a daily calorie goal');
  await userEvent.click(screen.getByText('Set my goal'));
  expect(mockNavigate).toHaveBeenCalledWith('/personal-diet');
});

test('new plan opens the form; clear removes the plan; keyword fallback is labeled', async () => {
  getMealPlan.mockResolvedValue(plan({ aiStatus: 'FALLBACK', weeklyBudget: null }));
  deleteMealPlan.mockResolvedValue(null);
  renderPage();
  expect(await screen.findByText(/read with keywords/)).toBeInTheDocument();
  expect(screen.getByText('no limit set')).toBeInTheDocument();
  await userEvent.click(screen.getByText('New plan'));
  expect(screen.getByText('Start a new plan')).toBeInTheDocument();
  await userEvent.click(screen.getByText('Cancel'));
  expect(screen.queryByText('Start a new plan')).not.toBeInTheDocument();
  await userEvent.click(screen.getByText('Clear plan'));
  expect(await screen.findByText('Plan your week')).toBeInTheDocument();
});

test('a load error is shown', async () => {
  getMealPlan.mockRejectedValue(new Error('server down'));
  renderPage();
  expect(await screen.findByRole('alert')).toHaveTextContent('server down');
});
