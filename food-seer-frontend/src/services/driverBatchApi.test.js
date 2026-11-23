import {
  getSuggestedBatches,
  claimBatch,
  advanceBatchStep,
  getActiveBatch,
  getBatchById,
} from './driverBatchApi';

describe('driverBatchApi', () => {
  beforeEach(() => {
    localStorage.setItem('token', 'test-token');
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ success: true }),
    });
  });

  afterEach(() => {
    localStorage.clear();
    jest.restoreAllMocks();
    delete global.fetch;
  });

  test.each([
    [() => getSuggestedBatches(), '/suggestions', 'GET'],
    [() => claimBatch(10), '/10/claim', 'POST'],
    [() => advanceBatchStep(10), '/10/step', 'POST'],
    [() => getActiveBatch(), '/active', 'GET'],
    [() => getBatchById(10), '/10', 'GET'],
  ])('sends authenticated batch request %#', async (call, path, method) => {
    await call();
    expect(fetch).toHaveBeenCalledWith(
      `http://localhost:8080/api/driver/batches${path}`,
      expect.objectContaining({
        method,
        headers: expect.objectContaining({
          Authorization: 'Bearer test-token',
        }),
      })
    );
  });

  test('handles and preserves backend error messages', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'Batch not available to claim' }),
    });

    await expect(claimBatch(99)).rejects.toThrow('Batch not available to claim');
  });
});
