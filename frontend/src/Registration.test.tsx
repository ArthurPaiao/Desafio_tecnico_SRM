import { afterEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import Registration, { validDraft } from './Registration';

afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); });
const calc = { finalAmount: '92.86', presentValueBrl: '92.86', discountBrl: '7.14', paymentCurrency: 'BRL', termMonths: 3, referenceDate: '2026-09-15', effectiveRate: '0.025', exchangeRateUsed: null };
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
function setup() {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-09-15T12:00:00Z'));
  render(<Registration assignors={[{ id: 'assignor', code: 'CED-001', name: 'Teste' }]} />);
  fireEvent.change(screen.getByLabelText('Cedente do título'), { target: { value: 'assignor' } });
  fireEvent.change(screen.getByLabelText('Código do título'), { target: { value: 'T-001' } });
  fireEvent.change(screen.getByLabelText('Valor de face (BRL, sem milhar)'), { target: { value: '100,00' } });
  fireEvent.change(screen.getByLabelText('Vencimento'), { target: { value: '2026-12-15' } });
}
test('debounces for 400 ms and invalidates the old preview immediately', async () => {
  const fetcher = vi.fn(async (_url: string, _options: RequestInit) => response(calc)); vi.stubGlobal('fetch', fetcher); setup();
  await act(() => vi.advanceTimersByTimeAsync(399)); expect(fetcher).not.toHaveBeenCalled();
  await act(() => vi.advanceTimersByTimeAsync(1)); expect(screen.getByText('Prévia · BRL 92,86')).toBeTruthy();
  expect(JSON.parse(fetcher.mock.calls[0]?.[1]?.body as string).faceValue).toBe('100.00');
  fireEvent.change(screen.getByLabelText('Valor de face (BRL, sem milhar)'), { target: { value: '200,00' } });
  expect(screen.queryByText('Prévia · BRL 92,86')).toBeNull();
});
test('rejects invalid amount, impossible date and past business date', () => {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-09-15T12:00:00Z'));
  const draft = { assignorId: 'a', titleCode: 't', faceValue: '1.00', dueDate: '2026-09-15', type: 'DUPLICATA_MERCANTIL', paymentCurrency: 'BRL' };
  expect(validDraft(draft)).toBe(true);
  for (const faceValue of ['0', '1e2', '1.000,00', '1.001', '-1']) expect(validDraft({ ...draft, faceValue })).toBe(false);
  for (const dueDate of ['2026-02-30', '2026-09-14']) expect(validDraft({ ...draft, dueDate })).toBe(false);
});
test('registers as pending without a successful USD simulation and prevents double submission', async () => {
  const fetcher = vi.fn(async (url: string) => url.endsWith('/simulations') ? response({ message: 'Cotação ausente' }, 422) : response({ id: 'new-title' }, 201));
  vi.stubGlobal('fetch', fetcher); setup();
  fireEvent.change(screen.getByLabelText('Moeda para pagamento'), { target: { value: 'USD' } });
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(screen.getByText(/Cotação ausente/)).toBeTruthy();
  const submit = screen.getByRole('button', { name: 'Cadastrar como pendente' });
  await act(async () => { fireEvent.click(submit); fireEvent.click(submit); });
  expect(fetcher.mock.calls.filter(([url]) => url.endsWith('/receivables'))).toHaveLength(1);
  expect(screen.getByText(/ID: new-title/)).toBeTruthy();
  expect(fetcher.mock.calls.some(([url]) => url.includes('/settlements'))).toBe(false);
});
test('discards a simulation response for edited inputs even if abort is ignored', async () => {
  let resolve!: (value: Response) => void;
  vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(done => { resolve = done; }))); setup();
  await act(() => vi.advanceTimersByTimeAsync(400));
  fireEvent.change(screen.getByLabelText('Valor de face (BRL, sem milhar)'), { target: { value: '' } });
  await act(async () => { resolve(response(calc)); });
  expect(screen.queryByText('Prévia · BRL 92,86')).toBeNull();
});
test('renders server field errors without losing the draft', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => response({ message: 'Revise os campos', fieldErrors: [{ field: 'titleCode', message: 'Código inválido' }] }, 400))); setup();
  await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Cadastrar como pendente' })));
  expect(screen.getByText('titleCode: Código inválido')).toBeTruthy();
  expect((screen.getByLabelText('Código do título') as HTMLInputElement).value).toBe('T-001');
});
