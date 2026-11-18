import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getRescueStats, previewRescueMatches, sendRescueAlerts } from '../../services/rescueApi';
import '../SurplusMarketplace.css';
import './Rescue.css';

const money = (value) => `$${Number(value).toFixed(2)}`;
const percent = (value) => `${Math.round(value * 100)}%`;
const open = (stat, now) => stat.remainingPortions > 0 && new Date(stat.expiresAt).getTime() > now;

const sum = (stats, key) => stats.reduce((total, stat) => total + Number(stat[key]), 0);

const Totals = ({ stats }) => (
  <div className="rescue-numbers" aria-label="Rescue totals">
    <div>
      <strong>{sum(stats, 'portionsRescued')}</strong>meals rescued
    </div>
    <div>
      <strong>{money(sum(stats, 'revenueRecovered'))}</strong>revenue recovered
    </div>
    <div>
      <strong>{money(sum(stats, 'customerSavings'))}</strong>customers saved
    </div>
    <div>
      <strong>{sum(stats, 'alerted')}</strong>customers alerted
    </div>
    <div>
      <strong>{sum(stats, 'alertedWhoReserved')}</strong>alerted customers reserved
    </div>
  </div>
);

const Preview = ({ preview }) => (
  <div>
    <h4>
      Would alert {Math.min(preview.alertBudget, preview.ranked.length)} of {preview.ranked.length}{' '}
      eligible customers
    </h4>
    {preview.ranked.length > 0 && (
      <table className="rescue-table">
        <thead>
          <tr>
            <th>Customer</th>
            <th>Score</th>
            <th>Why</th>
            <th>Alert?</th>
          </tr>
        </thead>
        <tbody>
          {preview.ranked.map((match) => (
            <tr key={match.username}>
              <td>{match.username}</td>
              <td>{percent(match.score)}</td>
              <td>{match.reason}</td>
              <td>{match.willAlert ? 'Yes' : 'Over budget'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    )}
    {preview.excluded.length > 0 && (
      <>
        <h4>Not alerted ({preview.excluded.length})</h4>
        <table className="rescue-table">
          <tbody>
            {preview.excluded.map((excluded) => (
              <tr key={excluded.username}>
                <td>{excluded.username}</td>
                <td>{excluded.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </>
    )}
  </div>
);

const OfferStats = ({ stat, now, busy, preview, onPreview, onSend }) => (
  <article className="surplus-panel">
    <h3>{stat.foodName}</h3>
    <p>
      {open(stat, now) ? `${stat.remainingPortions} portions left` : 'Closed or sold out'} ·{' '}
      {stat.alerted} alerted · {stat.alertedWhoReserved} of them reserved
    </p>
    <p>
      {stat.portionsRescued} portions rescued ({stat.portionsCollected} collected) ·{' '}
      {money(stat.revenueRecovered)} recovered · customers saved {money(stat.customerSavings)}
    </p>
    {open(stat, now) && (
      <div className="header-actions">
        <button disabled={busy} onClick={() => onPreview(stat.offerId)}>
          Preview matches for {stat.foodName}
        </button>
        <button disabled={busy} onClick={() => onSend(stat.offerId)}>
          Send rescue alerts for {stat.foodName}
        </button>
      </div>
    )}
    {preview && <Preview preview={preview} />}
  </article>
);

// Staff page: who rescue matching would alert for each surplus offer, and how
// much surplus was rescued.
const RescueManage = () => {
  const navigate = useNavigate();
  const [stats, setStats] = useState([]);
  const [previews, setPreviews] = useState({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [now, setNow] = useState(Date.now());

  const load = useCallback(async () => {
    try {
      setStats(await getRescueStats());
      setNow(Date.now());
      setError('');
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action) => {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (actionError) {
      setError(actionError.message);
    } finally {
      setBusy(false);
    }
  };

  const preview = (offerId) =>
    run(async () => {
      const result = await previewRescueMatches(offerId);
      setPreviews((current) => ({ ...current, [offerId]: result }));
    });

  const send = (offerId) =>
    run(async () => {
      const result = await sendRescueAlerts(offerId);
      setMessage(
        result.alerted.length === 0
          ? 'Nobody new to alert for this offer.'
          : `Alerted ${result.alerted.length} customers: ${result.alerted.join(', ')}.`
      );
      setPreviews((current) => ({ ...current, [offerId]: undefined }));
      await load();
    });

  if (loading) {
    return (
      <div className="surplus-marketplace">
        <p>Loading rescue results...</p>
      </div>
    );
  }

  return (
    <div className="surplus-marketplace">
      <div className="surplus-heading">
        <h1>♻️ Rescue matching</h1>
        <button className="back-button" onClick={() => navigate('/surplus')}>
          Publish surplus
        </button>
      </div>
      <p>
        Alerts go only to customers the dish is safe for (saved allergies), who can afford it, who
        have the calories left today, and who have not hit today&apos;s alert limit.
      </p>
      {message && <p className="rescue-status">{message}</p>}
      {error && <p className="rescue-error">{error}</p>}
      <Totals stats={stats} />
      {stats.length === 0 ? (
        <p>No surplus offers yet. Publish one in the Surplus Marketplace.</p>
      ) : (
        stats.map((stat) => (
          <OfferStats
            key={stat.offerId}
            stat={stat}
            now={now}
            busy={busy}
            preview={previews[stat.offerId]}
            onPreview={preview}
            onSend={send}
          />
        ))
      )}
    </div>
  );
};

export default RescueManage;
