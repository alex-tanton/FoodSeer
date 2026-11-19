import React, { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  getGroupSession,
  getHarmonizedMenu,
  addCartItem,
  removeCartItem,
  getSplitBill,
  checkoutGroupSession,
} from '../../services/groupDiningApi';
import './GroupRoom.css';

const GroupRoom = () => {
  const { sessionCode } = useParams();

  const [session, setSession] = useState(null);
  const [menu, setMenu] = useState(null);
  const [splitBill, setSplitBill] = useState(null);
  const [activeTab, setActiveTab] = useState('menu'); // 'menu' | 'cart'
  const [menuFilter, setMenuFilter] = useState('all'); // 'all' | 'safe' | 'caution'
  const [quantities, setQuantities] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionMessage, setActionMessage] = useState(null);
  const [checkingOut, setCheckingOut] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const [sessionData, menuData, billData] = await Promise.all([
        getGroupSession(sessionCode),
        getHarmonizedMenu(sessionCode),
        getSplitBill(sessionCode),
      ]);
      setSession(sessionData);
      setMenu(menuData);
      setSplitBill(billData);
    } catch (err) {
      setError(err.message || 'Failed to load group room data.');
    } finally {
      setLoading(false);
    }
  }, [sessionCode]);

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 5000); // 5-second polling for collaborative updates
    return () => clearInterval(interval);
  }, [loadData]);

  const handleAdd = async (foodId) => {
    try {
      const qty = quantities[foodId] || 1;
      await addCartItem(sessionCode, { foodId, quantity: qty });
      setActionMessage('Item added to your tray!');
      setTimeout(() => setActionMessage(null), 3000);
      loadData();
    } catch (err) {
      setError(err.message || 'Failed to add item to tray.');
    }
  };

  const handleRemove = async (itemId) => {
    try {
      await removeCartItem(sessionCode, itemId);
      loadData();
    } catch (err) {
      setError(err.message || 'Failed to remove item.');
    }
  };

  const handleCheckout = async () => {
    if (!window.confirm('Are you sure you want to submit the group order for preparation?')) {
      return;
    }
    try {
      setCheckingOut(true);
      setError(null);
      const res = await checkoutGroupSession(sessionCode);
      setActionMessage(`Order #${res.orderId} submitted successfully!`);
      loadData();
    } catch (err) {
      setError(err.message || 'Checkout failed.');
    } finally {
      setCheckingOut(false);
    }
  };

  if (loading && !session) {
    return <div className="group-room-container"><p>Loading group room...</p></div>;
  }

  if (error && !session) {
    return (
      <div className="group-room-container">
        <div className="hub-error-banner">{error}</div>
      </div>
    );
  }

  const isSubmitted = session && session.status === 'SUBMITTED';

  // Filter menu items
  const filteredDishes = (menu?.dishes || []).filter((dish) => {
    if (menuFilter === 'safe') return dish.isUniversallySafe;
    if (menuFilter === 'caution') return !dish.isUniversallySafe;
    return true;
  });

  return (
    <div className="group-room-container">
      {actionMessage && <div className="order-submitted-banner">{actionMessage}</div>}
      {error && <div className="hub-error-banner">{error}</div>}

      {/* Header */}
      <div className="room-header">
        <div className="room-title-bar">
          <div>
            <h1>{session?.sessionName}</h1>
            <span style={{ color: '#64748b', fontSize: '0.9rem' }}>
              Host: <strong>{session?.hostUsername}</strong> | Status: <strong>{session?.status}</strong>
            </span>
          </div>
          <div className="room-code-badge">
            <span>CODE: {session?.sessionCode}</span>
            <button
              onClick={() => {
                navigator.clipboard.writeText(session?.sessionCode);
                alert('Room code copied to clipboard!');
              }}
            >
              📋 Copy
            </button>
          </div>
        </div>

        <div className="room-members-bar">
          <strong style={{ fontSize: '0.9rem', color: '#475569' }}>Seated Members:</strong>
          {session?.members.map((m) => (
            <div key={m.id} className="member-chip">
              <span>👤 {m.displayName}</span>
              {m.dietaryRestrictions && (
                <span className="diet-tag">{m.dietaryRestrictions}</span>
              )}
            </div>
          ))}
        </div>
      </div>

      {isSubmitted && (
        <div className="order-submitted-banner">
          🎉 This group order has been finalized and submitted to the kitchen (Order #{session?.orderId})!
        </div>
      )}

      {/* Navigation tabs */}
      <div className="room-nav-tabs">
        <button
          className={`room-tab-btn ${activeTab === 'menu' ? 'active' : ''}`}
          onClick={() => setActiveTab('menu')}
        >
          📜 Harmonized Menu ({menu?.dishes.length || 0})
        </button>
        <button
          className={`room-tab-btn ${activeTab === 'cart' ? 'active' : ''}`}
          onClick={() => setActiveTab('cart')}
        >
          🛒 Shared Cart & Split Bill ({session?.cartItems.length || 0} items)
        </button>
      </div>

      {/* Menu View */}
      {activeTab === 'menu' && (
        <div>
          <div className="menu-filter-bar">
            <button
              className={`filter-chip ${menuFilter === 'all' ? 'active' : ''}`}
              onClick={() => setMenuFilter('all')}
            >
              All Items ({menu?.dishes.length || 0})
            </button>
            <button
              className={`filter-chip ${menuFilter === 'safe' ? 'active' : ''}`}
              onClick={() => setMenuFilter('safe')}
            >
              ✅ Universally Safe for All ({menu?.safeDishCount || 0})
            </button>
            <button
              className={`filter-chip ${menuFilter === 'caution' ? 'active' : ''}`}
              onClick={() => setMenuFilter('caution')}
            >
              ⚠️ Needs Caution ({(menu?.dishes.length || 0) - (menu?.safeDishCount || 0)})
            </button>
          </div>

          <div className="dish-grid">
            {filteredDishes.map((dish) => (
              <div key={dish.foodId} className="dish-card">
                <div>
                  <div className="dish-header">
                    <span className="dish-title">{dish.foodName}</span>
                    <span className="dish-price">${dish.price}.00</span>
                  </div>
                  <div className="dish-meta">
                    {dish.calories ? `${dish.calories} cal | ` : ''}
                    Tags: {dish.allergies?.length ? dish.allergies.join(', ') : 'None declared'}
                  </div>

                  {dish.isUniversallySafe ? (
                    <div className="dish-safety-banner safety-safe">
                      ✅ Safe for all {menu?.totalMembers} members
                    </div>
                  ) : (
                    <div className="dish-safety-banner safety-caution">
                      ⚠️ Allergen Conflict:
                      {dish.memberConflicts.map((c, i) => (
                        <div key={i} style={{ marginTop: '2px', fontWeight: 500 }}>
                          • {c.memberDisplayName}: {c.conflictReason}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {!isSubmitted && (
                  <div className="dish-actions">
                    <select
                      value={quantities[dish.foodId] || 1}
                      onChange={(e) =>
                        setQuantities({ ...quantities, [dish.foodId]: parseInt(e.target.value, 10) })
                      }
                    >
                      {[1, 2, 3, 4, 5].map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                    <button
                      className="add-tray-btn"
                      onClick={() => handleAdd(dish.foodId)}
                    >
                      + Add to Tray
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Cart & Split Bill View */}
      {activeTab === 'cart' && (
        <div className="shared-cart-panel">
          {/* Cross Contamination Banner */}
          {splitBill?.contaminationAlerts && splitBill.contaminationAlerts.length > 0 && (
            <div className="contamination-banner">
              <h3>⚠️ Table Cross-Contamination Alert</h3>
              <ul>
                {splitBill.contaminationAlerts.map((alert, idx) => (
                  <li key={idx}>
                    <strong>{alert.sourceDishName}</strong> (added by {alert.orderedByDisplayName})
                    contains <strong>{alert.allergen}</strong>, which conflicts with{' '}
                    <strong>{alert.affectedMemberDisplayName}'s</strong> allergy.
                    <em> {alert.recommendation}</em>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Member Trays */}
          <div className="tray-section">
            <h2>Individual Trays</h2>
            {session?.members.map((member) => {
              const memberItems = (session.cartItems || []).filter(
                (item) => item.memberId === member.id
              );
              return (
                <div key={member.id} className="member-tray">
                  <div className="member-tray-header">
                    <span>🍽️ {member.displayName}'s Tray</span>
                    <span>{memberItems.length} items</span>
                  </div>
                  {memberItems.length === 0 ? (
                    <p style={{ color: '#94a3b8', margin: '4px 0', fontSize: '0.9rem' }}>
                      Tray is currently empty.
                    </p>
                  ) : (
                    memberItems.map((item) => (
                      <div key={item.id} className="cart-item-row">
                        <span>
                          {item.quantity}x {item.foodName} (${item.price * item.quantity}.00)
                        </span>
                        {!isSubmitted && (
                          <button
                            className="remove-item-btn"
                            onClick={() => handleRemove(item.id)}
                          >
                            ✕ Remove
                          </button>
                        )}
                      </div>
                    ))
                  )}
                </div>
              );
            })}
          </div>

          {/* Split Bill Table */}
          <h2>Split Bill Breakdown</h2>
          <table className="split-table">
            <thead>
              <tr>
                <th>Member</th>
                <th>Dishes</th>
                <th>Items Subtotal</th>
                <th>Tax & Fees (8%)</th>
                <th>Total Owed</th>
              </tr>
            </thead>
            <tbody>
              {splitBill?.memberShares.map((share, idx) => (
                <tr key={idx}>
                  <td><strong>{share.memberDisplayName}</strong></td>
                  <td>{share.itemCount}</td>
                  <td>${share.itemsSubtotal.toFixed(2)}</td>
                  <td>${share.taxShare.toFixed(2)}</td>
                  <td><strong>${share.totalOwed.toFixed(2)}</strong></td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Checkout Bar */}
          <div className="checkout-bar">
            <div>
              <span style={{ fontSize: '1.2rem', color: '#334155' }}>
                Group Order Total:{' '}
                <strong style={{ color: '#059669', fontSize: '1.4rem' }}>
                  ${splitBill?.totalAmount ? splitBill.totalAmount.toFixed(2) : '0.00'}
                </strong>
              </span>
            </div>
            {!isSubmitted && (
              <button
                className="checkout-btn"
                onClick={handleCheckout}
                disabled={checkingOut || session?.cartItems.length === 0}
              >
                {checkingOut ? 'Submitting Order...' : 'Submit Group Order'}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default GroupRoom;
