interface Props {
  className?: string;
  style?: React.CSSProperties;
}

export default function FlameIcon({ className, style }: Props) {
  return (
    <svg
      className={className}
      style={style}
      width="1em"
      height="1em"
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
    >
      <path
        d="M12 2C8 6 8 10 8 12a4 4 0 008 0c0-2 0-6-4-10z"
        opacity=".85"
      />
      <path
        d="M10 18c-1.5 0-3-1-3-2.5S9 12 10.5 11c-1 1.5-1 3 0 4 .5.5 1 1 1.5 1s1-.5 1.5-1c1-1 1-2.5 0-4 1 1 1.5 2.5 1.5 4 0 1.5-1.5 3-3 3z"
        opacity=".6"
      />
    </svg>
  );
}
