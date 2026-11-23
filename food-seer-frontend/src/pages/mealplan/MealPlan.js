import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  createMealPlan,
  getMealPlan,
  swapMeal,
  orderMeal,
  deleteMealPlan,
} from '../../services/mealPlanApi';
import './MealPlan.css';

const MEAL_LABEL = { BREAKFAST: 'Breakfast', LUNCH: 'Lunch', DINNER: 'Dinner' };
const AI_LABEL = {
  USED: 'Read by the AI assistant',
  FALLBACK: 'The AI assistant was unavailable, so your request was read with keywords',
};

const todayIso = () => {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

const shortDate = (iso) =>
  new Date(`${iso}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

/** The form that builds a plan. */
const PlanForm = ({ busy, onSubmit, onCancel }) => {
  const [days, setDays] = useState(7);
  const [weeklyBudget, setWeeklyBudget] = useState('');
  const [request, setRequest] = useState('');
  const [useAi, setUseAi] = useState(true);

  const submit = (event) => {
    event.preventDefault();
    onSubmit({ days: Number(days), weeklyBudget, request, useAi });
  };

  return (
    <form className="mealplan-form" onSubmit={submit}>
      <label>
        Days to plan
        <select value={days} onChange={(e) => setDays(e.target.value)}>
          {[1, 2, 3, 4, 5, 6, 7].map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </label>
      <label>
        Spending limit for the plan ($)
        <input
          type="number"
          min="10"
          placeholder="No limit"
          value={weeklyBudget}
          onChange={(e) => setWeeklyBudget(e.target.value)}
        />
      </label>
      <label className="mealplan-request">
        Anything else? (optional)
        <textarea
          aria-label="Plan request"
          placeholder="e.g. skip breakfast, pizza on Friday, no salads, don't repeat dishes"
          maxLength={500}
          value={request}
          onChange={(e) => setRequest(e.target.value)}
        />
      </label>
      <label className="mealplan-check">
        <input type="checkbox" checked={useAi} onChange={(e) => setUseAi(e.target.checked)} />
        Let the AI assistant read my request
      </label>
      <div className="mealplan-actions">
        <button type="submit" className="primary" disabled={busy}>
          {busy ? 'Planning…' : 'Build my plan'}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
};

/** One planned meal with its swap and order buttons. */
const MealRow = ({ day, meal, isPast, busy, onSwap, onOrder }) => (
  <div className="mealplan-meal" data-testid={`meal-${day.date}-${meal.meal}`}>
    <div className="mealplan-meal-head">
      <span>{MEAL_LABEL[meal.meal]}</span>
      <span>
        {meal.calories} kcal · ${meal.cost}
      </span>
    </div>
    <div className="mealplan-dishes">
      {meal.dishes.length === 0
        ? 'Nothing safe available'
        : meal.dishes.map((dish, i) => (
            <span key={`${dish.id}-${i}`} className={i === 0 ? 'main' : 'side'}>
              {i > 0 && ' + '}
              {dish.name.toLowerCase()}
            </span>
          ))}
    </div>
    <div className="mealplan-actions">
      {meal.ordered ? (
        <span className="mealplan-ordered">✓ Ordered</span>
      ) : (
        <>
          {meal.orderable && (
            <button
              className="primary"
              disabled={busy}
              onClick={() => onOrder(day.date, meal.meal)}
            >
              Order
            </button>
          )}
          {!isPast && meal.dishes.length > 0 && (
            <button disabled={busy} onClick={() => onSwap(day.date, meal.meal)}>
              Swap
            </button>
          )}
        </>
      )}
    </div>
  </div>
);

/** One day: calories planned against the target, what was eaten, and its meals. */
const DayCard = ({ day, busy, onSwap, onOrder }) => {
  const today = todayIso();
  const isPast = day.date < today;
  const fill =
    day.target > 0 ? Math.min(100, Math.round((100 * day.plannedCalories) / day.target)) : 0;
  return (
    <div className={`mealplan-day ${day.date === today ? 'today' : ''}`} data-testid="plan-day">
      <h3>
        {day.dayName}
        <small>
          {shortDate(day.date)}
          {day.date === today ? ' · today' : ''}
        </small>
      </h3>
      <div className="mealplan-bar" title={`${fill}% of target`}>
        <div className={day.onTarget ? 'hit' : ''} style={{ width: `${fill}%` }} />
      </div>
      <div className="mealplan-daytotals">
        <span>
          {day.plannedCalories} / {day.target} kcal {day.onTarget ? '✓' : ''}
        </span>
        <span>${day.plannedCost}</span>
      </div>
      {day.eatenCalories != null && (
        <div className="mealplan-daytotals">
          <span>
            Eaten {isPast ? '' : 'so far'}: {day.eatenCalories} kcal
          </span>
        </div>
      )}
      {day.meals.map((meal) => (
        <MealRow
          key={meal.meal}
          day={day}
          meal={meal}
          isPast={isPast}
          busy={busy}
          onSwap={onSwap}
          onOrder={onOrder}
        />
      ))}
    </div>
  );
};

const MealPlan = () => {
  const navigate = useNavigate();
  const [plan, setPlan] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [needsGoal, setNeedsGoal] = useState(false);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    getMealPlan()
      .then((current) => setPlan(current))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const run = useCallback(async (action) => {
    setBusy(true);
    setError('');
    setNeedsGoal(false);
    try {
      const result = await action();
      setPlan(result);
      setShowForm(false);
    } catch (e) {
      setError(e.message);
      setNeedsGoal(/calorie goal/i.test(e.message));
    } finally {
      setBusy(false);
    }
  }, []);

  const handleCreate = (options) => run(() => createMealPlan(options));
  const handleSwap = (date, meal) => run(() => swapMeal(date, meal));
  const handleOrder = (date, meal) => run(() => orderMeal(date, meal));
  const handleDelete = () =>
    run(async () => {
      await deleteMealPlan();
      return null;
    });

  if (loading) {
    return (
      <div className="recommendations-container">
        <div className="loading">Loading...</div>
      </div>
    );
  }

  return (
    <div className="recommendations-container">
      <div className="recommendations-header">
        <h1 className="recommendations-title">📅 Meal Plan</h1>
        <div className="header-actions">
          <button className="nav-button" onClick={() => navigate('/personal-diet')}>
            Personal Diet
          </button>
          {plan && !showForm && (
            <button className="nav-button" onClick={() => setShowForm(true)}>
              New plan
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="mealplan-banner error" role="alert">
          {error}{' '}
          {needsGoal && (
            <button className="nav-button" onClick={() => navigate('/personal-diet')}>
              Set my goal
            </button>
          )}
        </div>
      )}

      {(!plan || showForm) && (
        <div className="mealplan-card">
          <h2>{plan ? 'Start a new plan' : 'Plan your week'}</h2>
          <p style={{ color: '#5b6573' }}>
            We plan breakfast, lunch and dinner from the menu so each day lands near your Personal
            Diet target (your calorie goal plus calories burned). Dishes with your allergens are
            never planned, and each main dish appears at most twice.
          </p>
          <PlanForm
            busy={busy}
            onSubmit={handleCreate}
            onCancel={plan ? () => setShowForm(false) : null}
          />
        </div>
      )}

      {plan && (
        <>
          <div className="mealplan-card">
            <div className="mealplan-summary">
              <div className="mealplan-stat">
                <strong>{plan.dailyTarget} kcal</strong>
                <span>
                  daily target ({plan.dailyGoal} goal + {plan.dailyBurn} burned)
                </span>
              </div>
              <div className="mealplan-stat">
                <strong>
                  {plan.daysOnTarget} / {plan.days.length}
                </strong>
                <span>days within 10% of target</span>
              </div>
              <div className="mealplan-stat">
                <strong>${plan.totalCost}</strong>
                <span>
                  {plan.weeklyBudget != null
                    ? `of your $${plan.weeklyBudget} limit`
                    : 'no limit set'}
                </span>
              </div>
            </div>
            {plan.understood && (
              <div className="mealplan-banner">
                Your request: {plan.understood}
                {AI_LABEL[plan.aiStatus] && <em> — {AI_LABEL[plan.aiStatus]}</em>}
              </div>
            )}
            {plan.warnings.length > 0 && (
              <div className="mealplan-banner warn">
                Heads up:
                <ul>
                  {plan.warnings.map((w) => (
                    <li key={w}>{w}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          <div className="mealplan-days">
            {plan.days.map((day) => (
              <DayCard
                key={day.date}
                day={day}
                busy={busy}
                onSwap={handleSwap}
                onOrder={handleOrder}
              />
            ))}
          </div>

          <div style={{ marginTop: 20 }}>
            <button className="logout-button" disabled={busy} onClick={handleDelete}>
              Clear plan
            </button>
          </div>
        </>
      )}
    </div>
  );
};

export default MealPlan;
