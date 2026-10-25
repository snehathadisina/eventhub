import { useEffect, useRef } from 'react';

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel,
  busy,
  onConfirm,
  onCancel,
}) {
  const ref = useRef(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  const handleCancel = (e) => {
    e.preventDefault();
    if (!busy) onCancel();
  };

  const handleBackdropClick = (e) => {
    if (e.target === ref.current && !busy) onCancel();
  };

  return (
    <dialog
      ref={ref}
      className="confirm-dialog"
      onCancel={handleCancel}
      onClick={handleBackdropClick}
    >
      <div className="confirm-body">
        <h2>{title}</h2>
        <p className="muted">{message}</p>
        <div className="confirm-actions">
          <button type="button" className="btn btn-outline" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          <button type="button" className="btn btn-danger" onClick={onConfirm} disabled={busy}>
            {busy ? 'Working...' : confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  );
}