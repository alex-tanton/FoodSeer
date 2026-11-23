import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import DriverBatchPanel from './DriverBatchPanel';
import * as batchApi from '../../services/driverBatchApi';

jest.mock('../../services/driverBatchApi');

describe('DriverBatchPanel', () => {
  const mockSuggestions = [
    {
      id: 101,
      batchCode: 'BATCH-88AA',
      status: 'PROPOSED',
      totalDistanceMiles: 2.15,
      estimatedDurationMinutes: 19,
      basePayout: 7.5,
      batchBonus: 2.5,
      totalPayout: 10.0,
      thermalDegradationRisk: 0.04,
      currentStopIndex: 0,
      stops: [
        {
          id: 1,
          stopSequence: 1,
          stopType: 'PICKUP',
          orderName: 'Order #1',
          locationName: 'Tuscany Bistro',
          thermalCategory: 'HOT',
          status: 'PENDING',
        },
        {
          id: 2,
          stopSequence: 2,
          stopType: 'PICKUP',
          orderName: 'Order #2',
          locationName: 'Frosty Freeze',
          thermalCategory: 'COLD',
          status: 'PENDING',
        },
        {
          id: 3,
          stopSequence: 3,
          stopType: 'DROPOFF',
          orderName: 'Order #1',
          locationName: 'Talley Union',
          thermalCategory: 'HOT',
          status: 'PENDING',
        },
        {
          id: 4,
          stopSequence: 4,
          stopType: 'DROPOFF',
          orderName: 'Order #2',
          locationName: 'North Campus',
          thermalCategory: 'COLD',
          status: 'PENDING',
        },
      ],
    },
  ];

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders suggested batch routes when no active batch exists', async () => {
    batchApi.getActiveBatch.mockResolvedValue(null);
    batchApi.getSuggestedBatches.mockResolvedValue(mockSuggestions);

    render(<DriverBatchPanel />);

    expect(screen.getByText('Loading batch dispatcher...')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('⚡ Optimized Batch Dispatcher')).toBeInTheDocument();
      expect(screen.getByText('BATCH-88AA')).toBeInTheDocument();
      expect(screen.getByText('$10')).toBeInTheDocument();
      expect(screen.getByText('+$2.5 Bonus')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '⚡ Claim Batched Route' })).toBeInTheDocument();
    });
  });

  test('claims batch route successfully', async () => {
    batchApi.getActiveBatch.mockResolvedValue(null);
    batchApi.getSuggestedBatches.mockResolvedValue(mockSuggestions);

    const claimedBatch = {
      ...mockSuggestions[0],
      status: 'CLAIMED',
      currentStopIndex: 0,
    };
    batchApi.claimBatch.mockResolvedValue(claimedBatch);

    render(<DriverBatchPanel />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '⚡ Claim Batched Route' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '⚡ Claim Batched Route' }));

    await waitFor(() => {
      expect(batchApi.claimBatch).toHaveBeenCalledWith(101);
      expect(screen.getByText('Successfully claimed batch BATCH-88AA!')).toBeInTheDocument();
      expect(screen.getByTestId('active-batch-section')).toBeInTheDocument();
    });
  });

  test('advances route step in active batch', async () => {
    const activeBatch = {
      ...mockSuggestions[0],
      status: 'CLAIMED',
      currentStopIndex: 0,
    };

    batchApi.getActiveBatch.mockResolvedValue(activeBatch);
    batchApi.getSuggestedBatches.mockResolvedValue([]);
    batchApi.advanceBatchStep.mockResolvedValue({
      batchId: 101,
      currentStopIndex: 1,
      totalStops: 4,
      status: 'IN_TRANSIT',
      message: 'Picked up Order #1 at Tuscany Bistro',
      batchCompleted: false,
    });

    render(<DriverBatchPanel />);

    await waitFor(() => {
      expect(screen.getByText('Turn-by-Turn Route Sequence')).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: 'Confirm Pickup at Tuscany Bistro' })
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Confirm Pickup at Tuscany Bistro' }));

    await waitFor(() => {
      expect(batchApi.advanceBatchStep).toHaveBeenCalledWith(101);
      expect(screen.getByText('Picked up Order #1 at Tuscany Bistro')).toBeInTheDocument();
    });
  });

  test('displays error message when API fails', async () => {
    batchApi.getActiveBatch.mockRejectedValue(new Error('Network connection failed'));
    batchApi.getSuggestedBatches.mockRejectedValue(new Error('Network connection failed'));

    render(<DriverBatchPanel />);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Network connection failed');
    });
  });
});
