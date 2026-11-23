import React, { useState, useEffect } from 'react';
import {
  getSuggestedBatches,
  getActiveBatch,
  claimBatch,
  advanceBatchStep,
} from '../../services/driverBatchApi';
import './DriverBatchPanel.css';

const DriverBatchPanel = ({ onEarningsUpdated }) => {
  const [activeBatch, setActiveBatch] = useState(null);
  const [suggestedBatches, setSuggestedBatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const loadBatches = async () => {
    try {
      setLoading(true);
      setError(null);
      const [active, suggestions] = await Promise.all([getActiveBatch(), getSuggestedBatches()]);
      setActiveBatch(active);
      setSuggestedBatches(suggestions || []);
    } catch (err) {
      setError(err.message || 'Failed to load batch routes.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBatches();
  }, []);

  const handleClaimBatch = async (batchId) => {
    try {
      setActionLoading(true);
      setError(null);
      const claimed = await claimBatch(batchId);
      setActiveBatch(claimed);
      setSuccessMsg(`Successfully claimed batch ${claimed.batchCode}!`);
      // Reload suggestions to update list
      const suggestions = await getSuggestedBatches().catch(() => []);
      setSuggestedBatches(suggestions || []);
    } catch (err) {
      setError(err.message || 'Failed to claim batch.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleAdvanceStep = async () => {
    if (!activeBatch) return;
    try {
      setActionLoading(true);
      setError(null);
      const stepResp = await advanceBatchStep(activeBatch.id);
      setSuccessMsg(stepResp.message);

      // Refresh active batch
      const updatedActive = await getActiveBatch().catch(() => null);
      setActiveBatch(updatedActive);

      if (stepResp.batchCompleted) {
        setSuccessMsg(
          `🎉 Batch completed! Bonus of $${stepResp.totalEarningsAwarded || '0.00'} awarded!`
        );
        if (onEarningsUpdated) onEarningsUpdated();
      }
    } catch (err) {
      setError(err.message || 'Failed to advance route stop.');
    } finally {
      setActionLoading(false);
    }
  };

  const renderThermalBadge = (thermal) => {
    switch (thermal) {
      case 'HOT':
        return <span className="thermal-badge hot">🔥 Hot Food</span>;
      case 'COLD':
        return <span className="thermal-badge cold">❄️ Cold / Chilled</span>;
      default:
        return <span className="thermal-badge ambient">🌱 Ambient</span>;
    }
  };

  if (loading) {
    return <div className="batch-panel-loading">Loading batch dispatcher...</div>;
  }

  return (
    <div className="driver-batch-panel" data-testid="driver-batch-panel">
      <div className="batch-panel-header">
        <div>
          <h2>⚡ Optimized Batch Dispatcher</h2>
          <p className="batch-subtitle">
            Multi-order delivery routes optimized for fuel efficiency and thermal freshness.
          </p>
        </div>
        <button className="batch-refresh-btn" onClick={loadBatches} disabled={actionLoading}>
          🔄 Refresh Batches
        </button>
      </div>

      {error && (
        <div className="batch-alert error" role="alert">
          {error}
        </div>
      )}
      {successMsg && <div className="batch-alert success">{successMsg}</div>}

      {/* ---------------- Active Batch Route ---------------- */}
      {activeBatch ? (
        <section className="active-batch-section" data-testid="active-batch-section">
          <div className="active-batch-card">
            <div className="active-batch-header">
              <div>
                <span className="batch-status-pill">{activeBatch.status}</span>
                <h3>{activeBatch.batchCode}</h3>
              </div>
              <div className="batch-payout-box">
                <span className="payout-label">Guaranteed Payout</span>
                <span className="payout-amount">${activeBatch.totalPayout}</span>
                {activeBatch.batchBonus > 0 && (
                  <span className="payout-bonus">+${activeBatch.batchBonus} Batch Bonus</span>
                )}
              </div>
            </div>

            <div className="batch-metrics-row">
              <div className="metric-item">
                <span className="metric-label">Total Distance</span>
                <span className="metric-val">{activeBatch.totalDistanceMiles} mi</span>
              </div>
              <div className="metric-item">
                <span className="metric-label">Est. Duration</span>
                <span className="metric-val">{activeBatch.estimatedDurationMinutes} mins</span>
              </div>
              <div className="metric-item">
                <span className="metric-label">Freshness Rating</span>
                <span className="metric-val fresh">
                  {activeBatch.thermalDegradationRisk < 0.1 ? '🌟 Optimal Freshness' : '✓ Good'}
                </span>
              </div>
              <div className="metric-item">
                <span className="metric-label">Route Progress</span>
                <span className="metric-val">
                  Stop {activeBatch.currentStopIndex} of {activeBatch.stops?.length || 0}
                </span>
              </div>
            </div>

            {/* Sequence of stops */}
            <div className="stops-timeline">
              <h4>Turn-by-Turn Route Sequence</h4>
              <div className="stops-list">
                {activeBatch.stops?.map((stop, idx) => {
                  const isCurrent = idx === activeBatch.currentStopIndex;
                  const isDone = stop.status === 'COMPLETED';
                  return (
                    <div
                      key={stop.id || idx}
                      className={`stop-row ${isCurrent ? 'current' : ''} ${isDone ? 'completed' : ''}`}
                    >
                      <div className="stop-step-num">{stop.stopSequence}</div>
                      <div className="stop-info">
                        <div className="stop-title-line">
                          <span className={`stop-type-tag ${stop.stopType?.toLowerCase()}`}>
                            {stop.stopType === 'PICKUP' ? '🛍️ PICKUP' : '📍 DROPOFF'}
                          </span>
                          <strong>{stop.locationName}</strong>
                          {renderThermalBadge(stop.thermalCategory)}
                        </div>
                        <div className="stop-sub-line">
                          <span>{stop.orderName}</span>
                          {stop.allergyNotice && (
                            <span className="allergy-warn">⚠️ Allergy: {stop.allergyNotice}</span>
                          )}
                        </div>
                      </div>
                      <div className="stop-status-col">
                        {isDone ? (
                          <span className="status-tag done">✓ Done</span>
                        ) : isCurrent ? (
                          <span className="status-tag current">▶ Next Up</span>
                        ) : (
                          <span className="status-tag pending">Queued</span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Action controller button */}
            <div className="batch-action-bar">
              {activeBatch.currentStopIndex < (activeBatch.stops?.length || 0) ? (
                <button
                  className="batch-step-btn"
                  onClick={handleAdvanceStep}
                  disabled={actionLoading}
                >
                  {actionLoading
                    ? 'Processing...'
                    : activeBatch.stops[activeBatch.currentStopIndex]?.stopType === 'PICKUP'
                      ? `Confirm Pickup at ${activeBatch.stops[activeBatch.currentStopIndex]?.locationName}`
                      : `Confirm Delivery to ${activeBatch.stops[activeBatch.currentStopIndex]?.locationName}`}
                </button>
              ) : (
                <div className="batch-complete-notice">
                  ✓ All stops completed. Batch payout credited!
                </div>
              )}
            </div>
          </div>
        </section>
      ) : null}

      {/* ---------------- Available Batch Opportunities ---------------- */}
      <section className="suggested-batches-section">
        <h3>Available Batch Routes ({suggestedBatches.length})</h3>
        {suggestedBatches.length === 0 ? (
          <div className="no-batches-card">
            <p>
              No batched routes available at the moment. As more orders arrive, the routing engine
              will automatically group proximate orders.
            </p>
          </div>
        ) : (
          <div className="batches-grid">
            {suggestedBatches.map((batch) => (
              <div key={batch.id} className="suggested-batch-card">
                <div className="suggested-header">
                  <div>
                    <span className="batch-code-badge">{batch.batchCode}</span>
                    <h4>{batch.stops?.length / 2} Batched Orders</h4>
                  </div>
                  <div className="suggested-payout">
                    <span className="total-payout">${batch.totalPayout}</span>
                    <span className="bonus-pill">+${batch.batchBonus} Bonus</span>
                  </div>
                </div>

                <div className="suggested-metrics">
                  <span>🚗 {batch.totalDistanceMiles} mi</span>
                  <span>⏱️ {batch.estimatedDurationMinutes} mins</span>
                  <span>
                    🌡️ Freshness Score: {(100 - batch.thermalDegradationRisk * 100).toFixed(0)}%
                  </span>
                </div>

                <div className="suggested-stops-preview">
                  <span className="preview-label">Route Sequence:</span>
                  <ol>
                    {batch.stops?.map((s) => (
                      <li key={s.id || s.stopSequence}>
                        {s.stopType === 'PICKUP' ? '🛍️' : '📍'} {s.locationName} (
                        {s.thermalCategory})
                      </li>
                    ))}
                  </ol>
                </div>

                <button
                  className="claim-batch-btn"
                  onClick={() => handleClaimBatch(batch.id)}
                  disabled={actionLoading || !!activeBatch}
                >
                  {activeBatch ? 'Active Batch in Progress' : '⚡ Claim Batched Route'}
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
};

export default DriverBatchPanel;
