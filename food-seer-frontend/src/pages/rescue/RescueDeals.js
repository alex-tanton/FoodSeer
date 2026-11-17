import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { reserveSurplusOffer } from '../../services/api';
import { getRescueDeals, earliestPickup } from '../../services/rescueApi';
import '../SurplusMarketplace.css';
import './Rescue.css';

const money = (value) => `$${Number(value).toFixed(2)}`;
const when = (value) => new Date(value).toLocaleString();

const DealCard = ({ deal, busy, onReserve }) => {
  const { offer } = deal;
  return (
    <article className="surplus-panel">
      <h3>{offer.foodName}</h3>
      <span className="rescue-reason">{deal.reason}</span>
      <p>
        <del>{money(offer.originalPrice)}</del> <strong>{money(offer.discountedPrice)}</strong>
      </p>
      <p>{offer.remainingPortions} portions left</p>
      <p>
        <strong>Declared allergens:</strong> {offer.allergens.join(', ') || 'None listed'}
      </p>
      <p>
        <strong>Pickup:</strong> {offer.pickupLocation}, {when(offer.pickupStart)} –{' '}
        {when(offer.pickupEnd)}
      </p>
      <p>Book before {when(offer.expiresAt)}</p>
      {deal.reserved ? (
        <p className="rescue-status">✓ You reserved this. See it under My reservations.</p>
      ) : (
        <button disabled={busy} onClick={() => onReserve(deal)}>
          Reserve 1 portion of {offer.foodName}
        </button>
      )}
    </article>
  );
};

// Customer page: surplus deals FoodSeer picked for this customer, with the
// reason they were picked and a one-tap reserve.
const RescueDeals = () => {
  const navigate = useNavigate();
  const [deals, setDeals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    try {
      setDeals(await getRescueDeals());
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

  const reserve = async (deal) => {
    const pickupAt = earliestPickup(deal.offer);
    if (!pickupAt) {
      setError('The pickup window for this deal has closed.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const reservation = await reserveSurplusOffer(deal.offer.id, { quantity: 1, pickupAt });
      setMessage(
        `Reserved! Reservation #${reservation.id}: pay ${money(reservation.totalPrice)} at ` +
          `${deal.offer.pickupLocation}, from ${when(reservation.pickupAt)}.`
      );
      await load();
    } catch (reserveError) {
      setError(reserveError.message);
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <div className="surplus-marketplace">
        <p>Loading your rescue deals...</p>
      </div>
    );
  }

  return (
    <div className="surplus-marketplace">
      <div className="surplus-heading">
        <h1>♻️ Rescue deals for you</h1>
        <button className="back-button" onClick={() => navigate('/surplus')}>
          My reservations
        </button>
      </div>
      <p>
        Discounted surplus that would otherwise be thrown out. We only pick deals that avoid your
        saved allergies and fit your budget and remaining calories.
      </p>
      {message && <p className="rescue-status">{message}</p>}
      {error && <p className="rescue-error">{error}</p>}
      {deals.length === 0 ? (
        <p>No rescue deals for you right now. We will alert you when one fits.</p>
      ) : (
        <div className="surplus-grid">
          {deals.map((deal) => (
            <DealCard key={deal.alertId} deal={deal} busy={busy} onReserve={reserve} />
          ))}
        </div>
      )}
    </div>
  );
};

export default RescueDeals;
