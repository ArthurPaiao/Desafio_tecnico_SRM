import { afterEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import { money } from './api';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const empty = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
function mockApi(settlements: (url: string) => Promise<Response>) {
  const fetcher = vi.fn((url: string) => url.includes('/assignors') || url.includes('/receivables') ? Promise.resolve(response(empty)) : settlements(url));
  vi.stubGlobal('fetch', fetcher);
  return fetcher;
}
test('formats a financial string without losing precision', () => {
  expect(money('99999999999999999.99')).toBe('99.999.999.999.999.999,99');
  expect(money('0.00')).toBe('0,00');
});
test('shows loading then empty state', async () => {
  mockApi(async () => response(empty));
  render(<App />);
  expect(screen.getByRole('status').textContent).toContain('Carregando');
  expect(await screen.findByText('Nenhuma liquidação encontrada para esta consulta.')).toBeTruthy();
});
test('encodes filters and resets pagination', async () => {
  const fetcher = mockApi(async () => response(empty));
  render(<App />);
  await screen.findByText('Nenhuma liquidação encontrada para esta consulta.');
  fireEvent.change(screen.getByLabelText('Início inclusivo (ISO com fuso)'), { target: { value: '2026-09-14T00:00:00+03:00' } });
  fireEvent.change(screen.getByLabelText('Moeda de pagamento'), { target: { value: 'USD' } });
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
  await waitFor(() => expect(fetcher.mock.calls.some(([url]) => url.includes('%2B03%3A00') && url.includes('currency=USD') && url.includes('page=0'))).toBe(true));
});
test('shows API errors and allows manual retry', async () => {
  let fail = true;
  mockApi(async () => response(fail ? { message: 'Período inválido' } : empty, fail ? 400 : 200));
  render(<App />);
  expect(await screen.findByText('Período inválido')).toBeTruthy();
  fail = false; fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
  expect(await screen.findByText('Nenhuma liquidação encontrada para esta consulta.')).toBeTruthy();
});
test('ignores obsolete responses even when transport does not honor abort', async () => {
  let resolveOld!: (value: Response) => void;
  let calls = 0;
  mockApi(() => ++calls === 1 ? new Promise(resolve => { resolveOld = resolve; }) : Promise.resolve(response(empty)));
  render(<App />);
  fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
  await screen.findByText('Nenhuma liquidação encontrada para esta consulta.');
  resolveOld(response({ ...empty, totalElements: 99 }));
  await new Promise(resolve => setTimeout(resolve, 20));
  expect(screen.queryByText('99 registro(s)')).toBeNull();
});

test('displays a persisted snapshot and navigates to the next page', async () => {
  mockApi(async url => {
    const page = Number(new URLSearchParams(url.split('?')[1]).get('page'));
    return response({ ...empty, page, totalElements: 21, totalPages: 2, content: [{
      id: 'snapshot-' + page, receivableId: 'title', titleCode: 'TIT-' + page,
      assignorName: 'Cedente de teste', settledAt: '2026-09-15T03:00:00Z',
      calculation: { paymentCurrency: 'USD', finalAmount: '18571.99', presentValueBrl: '92859.94', discountBrl: '7140.06' },
    }] });
  });
  render(<App />);
  expect(await screen.findByText('USD 18.571,99')).toBeTruthy();
  expect((screen.getByRole('button', { name: 'Anterior' }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: 'Próxima' }));
  expect(await screen.findByText('TIT-1')).toBeTruthy();
  expect((screen.getByRole('button', { name: 'Próxima' }) as HTMLButtonElement).disabled).toBe(true);
});
