import { afterEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import SettlementPanel, { totals } from './SettlementPanel';
const titles = [1, 2, 3].map(n => ({ id: `id-${n}`, titleCode: `B-${n}`, assignorId: 'a', faceValue: '100.00', dueDate: '2026-12-15', paymentCurrency: n === 2 ? 'USD' : 'BRL' }));
const preview = (index: number, amount = '90.00') => ({ receivable: titles[index], simulation: { paymentCurrency: titles[index].paymentCurrency, finalAmount: amount, presentValueBrl: '90.00', discountBrl: '10.00' }, expectedConditions: { titleCode: titles[index].titleCode, paymentCurrency: titles[index].paymentCurrency, finalAmount: amount } });
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const success = (index: number) => response({ id: `settled-${index}`, receivableId: titles[index].id, calculation: preview(index).simulation }, 201);
afterEach(() => { cleanup(); localStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
function setup(handler: (options: RequestInit) => Promise<Response>) {
  const fetcher = vi.fn(async (url: string, options: RequestInit) => {
    if (url === '/api/settlements') return handler(options);
    if (url.endsWith('/simulations')) return response(preview(titles.findIndex(t => url.includes(t.id))));
    return response({ content: titles, page: 0, totalPages: 1, size: 20 });
  });
  vi.stubGlobal('fetch', fetcher);
  const view = render(<SettlementPanel assignors={[]} revision={0} onSettled={vi.fn()} />);
  return { ...view, fetcher };
}
async function review() {
  for (const title of titles) fireEvent.click(await screen.findByRole('button', { name: `Lote ${title.titleCode}` }));
  fireEvent.click(screen.getByText('Revisar lote'));
  await screen.findByRole('checkbox', { name: /Autorizo B-3/ });
}
function authorize() { for (const checkbox of screen.getAllByRole('checkbox')) fireEvent.click(checkbox); }

test('selection is explicit and totals use exact cents separated by currency', async () => {
  const handler = vi.fn(async () => success(0)); setup(handler); await review();
  expect(screen.getAllByRole('checkbox').every(e => !(e as HTMLInputElement).checked)).toBe(true);
  expect((screen.getByText('Liquidar selecionados') as HTMLButtonElement).disabled).toBe(true);
  authorize(); expect(screen.getByText(/BRL 180,00 · USD 90,00/)).toBeTruthy(); expect(handler).not.toHaveBeenCalled();
  const amount = { paymentCurrency: 'BRL' as const, finalAmount: '99999999999999999.99', presentValueBrl: '0.00', discountBrl: '0.00' };
  expect(totals([amount, amount])).toBe('BRL 199.999.999.999.999.999,98 · USD 0,00');
});
test('continues after business error and requires explicit changed-condition reconfirmation with a new key', async () => {
  const sent: RequestInit[] = []; let changed = false;
  setup(async options => {
    sent.push(options); const id = JSON.parse(options.body as string).receivableId;
    if (id === 'id-1') return response({ code: 'ALREADY_SETTLED', message: 'Já liquidado' }, 409);
    if (id === 'id-2' && !changed) { changed = true; return response({ code: 'CONDITIONS_CHANGED', current: preview(1, '80.00') }, 409); }
    return success(id === 'id-2' ? 1 : 2);
  });
  await review(); authorize(); fireEvent.click(screen.getByText('Liquidar selecionados'));
  await screen.findByText('B-3 · Liquidado');
  expect(sent).toHaveLength(3); expect(screen.getByText('B-1 · Erro de negócio')).toBeTruthy();
  expect(screen.getByText('Anterior')).toBeTruthy(); expect(screen.getByText('80.00')).toBeTruthy();
  const checkbox = screen.getByRole('checkbox', { name: /Autorizo B-2/ }); expect((checkbox as HTMLInputElement).checked).toBe(false);
  fireEvent.click(checkbox); fireEvent.click(screen.getByText('Reconfirmar selecionados'));
  await screen.findByText('B-2 · Liquidado');
  expect(sent).toHaveLength(4); expect(sent[1].headers).not.toEqual(sent[3].headers);
  expect(JSON.parse(sent[3].body as string).expectedConditions.finalAmount).toBe('80.00');
});
test('technical failure stops later sends and remount recovers only the exact uncertain attempt', async () => {
  const handler = vi.fn(async (options: RequestInit) => {
    const id = JSON.parse(options.body as string).receivableId;
    return id === 'id-1' ? success(0) : response({}, 502);
  });
  const view = setup(handler); await review(); authorize(); fireEvent.click(screen.getByText('Liquidar selecionados'));
  await screen.findByText('B-3 · Não processado'); expect(handler).toHaveBeenCalledTimes(2); expect(localStorage.length).toBe(1);
  expect(screen.getByText('B-1 · Liquidado')).toBeTruthy(); expect(screen.getByText('B-2 · Resultado a confirmar')).toBeTruthy();
  const original = handler.mock.calls[1][0]; view.unmount();
  const replay = vi.fn(async (_options: RequestInit) => success(1)); setup(replay);
  await screen.findByText('Consultar ou concluir tentativa'); expect(replay).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('Consultar ou concluir tentativa')); await screen.findByText(/Liquidação confirmada/);
  expect(replay.mock.calls[0][0].body).toBe(original.body); expect(replay.mock.calls[0][0].headers).toEqual(original.headers); expect(localStorage.length).toBe(0);
});
test('storage failure sends nothing and marks remaining selected titles unprocessed', async () => {
  const handler = vi.fn(async () => success(0)); setup(handler); await review(); authorize();
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota'); });
  fireEvent.click(screen.getByText('Liquidar selecionados'));
  await screen.findByText('B-3 · Não processado'); expect(handler).not.toHaveBeenCalled();
});
test('double click starts only one sequence and unmount prevents later requests', async () => {
  let resolve!: (value: Response) => void;
  const handler = vi.fn(() => new Promise<Response>(done => { resolve = done; }));
  const view = setup(handler); await review(); authorize(); const button = screen.getByText('Liquidar selecionados');
  await act(async () => { fireEvent.click(button); fireEvent.click(button); });
  expect(handler).toHaveBeenCalledOnce(); view.unmount();
  await act(async () => resolve(success(0))); expect(handler).toHaveBeenCalledOnce();
});

test('technical simulation failure stops remaining previews without any settlement request', async () => {
  const handler = vi.fn(async () => success(0)); const { fetcher } = setup(handler);
  const simulations: string[] = [];
  fetcher.mockImplementation(async (url: string) => {
    if (url.endsWith('/simulations')) { simulations.push(url); return url.includes('id-1') ? response(preview(0)) : response({}, 503); }
    return response({ content: titles, page: 0, totalPages: 1, size: 20 });
  });
  for (const title of titles) fireEvent.click(await screen.findByRole('button', { name: `Lote ${title.titleCode}` }));
  fireEvent.click(screen.getByText('Revisar lote'));
  await screen.findByText('B-3 · Não processado');
  expect(simulations).toHaveLength(2); expect(handler).not.toHaveBeenCalled();
  expect(screen.getAllByRole('checkbox')).toHaveLength(1);
});
