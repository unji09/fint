'use client';

import { useCallback, useState } from 'react';
import { fetchWithAuth } from '@/hooks/useAuth';

// ─── 타입 ────────────────────────────────────────────────────────────────────

export interface ContactCreateRequest {
  accountId: number;
  name: string;
  title?: string;
  phone?: string;
  email?: string;
  personality?: string;
}

export interface ContactUpdateRequest {
  name?: string;
  title?: string;
  phone?: string;
  email?: string;
  personality?: string;
}

// ─── 담당자 생성 ─────────────────────────────────────────────────────────────

export function useCreateContact() {
  const [loading, setLoading] = useState(false);

  const create = useCallback(async (req: ContactCreateRequest) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth('/contacts', {
        method: 'POST',
        body: JSON.stringify(req),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      return json.data as { contactId: number };
    } finally {
      setLoading(false);
    }
  }, []);

  return { create, loading };
}

// ─── 담당자 수정 ─────────────────────────────────────────────────────────────

export function useUpdateContact() {
  const [loading, setLoading] = useState(false);

  const update = useCallback(async (contactId: number, req: ContactUpdateRequest) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth(`/contacts/${contactId}`, {
        method: 'PATCH',
        body: JSON.stringify(req),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return true;
    } finally {
      setLoading(false);
    }
  }, []);

  return { update, loading };
}

// ─── 담당자 삭제 ─────────────────────────────────────────────────────────────

export function useDeleteContact() {
  const [loading, setLoading] = useState(false);

  const remove = useCallback(async (contactId: number) => {
    setLoading(true);
    try {
      const res = await fetchWithAuth(`/contacts/${contactId}`, {
        method: 'DELETE',
      });
      if (!res.ok && res.status !== 204) throw new Error(`HTTP ${res.status}`);
      return true;
    } catch {
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { remove, loading };
}
