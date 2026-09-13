import './ConnectionBanner.css';

interface ConnectionBannerProps { status: 'connecting' | 'connected' | 'reconnecting'; }

export default function ConnectionBanner({ status }: ConnectionBannerProps) {
  if (status === 'connected') return <p className="connection-banner connection-banner--connected" role="status"><span className="connection-banner__dot" aria-hidden="true" /> Live sync stable</p>;
  return <p className="connection-banner connection-banner--attention" role="status"><span className="connection-banner__dot" aria-hidden="true" /> {status === 'connecting' ? 'Connecting to the room...' : 'We lost the room for a second. Reconnecting...'}</p>;
}
