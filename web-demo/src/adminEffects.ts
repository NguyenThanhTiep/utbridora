import { useSurfaceSpotlight } from './ui/useSurfaceSpotlight';
import { workspaceFrames } from './ui/workspaceSurfaces';

// Pointer feedback is decorative: no React renders or persistent user data.
export function useAdminSpotlight(enabled: boolean) {
  return useSurfaceSpotlight(
    enabled,
    'button.admin-metric:not(:disabled), button.admin-shortcut:not(:disabled), .admin-welcome, ' + workspaceFrames,
    'admin',
  );
}
