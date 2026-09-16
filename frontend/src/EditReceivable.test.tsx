import { afterEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import EditReceivable from './EditReceivable';
const title = { id: 't', assignorId: 'a', titleCode: 'FIXED', faceValue: '100.00', type: 'DUPLICATA_MERCANTIL', dueDate: '2026-12-15', paymentCurrency: 'BRL' };
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
test('sends only editable fields, normalizes decimal and prevents double submit', async () => {
  const fetcher = vi.fn(async () => new Response(JSON.stringify({ id: 't', status: 'PENDING' })));
  vi.stubGlobal('fetch', fetcher); const saved = vi.fn();
  render(<EditReceivable title={title} onBusy={vi.fn()} onClose={vi.fn()} onSaved={saved} />);
  fireEvent.change(screen.getByLabelText('Editar valor de face'), { target: { value: '200,50' } });
  const button = screen.getByText('Salvar alterações');
  await act(async () => { fireEvent.click(button); fireEvent.click(button); });
  expect(fetcher).toHaveBeenCalledOnce(); expect(saved).toHaveBeenCalledOnce();
  const options = (fetcher.mock.calls as unknown as [string, RequestInit][])[0][1];
  expect(options.method).toBe('PUT');
  expect(JSON.parse(options.body as string)).toEqual({ faceValue: '200.50', type: title.type, dueDate: title.dueDate, paymentCurrency: 'BRL' });
});
test('uncertain response blocks another save until reopening', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('network'); }));
  render(<EditReceivable title={title} onBusy={vi.fn()} onClose={vi.fn()} onSaved={vi.fn()} />);
  await act(async () => fireEvent.click(screen.getByText('Salvar alterações')));
  expect(screen.getByText(/Resultado da edição incerto/)).toBeTruthy();
  expect((screen.getByText('Salvar alterações') as HTMLButtonElement).disabled).toBe(true);
});
