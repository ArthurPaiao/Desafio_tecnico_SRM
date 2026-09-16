import { afterEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import SettlementPanel from './SettlementPanel';

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); localStorage.clear(); });
const title = { id: 'title-1', assignorId: 'assignor', titleCode: 'T-001', faceValue: '100.00', dueDate: '2026-12-15', paymentCurrency: 'BRL' };
const calculation = { paymentCurrency: 'BRL', finalAmount: '92.86', presentValueBrl: '92.86', discountBrl: '7.14' };
const conditions = { titleCode: 'T-001', paymentCurrency: 'BRL', finalAmount: '92.86' };
const preview = { receivable: title, simulation: calculation, expectedConditions: conditions };
const result = { id: 'settled-1', receivableId: title.id, calculation };
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
test('opening edit clears authorized preview and requires a new review after save', async () => {
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith('/simulations')) return response(preview);
    if (url === '/api/receivables/title-1') return response({ ...title, type: 'DUPLICATA_MERCANTIL', status: 'PENDING' });
    return response({ content: [title], page: 0, totalPages: 1 });
  });
  vi.stubGlobal('fetch', fetcher);
  render(<SettlementPanel assignors={[]} revision={0} onSettled={vi.fn()} />);
  await review(); fireEvent.click(screen.getByRole('checkbox'));
  await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Editar T-001' })));
  expect(screen.queryByRole('checkbox')).toBeNull();
  expect(screen.queryByText('Confirmar liquidação')).toBeNull();
  await act(async () => fireEvent.click(screen.getByText('Salvar alterações')));
  expect(screen.getByText(/Título atualizado. Clique em Revisar/)).toBeTruthy();
  expect(screen.queryByText('Confirmar liquidação')).toBeNull();
  expect(fetcher.mock.calls.some(([url]) => url.includes('settlements'))).toBe(false);
});
function setup(settle: (options: RequestInit) => Promise<Response>) {
  const fetcher = vi.fn((url: string, options: RequestInit) => {
    if (url === '/api/settlements') return settle(options);
    if (url.endsWith('/simulations')) return Promise.resolve(response(preview));
    return Promise.resolve(response({ content: [title], page: 0, totalPages: 1, totalElements: 1, size: 20 }));
  });
  vi.stubGlobal('fetch', fetcher);
  const onSettled = vi.fn();
  const view = render(<SettlementPanel assignors={[]} revision={0} onSettled={onSettled} />);
  return { fetcher, onSettled, ...view };
}
async function review() {
  fireEvent.click(await screen.findByRole('button', { name: 'Revisar T-001' }));
  await screen.findByRole('checkbox');
}
async function confirm() {
  await review();
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar liquidação' }));
}
test('requires explicit review, persists before sending, and clears on success', async () => {
  const { fetcher, onSettled } = setup(async options => {
    expect(localStorage.getItem('srm.settlement.v1.' + (options.headers as Record<string, string>)['Idempotency-Key'])).toBe(options.body);
    return response(result, 201);
  });
  await review();
  expect((screen.getByRole('button', { name: 'Confirmar liquidação' }) as HTMLButtonElement).disabled).toBe(true);
  expect(fetcher.mock.calls.filter(([url]) => url === '/api/settlements')).toHaveLength(0);
  fireEvent.click(screen.getByRole('checkbox'));
  const button = screen.getByRole('button', { name: 'Confirmar liquidação' });
  await act(async () => { fireEvent.click(button); fireEvent.click(button); });
  expect(await screen.findByText(/Liquidação confirmada: settled-1/)).toBeTruthy();
  expect(fetcher.mock.calls.filter(([url]) => url === '/api/settlements')).toHaveLength(1);
  expect(localStorage.length).toBe(0); expect(onSettled).toHaveBeenCalledOnce();
});
test('changed conditions require a second unchecked confirmation and a new key', async () => {
  let calls = 0;
  const { fetcher } = setup(async () => ++calls === 1 ? response({ code: 'CONDITIONS_CHANGED', current: { ...preview, expectedConditions: { ...conditions, finalAmount: '90.00' }, simulation: { ...calculation, finalAmount: '90.00' } } }, 409) : response(result));
  await confirm();
  const button = await screen.findByRole('button', { name: 'Reconfirmar liquidação' });
  expect((button as HTMLButtonElement).disabled).toBe(true); expect(calls).toBe(1);
  fireEvent.click(screen.getByRole('checkbox')); fireEvent.click(button);
  await screen.findByText(/Liquidação confirmada/);
  const requests = fetcher.mock.calls.filter(([url]) => url === '/api/settlements').map(([, options]) => options);
  expect(requests[0].headers).not.toEqual(requests[1].headers);
  expect(JSON.parse(requests[1].body as string).expectedConditions.finalAmount).toBe('90.00');
});
test('network failure survives remount without automatic retry and reuses exact body and key', async () => {
  let calls = 0;
  const handler = vi.fn(async (_options: RequestInit) => { if (++calls === 1) throw new TypeError('network'); return response(result); });
  const view = setup(handler); await confirm();
  await screen.findByText(/a resposta não foi recebida/);
  const first = handler.mock.calls[0][0]; view.unmount();
  setup(handler);
  await screen.findByRole('button', { name: 'Consultar ou concluir tentativa' });
  expect(calls).toBe(1);
  fireEvent.click(screen.getByRole('button', { name: 'Consultar ou concluir tentativa' }));
  await screen.findByText(/Liquidação confirmada/);
  expect(handler.mock.calls[1][0].body).toBe(first.body);
  expect(handler.mock.calls[1][0].headers).toEqual(first.headers);
});
test.each([500, 502, 409])('preserves uncertain response HTTP %s for manual replay', async status => {
  setup(async () => response({ code: 'OPERATION_IN_PROGRESS' }, status)); await confirm();
  await screen.findByText(/Resultado a confirmar ou operação/);
  expect(localStorage.length).toBe(1);
  expect(screen.getByRole('button', { name: 'Consultar ou concluir tentativa' })).toBeTruthy();
});
test('expired quotation blocks settlement and requires a fresh simulation', async () => {
  setup(async () => response({ code: 'EXCHANGE_RATE_EXPIRED', message: 'Cotação expirada' }, 422)); await confirm();
  await screen.findByText('Cotação expirada');
  expect(localStorage.length).toBe(0); expect(screen.queryByRole('checkbox')).toBeNull();
});
test('storage failure prevents any settlement request', async () => {
  const handler = vi.fn(async () => response(result)); setup(handler); await review();
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota'); });
  fireEvent.click(screen.getByRole('checkbox')); fireEvent.click(screen.getByRole('button', { name: 'Confirmar liquidação' }));
  await screen.findByText(/Nenhum pedido foi enviado/); expect(handler).not.toHaveBeenCalled();
});
test('obsolete simulations cannot replace the last selection', async () => {
  let resolve!: (value: Response) => void; let calls = 0;
  setup(async () => response(result));
  const fetcher = vi.mocked(fetch);
  fetcher.mockImplementation((url) => {
    if (String(url).endsWith('/simulations')) return ++calls === 1 ? new Promise(done => { resolve = done; }) : Promise.resolve(response({ ...preview, expectedConditions: { ...conditions, finalAmount: '88.00' } }));
    return Promise.resolve(response({ content: [title], page: 0, totalPages: 1 }));
  });
  const button = await screen.findByRole('button', { name: 'Revisar T-001' });
  fireEvent.click(button); fireEvent.click(button);
  await screen.findByRole('checkbox');
  await act(async () => resolve(response(preview)));
  await waitFor(() => expect(screen.getByText('88.00')).toBeTruthy());
});
