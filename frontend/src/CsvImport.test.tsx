import { afterEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import CsvImport from './CsvImport';
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const request = { assignorId: 'a', titleCode: 'T-1', faceValue: '10.00', dueDate: '2026-12-15', type: 'DUPLICATA_MERCANTIL', paymentCurrency: 'BRL' };
const row = (line: number) => ({ line, values: ['CED-001', `T-${line}`, 'DUPLICATA_MERCANTIL', '10,00', '15/12/2026', 'BRL'], request: { ...request, titleCode: `T-${line}` }, errors: [] });
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
async function setup(rows: unknown[]) {
  const fetcher = vi.fn(async (url: string, _options?: RequestInit) => url.endsWith('import-preview') ? response({ maxRows: 100, rows }) : response({ id: 'saved', status: 'PENDING' }, 201));
  vi.stubGlobal('fetch', fetcher);
  render(<CsvImport assignors={[]} onRegistered={vi.fn()} />);
  fireEvent.change(screen.getByLabelText('Arquivo CSV'), { target: { files: [new File(['csv'], 'test.csv')] } });
  await act(async () => fireEvent.click(screen.getByText('Gerar prévia')));
  return fetcher;
}
test('requires explicit selection, excludes errors and registers only selected valid rows without settling', async () => {
  const fetcher = await setup([row(2), { ...row(3), request: null, errors: ['Duplicado'] }]);
  expect((screen.getByLabelText('Selecionar linha 2') as HTMLInputElement).checked).toBe(false);
  expect((screen.getByLabelText('Selecionar linha 3') as HTMLInputElement).disabled).toBe(true);
  fireEvent.click(screen.getByText('Selecionar válidas'));
  await act(async () => { fireEvent.click(screen.getByText('Confirmar cadastro de 1 título(s)')); });
  expect(screen.getByText(/Cadastrado como PENDING/)).toBeTruthy();
  expect(fetcher.mock.calls.filter(([url]) => url === '/api/receivables')).toHaveLength(1);
  expect(fetcher.mock.calls.some(([url]) => url.includes('settlements'))).toBe(false);
});
test('continues after duplicate business error but stops on uncertain transport result', async () => {
  const fetcher = await setup([row(2), row(3), row(4)]);
  fetcher.mockImplementation(async (_url, options) => {
    const body = JSON.parse(options?.body as string);
    if (body.titleCode === 'T-2') return response({ message: 'Duplicado' }, 409);
    throw new TypeError('Network failed');
  });
  fireEvent.click(screen.getByText('Selecionar válidas'));
  await act(async () => fireEvent.click(screen.getByText('Confirmar cadastro de 3 título(s)')));
  expect(screen.getByText('Não cadastrado: Duplicado')).toBeTruthy();
  expect(screen.getByText(/Resultado a confirmar:/)).toBeTruthy();
  expect(screen.getByText(/Não processado:/)).toBeTruthy();
  expect(fetcher.mock.calls.filter(([url]) => url === '/api/receivables')).toHaveLength(2);
});
test('changing the file invalidates preview and selection', async () => {
  await setup([row(2)]); fireEvent.click(screen.getByText('Selecionar válidas'));
  fireEvent.change(screen.getByLabelText('Arquivo CSV'), { target: { files: [new File(['new'], 'new.csv')] } });
  expect(screen.queryByText('Confirmar cadastro de 1 título(s)')).toBeNull();
});
