export function BrandMark({ className = '' }: { className?: string }) {
  return <svg
    className={className}
    viewBox="0 0 48 48"
    role="img"
    aria-label="toesa"
  >
    <path d="M15 8h17a5 5 0 0 1 5 5v22a5 5 0 0 1-5 5H15" />
    <path d="m19 17 4 4 8-9" />
    <path d="M9 30h22" />
    <path d="m26 25 5 5-5 5" />
  </svg>;
}
