import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { BrowserRouter } from 'react-router-dom';
import GroupDiningHub from './GroupDiningHub';
import GroupRoom from './GroupRoom';
import * as groupApi from '../../services/groupDiningApi';

jest.mock('../../services/groupDiningApi');

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
  useParams: () => ({ sessionCode: 'ROOM42' }),
}));

describe('GroupDiningHub', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders create and join room forms', () => {
    render(
      <BrowserRouter>
        <GroupDiningHub />
      </BrowserRouter>
    );

    expect(screen.getByText('🍽️ Group Dining Harmonizer')).toBeInTheDocument();
    expect(screen.getByText('🏠 Create a Room')).toBeInTheDocument();
    expect(screen.getByText('🎟️ Join with Room Code')).toBeInTheDocument();
  });

  test('submits create room and navigates to group room', async () => {
    groupApi.createGroupSession.mockResolvedValue({ sessionCode: 'NEW99' });

    render(
      <BrowserRouter>
        <GroupDiningHub />
      </BrowserRouter>
    );

    fireEvent.change(screen.getByLabelText('Room Name'), {
      target: { value: 'Study Lunch' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Create Dining Room' }));

    await waitFor(() => {
      expect(groupApi.createGroupSession).toHaveBeenCalledWith('Study Lunch');
      expect(mockNavigate).toHaveBeenCalledWith('/group-dining/NEW99');
    });
  });

  test('submits join room with dietary profile', async () => {
    groupApi.joinGroupSession.mockResolvedValue({ sessionCode: 'JOIN88' });

    render(
      <BrowserRouter>
        <GroupDiningHub />
      </BrowserRouter>
    );

    fireEvent.change(screen.getByLabelText('Room Code'), {
      target: { value: 'JOIN88' },
    });
    fireEvent.change(screen.getByLabelText('Your Display Name'), {
      target: { value: 'Leo' },
    });
    fireEvent.change(screen.getByLabelText('Allergies / Restrictions'), {
      target: { value: 'Peanuts' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Join Dining Room' }));

    await waitFor(() => {
      expect(groupApi.joinGroupSession).toHaveBeenCalledWith('JOIN88', {
        displayName: 'Leo',
        dietaryRestrictions: 'Peanuts',
        costPreference: 'moderate',
      });
      expect(mockNavigate).toHaveBeenCalledWith('/group-dining/JOIN88');
    });
  });
});

describe('GroupRoom', () => {
  const mockSession = {
    id: 1,
    sessionCode: 'ROOM42',
    sessionName: 'Library Study Group',
    hostUsername: 'host_alex',
    status: 'ACTIVE',
    members: [
      { id: 1, username: 'host_alex', displayName: 'Alex', dietaryRestrictions: null },
      { id: 2, username: 'leo', displayName: 'Leo', dietaryRestrictions: 'PEANUTS' },
    ],
    cartItems: [
      { id: 10, memberId: 1, foodId: 1, foodName: 'SALAD', price: 10, quantity: 1 },
      { id: 11, memberId: 1, foodId: 2, foodName: 'PEANUT NOODLES', price: 15, quantity: 1 },
    ],
  };

  const mockMenu = {
    sessionCode: 'ROOM42',
    totalMembers: 2,
    safeDishCount: 1,
    dishes: [
      {
        foodId: 1,
        foodName: 'SALAD',
        price: 10,
        calories: 150,
        allergies: [],
        isUniversallySafe: true,
        memberConflicts: [],
        harmonyScore: 1.0,
      },
      {
        foodId: 2,
        foodName: 'PEANUT NOODLES',
        price: 15,
        calories: 450,
        allergies: ['PEANUTS'],
        isUniversallySafe: false,
        memberConflicts: [{ memberDisplayName: 'Leo', conflictReason: 'Contains PEANUT' }],
        harmonyScore: 0.5,
      },
    ],
  };

  const mockBill = {
    sessionCode: 'ROOM42',
    overallSubtotal: 25.0,
    taxAndFees: 2.0,
    totalAmount: 27.0,
    memberShares: [
      {
        memberUsername: 'host_alex',
        memberDisplayName: 'Alex',
        itemCount: 2,
        itemsSubtotal: 25.0,
        taxShare: 2.0,
        totalOwed: 27.0,
      },
      {
        memberUsername: 'leo',
        memberDisplayName: 'Leo',
        itemCount: 0,
        itemsSubtotal: 0.0,
        taxShare: 0.0,
        totalOwed: 0.0,
      },
    ],
    contaminationAlerts: [
      {
        sourceDishName: 'PEANUT NOODLES',
        orderedByDisplayName: 'Alex',
        allergen: 'PEANUT',
        affectedMemberDisplayName: 'Leo',
        recommendation: 'Request separate packaging.',
      },
    ],
  };

  beforeEach(() => {
    jest.clearAllMocks();
    groupApi.getGroupSession.mockResolvedValue(mockSession);
    groupApi.getHarmonizedMenu.mockResolvedValue(mockMenu);
    groupApi.getSplitBill.mockResolvedValue(mockBill);
  });

  test('renders session header, member chips, and harmonized menu dishes with safety badges', async () => {
    render(
      <BrowserRouter>
        <GroupRoom />
      </BrowserRouter>
    );

    expect(await screen.findByText('Library Study Group')).toBeInTheDocument();
    expect(screen.getByText('CODE: ROOM42')).toBeInTheDocument();
    expect(screen.getByText('👤 Leo')).toBeInTheDocument();
    expect(screen.getByText('PEANUTS')).toBeInTheDocument();

    // Menu dishes and badges
    expect(screen.getByText('SALAD')).toBeInTheDocument();
    expect(screen.getByText('✅ Safe for all 2 members')).toBeInTheDocument();

    expect(screen.getByText('PEANUT NOODLES')).toBeInTheDocument();
    expect(screen.getByText('⚠️ Allergen Conflict:')).toBeInTheDocument();
    expect(screen.getByText('• Leo: Contains PEANUT')).toBeInTheDocument();
  });

  test('switches to shared cart and displays cross-contamination banner and split bill', async () => {
    render(
      <BrowserRouter>
        <GroupRoom />
      </BrowserRouter>
    );

    await screen.findByText('Library Study Group');

    // Click on Shared Cart tab
    fireEvent.click(screen.getByRole('button', { name: /Shared Cart & Split Bill/i }));

    // Verify cross-contamination alert
    expect(screen.getByText('⚠️ Table Cross-Contamination Alert')).toBeInTheDocument();
    expect(screen.getByText(/Request separate packaging/i)).toBeInTheDocument();

    // Verify individual tray
    expect(screen.getByText("🍽️ Alex's Tray")).toBeInTheDocument();
    expect(screen.getByText('1x SALAD ($10.00)')).toBeInTheDocument();

    // Verify split bill table
    expect(screen.getByText('$25.00')).toBeInTheDocument();
    expect(screen.getAllByText('$27.00').length).toBeGreaterThanOrEqual(1);
  });

  test('adds item to tray', async () => {
    groupApi.addCartItem.mockResolvedValue({});

    render(
      <BrowserRouter>
        <GroupRoom />
      </BrowserRouter>
    );

    await screen.findByText('SALAD');

    const addButtons = screen.getAllByRole('button', { name: '+ Add to Tray' });
    fireEvent.click(addButtons[0]);

    await waitFor(() => {
      expect(groupApi.addCartItem).toHaveBeenCalledWith('ROOM42', {
        foodId: 1,
        quantity: 1,
      });
    });
  });
});
