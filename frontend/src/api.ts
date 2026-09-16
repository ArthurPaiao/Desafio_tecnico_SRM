export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface Assignor { id: string; code: string; name: string }
export interface Settlement {
  id: string; receivableId: string; titleCode: string; assignorName: string; settledAt: string;
  calculation: { paymentCurrency: 'BRL' | 'USD'; finalAmount: string; presentValueBrl: string; discountBrl: string; [key: string]: string | number | null };
}
export async function get<T>(path: string, signal: AbortSignal): Promise<T> {
  const response = await fetch('/api' + path, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || `Não foi possível consultar a API (HTTP ${response.status}).`);
  }
  return response.json();
}

export class ApiError extends Error {
  constructor(public status: number, public detail: { message?: string; fieldErrors?: { field: string; message: string }[] }) {
    super(detail.message || `A API retornou HTTP ${status}.`);
  }
}
export async function post<T>(path: string, payload: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch('/api' + path, {
    method: 'POST', signal, headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new ApiError(response.status, await response.json().catch(() => ({})));
  return response.json();
}
// Presentation only: never convert financial strings to binary floating point.
export function money(value: string): string {
  const [integer, decimals = '00'] = value.split('.');
  return integer.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + ',' + decimals;
}
