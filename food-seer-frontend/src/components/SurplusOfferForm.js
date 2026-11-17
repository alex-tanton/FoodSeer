import React, { useState } from 'react';

const SurplusOfferForm = ({ foods, busy, onPublish }) => {
  const [foodId, setFoodId] = useState('');
  const food = foods.find((item) => item.id === Number(foodId));

  const submit = async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const published = await onPublish({
      foodId: Number(values.get('foodId')),
      ingredients: values.get('ingredients'),
      discountedPrice: Number(values.get('discountedPrice')),
      portions: Number(values.get('portions')),
      pickupLocation: values.get('pickupLocation'),
      pickupStart: new Date(values.get('pickupStart')).toISOString(),
      pickupEnd: new Date(values.get('pickupEnd')).toISOString(),
      expiresAt: new Date(values.get('expiresAt')).toISOString(),
    });
    if (published) {
      form.reset();
      setFoodId('');
    }
  };

  return (
    <section className="surplus-panel">
      <h2>Publish a surplus offer</h2>
      <p>
        Declare prepared surplus portions separately from regular inventory. All prices are USD.
      </p>
      <form onSubmit={submit} className="surplus-form">
        <label htmlFor="surplus-food">Menu item</label>
        <select
          id="surplus-food"
          name="foodId"
          required
          value={foodId}
          onChange={(event) => setFoodId(event.target.value)}
        >
          <option value="">Choose a menu item</option>
          {foods
            .filter((item) => item.price > 0)
            .map((item) => (
              <option key={item.id} value={item.id}>
                {item.foodName} (${Number(item.price).toFixed(2)})
              </option>
            ))}
        </select>
        {food && (
          <p>
            Declared allergens: {food.allergies?.join(', ') || 'None listed'}. These are saved with
            the offer.
          </p>
        )}
        <label htmlFor="surplus-ingredients">Ingredients</label>
        <textarea id="surplus-ingredients" name="ingredients" required maxLength={2000} />
        <label htmlFor="surplus-price">Discount price per portion ($)</label>
        <input
          id="surplus-price"
          name="discountedPrice"
          type="number"
          required
          min="0.01"
          step="0.01"
          max={food ? (food.price - 0.01).toFixed(2) : undefined}
        />
        <label htmlFor="surplus-portions">Surplus portions</label>
        <input
          id="surplus-portions"
          name="portions"
          type="number"
          required
          min="1"
          max="10000"
          step="1"
        />
        <label htmlFor="surplus-location">Pickup location</label>
        <input id="surplus-location" name="pickupLocation" required maxLength={255} />
        <label htmlFor="surplus-start">Pickup window starts</label>
        <input id="surplus-start" name="pickupStart" type="datetime-local" required />
        <label htmlFor="surplus-end">Pickup window ends</label>
        <input id="surplus-end" name="pickupEnd" type="datetime-local" required />
        <label htmlFor="surplus-expiry">Booking deadline</label>
        <input id="surplus-expiry" name="expiresAt" type="datetime-local" required />
        <button type="submit" disabled={busy || !foodId}>
          Publish offer
        </button>
      </form>
    </section>
  );
};

export default SurplusOfferForm;
