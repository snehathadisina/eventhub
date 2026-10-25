export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;

  return (
    <nav className="pagination" aria-label="Pagination">
      <button
        type="button"
        className="btn btn-outline"
        onClick={() => onChange(page - 1)}
        disabled={page <= 0}
      >
        Previous
      </button>
      <span className="muted">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="btn btn-outline"
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages - 1}
      >
        Next
      </button>
    </nav>
  );
}