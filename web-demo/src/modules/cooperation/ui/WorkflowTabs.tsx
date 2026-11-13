import { useId, type ReactNode } from 'react';

export function WorkflowTabs<T extends string>({ label, value, options, change, children }: {
  label: string; value: T; options: readonly { value: T; label: string }[]; change: (value: T) => void; children: ReactNode;
}) {
  const id = useId();
  return <div className="portal-tabbed-view">
    <div role="tablist" aria-label={label} className="portal-tabs">{options.map((option, index) => <button type="button" role="tab"
      key={option.value} id={id + option.value} aria-selected={value === option.value} aria-controls={id + '-panel'}
      tabIndex={value === option.value ? 0 : -1} onClick={() => change(option.value)} onKeyDown={event => {
        const next = event.key === 'ArrowRight' ? (index + 1) % options.length : event.key === 'ArrowLeft'
          ? (index + options.length - 1) % options.length : event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1 : -1;
        if (next < 0) return;
        event.preventDefault(); change(options[next].value);
        document.getElementById(id + options[next].value)?.focus();
      }}>{option.label}</button>)}</div>
    <section role="tabpanel" id={id + '-panel'} aria-labelledby={id + value}>{children}</section>
  </div>;
}
