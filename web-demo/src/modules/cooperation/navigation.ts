/** Synchronous, cancelable navigation guard owned by the active demand editor. */
let decision:boolean|undefined;
export function canLeaveDemand(): boolean {
  // Nested menu/notification/router handlers belong to the same user action.
  // Ask once, and keep a denial effective throughout that synchronous action.
  if(decision===undefined){
    decision=window.dispatchEvent(new Event('demand-before-leave', { cancelable: true }));
    queueMicrotask(()=>{decision=undefined;});
  }
  return decision;
}
