interface Props {
  className?: string;
  style?: React.CSSProperties;
}

export default function HourglassIcon({ className, style }: Props) {
  return (
    <svg
      className={className}
      width="1em"
      height="1em"
      viewBox="0 0 24 24"
      style={style}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 3h14v2l-6 7 6 7v2H5v-2l6-7-6-7V3z" />
      <line x1="5" y1="3" x2="19" y2="3" />
      <line x1="5" y1="21" x2="19" y2="21" />
    </svg>
  );
}
