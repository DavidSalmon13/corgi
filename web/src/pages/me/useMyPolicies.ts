import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { MyPolicies } from '../../api/types';
import { MY_POLICIES_KEY } from '../../lib/mePaths';

/** GET /api/me/policies: the owner's policies, newest first, and what is due across them (more-policies AC6). */
export function useMyPolicies() {
  return useQuery({ queryKey: MY_POLICIES_KEY, queryFn: () => api<MyPolicies>('/api/me/policies') });
}
