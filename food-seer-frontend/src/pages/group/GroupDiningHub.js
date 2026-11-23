import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createGroupSession, joinGroupSession } from '../../services/groupDiningApi';
import './GroupDiningHub.css';

const GroupDiningHub = () => {
  const navigate = useNavigate();

  const [createName, setCreateName] = useState('');
  const [joinCode, setJoinCode] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [dietaryRestrictions, setDietaryRestrictions] = useState('');
  const [costPreference, setCostPreference] = useState('moderate');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!createName.trim()) return;

    try {
      setLoading(true);
      setError(null);
      const session = await createGroupSession(createName.trim());
      navigate(`/group-dining/${session.sessionCode}`);
    } catch (err) {
      setError(err.message || 'Failed to create dining room.');
    } finally {
      setLoading(false);
    }
  };

  const handleJoin = async (e) => {
    e.preventDefault();
    if (!joinCode.trim()) return;

    try {
      setLoading(true);
      setError(null);
      const code = joinCode.trim().toUpperCase();
      const profile = {
        displayName: displayName.trim() || undefined,
        dietaryRestrictions: dietaryRestrictions.trim() || undefined,
        costPreference: costPreference || undefined,
      };
      await joinGroupSession(code, profile);
      navigate(`/group-dining/${code}`);
    } catch (err) {
      setError(err.message || 'Failed to join dining room.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="group-hub-container">
      <div className="group-hub-header">
        <h1>🍽️ Group Dining Harmonizer</h1>
        <p className="group-hub-subtitle">
          Order together with automated multi-profile allergen safety and itemized split billing.
        </p>
      </div>

      {error && <div className="hub-error-banner">{error}</div>}

      <div className="group-hub-cards">
        <div className="hub-card">
          <h2>🏠 Create a Room</h2>
          <p className="hub-card-desc">
            Start a new collaborative session. You will be the host and can checkout for the group.
          </p>
          <form onSubmit={handleCreate} className="hub-form">
            <div className="form-group">
              <label htmlFor="create-room-name">Room Name</label>
              <input
                id="create-room-name"
                type="text"
                placeholder="e.g. Hunt Library Study Lunch"
                value={createName}
                onChange={(e) => setCreateName(e.target.value)}
                required
              />
            </div>
            <button
              type="submit"
              className="hub-btn hub-btn-primary"
              disabled={loading || !createName.trim()}
            >
              {loading ? 'Creating...' : 'Create Dining Room'}
            </button>
          </form>
        </div>

        <div className="hub-card">
          <h2>🎟️ Join with Room Code</h2>
          <p className="hub-card-desc">
            Enter a 6-character room code from your host. Your dietary restrictions will protect the table.
          </p>
          <form onSubmit={handleJoin} className="hub-form">
            <div className="form-group">
              <label htmlFor="join-code">Room Code</label>
              <input
                id="join-code"
                type="text"
                placeholder="e.g. DEMO26"
                maxLength={10}
                value={joinCode}
                onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="display-name">Your Display Name</label>
              <input
                id="display-name"
                type="text"
                placeholder="e.g. Leo"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label htmlFor="dietary-restrictions">Allergies / Restrictions</label>
              <input
                id="dietary-restrictions"
                type="text"
                placeholder="e.g. Peanuts, Gluten"
                value={dietaryRestrictions}
                onChange={(e) => setDietaryRestrictions(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label htmlFor="cost-preference">Budget Preference</label>
              <select
                id="cost-preference"
                value={costPreference}
                onChange={(e) => setCostPreference(e.target.value)}
              >
                <option value="budget">Budget (under $10)</option>
                <option value="moderate">Moderate ($10 - $20)</option>
                <option value="premium">Premium ($20+)</option>
              </select>
            </div>
            <button
              type="submit"
              className="hub-btn hub-btn-secondary"
              disabled={loading || !joinCode.trim()}
            >
              {loading ? 'Joining...' : 'Join Dining Room'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default GroupDiningHub;
