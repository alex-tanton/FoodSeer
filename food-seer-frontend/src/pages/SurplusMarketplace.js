import React, { useCallback, useEffect, useState } from 'react';
import {
  getCurrentUser,
  getAllFoods,
  getSurplusOffers,
  getSurplusReservations,
  publishSurplusOffer,
  reserveSurplusOffer,
  cancelSurplusReservation,
  collectSurplusReservation,
} from '../services/api';
import SurplusOfferForm from '../components/SurplusOfferForm';
import './SurplusMarketplace.css';

const money = (value) => `$${Number(value).toFixed(2)}`;
const when = (value) => new Date(value).toLocaleString();
const localInput = (value) => {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};

const OfferCard = ({ offer, customer, busy, now, onReserve }) => {
  const available = new Date(offer.expiresAt).getTime() > now && offer.remainingPortions > 0;
  const submit = async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const reserved = await onReserve(offer.id, {
      quantity: Number(values.get('quantity')),
      pickupAt: new Date(values.get('pickupAt')).toISOString(),
    });
    if (reserved) form.reset();
  };
  return (
    <article className="surplus-panel">
      <h3>{offer.foodName}</h3>
      <p>
        <del>{money(offer.originalPrice)}</del>{' '}
        <strong>{money(offer.discountedPrice)} per portion</strong>
      </p>
      <p>
        {offer.remainingPortions} portions left ·{' '}
        {available ? 'Open for booking' : 'Closed or sold out'}
      </p>
      <p>
        <strong>Ingredients:</strong> {offer.ingredients}
      </p>
      <p>
        <strong>Declared allergens:</strong>{' '}
        {offer.allergens.join(', ') || 'None listed; check ingredients with staff'}
      </p>
      {offer.calories != null && <p>{offer.calories} calories per portion</p>}
      <p>
        <strong>Pickup:</strong> {offer.pickupLocation}
      </p>
      <p>
        {when(offer.pickupStart)} – {when(offer.pickupEnd)}
      </p>
      <p>Book before {when(offer.expiresAt)}</p>
      {customer && available && (
        <form onSubmit={submit} className="surplus-form">
          <label htmlFor={`quantity-${offer.id}`}>Portions for {offer.foodName}</label>
          <input
            id={`quantity-${offer.id}`}
            name="quantity"
            type="number"
            required
            min="1"
            max={Math.min(offer.remainingPortions, 10000)}
            step="1"
            defaultValue="1"
          />
          <label htmlFor={`pickup-${offer.id}`}>Pickup time for {offer.foodName}</label>
          <input
            id={`pickup-${offer.id}`}
            name="pickupAt"
            type="datetime-local"
            required
            min={localInput(Math.max(new Date(offer.pickupStart).getTime(), now))}
            max={localInput(offer.pickupEnd)}
          />
          <button disabled={busy} type="submit">
            Reserve {offer.foodName}
          </button>
        </form>
      )}
    </article>
  );
};

const SurplusMarketplace = () => {
  const [user, setUser] = useState(null);
  const [offers, setOffers] = useState([]);
  const [reservations, setReservations] = useState([]);
  const [foods, setFoods] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [now, setNow] = useState(Date.now());
  const staff = ['ROLE_STAFF', 'ROLE_ADMIN'].includes(user?.role);
  const customer = user?.role === 'ROLE_CUSTOMER';

  const load = useCallback(async (isStaff) => {
    const [nextOffers, nextReservations, menu] = await Promise.all([
      getSurplusOffers(isStaff),
      getSurplusReservations(),
      isStaff ? getAllFoods() : Promise.resolve([]),
    ]);
    setOffers(nextOffers);
    setReservations(nextReservations);
    setFoods(menu);
    setNow(Date.now());
  }, []);

  const initialize = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const account = await getCurrentUser();
      setUser(account);
      if (['ROLE_STAFF', 'ROLE_ADMIN', 'ROLE_CUSTOMER'].includes(account.role)) {
        await load(['ROLE_STAFF', 'ROLE_ADMIN'].includes(account.role));
      }
    } catch (failure) {
      setError(failure.message);
    } finally {
      setLoading(false);
    }
  }, [load]);

  useEffect(() => {
    initialize();
  }, [initialize]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(timer);
  }, []);

  const act = async (operation, success) => {
    setBusy(true);
    setError('');
    setMessage('');
    let completed = false;
    try {
      const result = await operation();
      completed = true;
      setMessage(typeof success === 'function' ? success(result) : success);
    } catch (failure) {
      setError(failure.message);
    }
    // Refresh even after a conflict so stale portions disappear from the page.
    try {
      await load(staff);
    } catch (failure) {
      setError(`Could not refresh the marketplace: ${failure.message}`);
    } finally {
      setBusy(false);
    }
    return completed;
  };

  return (
    <main className="surplus-marketplace">
      <div className="surplus-heading">
        <div>
          <h1>Surplus food marketplace</h1>
          <p>Save a meal. Reserve discounted surplus portions and pay at pickup.</p>
        </div>
        <button onClick={initialize} disabled={loading || busy}>
          Refresh marketplace
        </button>
      </div>
      <p>
        All times are shown in your local timezone. Review ingredients and declared allergens before
        reserving.
      </p>
      {error && (
        <p role="alert" className="surplus-error">
          {error}
        </p>
      )}
      {message && (
        <p role="status" className="surplus-success">
          {message}
        </p>
      )}
      {loading ? (
        <p role="status">Loading marketplace…</p>
      ) : !staff && !customer ? (
        user && <p>The marketplace is available to customers, staff, and admins.</p>
      ) : (
        <>
          {staff && (
            <SurplusOfferForm
              foods={foods}
              busy={busy}
              onPublish={(offer) =>
                act(() => publishSurplusOffer(offer), 'Surplus offer published.')
              }
            />
          )}
          <h2>{staff ? 'Published offers' : 'Available offers'}</h2>
          {offers.length === 0 && (
            <p>
              {staff
                ? 'No surplus offers published yet.'
                : 'No surplus offers available right now. Check back soon.'}
            </p>
          )}
          <div className="surplus-grid">
            {offers.map((offer) => (
              <OfferCard
                key={offer.id}
                offer={offer}
                customer={customer}
                busy={busy}
                now={now}
                onReserve={(id, request) =>
                  act(
                    () => reserveSurplusOffer(id, request),
                    (result) =>
                      `Reservation #${result.id} confirmed. Pay ${money(result.totalPrice)} at pickup.`
                  )
                }
              />
            ))}
          </div>
          <h2>{staff ? 'Customer reservations' : 'My reservations'}</h2>
          {reservations.length === 0 && <p>No reservations yet.</p>}
          <div className="surplus-grid">
            {reservations.map((reservation) => (
              <article className="surplus-panel" key={reservation.id}>
                <h3>
                  Reservation #{reservation.id}: {reservation.offer.foodName}
                </h3>
                {staff && <p>Customer: {reservation.customerUsername}</p>}
                <p>
                  {reservation.quantity} portions · {money(reservation.totalPrice)} total ·{' '}
                  {reservation.status}
                </p>
                <p>
                  Pickup: {when(reservation.pickupAt)} at {reservation.offer.pickupLocation}
                </p>
                {reservation.status === 'RESERVED' && customer && (
                  <button
                    disabled={busy || now >= new Date(reservation.pickupAt).getTime()}
                    onClick={() =>
                      act(() => cancelSurplusReservation(reservation.id), 'Reservation cancelled.')
                    }
                  >
                    Cancel reservation #{reservation.id}
                  </button>
                )}
                {reservation.status === 'RESERVED' && staff && (
                  <button
                    disabled={
                      busy ||
                      now < new Date(reservation.offer.pickupStart).getTime() ||
                      now > new Date(reservation.offer.pickupEnd).getTime()
                    }
                    onClick={() =>
                      act(
                        () => collectSurplusReservation(reservation.id),
                        'Reservation marked collected.'
                      )
                    }
                  >
                    Mark #{reservation.id} collected
                  </button>
                )}
              </article>
            ))}
          </div>
        </>
      )}
    </main>
  );
};

export default SurplusMarketplace;
