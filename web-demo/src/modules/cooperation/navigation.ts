/** Synchronous, cancelable navigation guard owned by the active demand editor. */
export function canLeaveDemand(): boolean {
  return window.dispatchEvent(new Event('demand-before-leave', { cancelable: true }));
}
