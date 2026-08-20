import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'

export function Modal({ title, children, className = '', onClose }: { title: string; children: ReactNode; className?: string; onClose: () => void }) {
  const titleId = useId()
  const dialogRef = useRef<HTMLElement>(null)
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const dialog = dialogRef.current
    const focusableSelector = 'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    const initialFocus = dialog?.querySelector<HTMLElement>('[data-autofocus]')
      ?? dialog?.querySelector<HTMLElement>(focusableSelector)
    initialFocus?.focus()

    function handleKeydown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab' || !dialog) return
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(focusableSelector))
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus()
      }
    }
    window.addEventListener('keydown', handleKeydown)
    return () => {
      window.removeEventListener('keydown', handleKeydown)
      previouslyFocused?.focus()
    }
  }, [])

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section ref={dialogRef} className={`modal ${className}`.trim()} role="dialog" aria-modal="true" aria-labelledby={titleId} onMouseDown={(event) => event.stopPropagation()}>
        <header className="modal__header">
          <h2 id={titleId}>{title}</h2>
          <button className="icon-button" type="button" onClick={onClose} title="关闭" aria-label="关闭">
            <X size={18} />
          </button>
        </header>
        <div className="modal__body">{children}</div>
      </section>
    </div>
  )
}
