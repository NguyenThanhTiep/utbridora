import { useWorkflowCommand } from '../proposals/useWorkflowCommand';

export function useCommand(org: string, changed: () => void) {
  const command = useWorkflowCommand<unknown>(org, () => {
    changed();
    window.dispatchEvent(new Event('cooperation-data-updated'));
  }, () => {});
  return {
    busy: command.blocked, retrying: command.busy, message: command.message,
    retryable: command.retryable, retry: command.retry,
    command: (path: string, method: 'POST' | 'PATCH' | 'PUT', data: object) => command.submit(path, method, data),
  };
}
