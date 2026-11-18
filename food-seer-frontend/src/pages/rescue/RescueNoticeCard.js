import React from 'react';
import { formatDateTime } from '../../components/allergyOptions';

// A surplus rescue deal shown in the notices list. Kept visually distinct from
// allergy safety notices so a deal is never mistaken for a warning.
const RescueNoticeCard = ({ notice, onAcknowledge, onOpen }) => (
  <div className={`trace-card rescue-notice ${notice.acknowledged ? '' : 'trace-unread'}`}>
    <div className="order-header">
      <h3>♻️ Rescue deal</h3>
      <span className={`status-badge ${notice.acknowledged ? 'fulfilled' : 'pending'}`}>
        {notice.acknowledged ? '✓ Read' : 'New deal'}
      </span>
    </div>
    <p>{notice.message}</p>
    <p className="trace-muted">Sent {formatDateTime(notice.createdAt)}</p>
    <div className="header-actions">
      <button className="create-button" onClick={onOpen}>
        See rescue deals
      </button>
      {!notice.acknowledged && (
        <button className="back-button" onClick={() => onAcknowledge(notice.id)}>
          Dismiss
        </button>
      )}
    </div>
  </div>
);

export default RescueNoticeCard;
