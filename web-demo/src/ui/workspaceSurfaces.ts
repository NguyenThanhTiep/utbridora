/** Shared frame targets; decorative effects never change form or navigation state. */
export const workspaceFrames = [
  '.panel', '.portal-context-bar', '.portal-welcome', '.portal-subpage-sheet', '.school-section-card',
  '.portal-workspace.is-school .portal-page-header:not(.is-overview)', '.workflow-detail-drawer',
  '.internship-group-card', '.stat-card', '.account-membership', '.t3-task-card', '.taxonomy-version',
].join(', ');

export const portalSpotlightTargets = [
  'button.portal-stat:not(:disabled)', 'button.portal-shortcut:not(:disabled)', 'button.kpi-card:not(:disabled)',
  '.matching-counts > button:not(:disabled)', '.t5-detail-card', '.matching-candidate', '.proposal-card', '.placement-item-card',
  workspaceFrames,
].join(', ');

export const studentSpotlightTargets = [
  workspaceFrames, '.student-home-stat', '.student-home-row', '.student-opp-card',
].join(', ');
