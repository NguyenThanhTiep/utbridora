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

export const internshipSpotlightTargets = '.app-page-stage :is(' + [
  '.panel', '.t3-kpis > div', '.hr-kpi-card', '.hr-cohort-choice', '.internship-group-card',
  '.t3-operation-card', '.t3-grade-row', '.t3-task-card', '.t3-month-grid button.text-button',
  '.t3-priorities > li', '.t3-progress-row', '.t3-group-banner',
].join(', ') + '):not(:disabled)';

export const studentSpotlightTargets = [
  workspaceFrames, '.student-home-stat', '.student-home-row', '.student-opp-card',
].join(', ');
