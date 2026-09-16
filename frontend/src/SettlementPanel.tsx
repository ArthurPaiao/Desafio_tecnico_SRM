import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Paper, Typography } from '@mui/material';
import { ApiError, get, post, money, type Assignor, type Page, type Settlement } from './api';
import EditReceivable, { type EditableTitle } from './EditReceivable';

type Title = { id: string; assignorId: string; titleCode: string; faceValue: string; paymentCurrency: string; dueDate: string };
type Conditions = Record<string, string | number | null>;
type Preview = { receivable: Title; simulation: Settlement['calculation']; expectedConditions: Conditions };
type Attempt = { key: string; body: string };
type BatchRow = { title: Title; preview?: Preview; previous?: Conditions; selected: boolean;
  status: 'Pronto para revisão' | 'Liquidado' | 'Aguardando reconfirmação' | 'Erro de negócio' | 'Não processado' | 'Resultado a confirmar'; detail?: string };
export function totals(values: Settlement['calculation'][]): string {
  const sums: Record<string, bigint> = { BRL: 0n, USD: 0n };
  for (const value of values) {
    if (!Object.hasOwn(sums, value.paymentCurrency) || !/^\d+\.\d{2}$/.test(value.finalAmount)) return 'Total indisponível: valor inesperado';
    sums[value.paymentCurrency] += BigInt(value.finalAmount.replace('.', ''));
  }
  return Object.entries(sums).map(([currency, cents]) => `${currency} ${money(`${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`)}`).join(' · ');
}
const prefix = 'srm.settlement.v1.';
const labels: Record<string, string> = { assignorId: 'Cedente (ID)', titleCode: 'Título', type: 'Tipo', dueDate: 'Vencimento', termMonths: 'Prazo (meses)', paymentCurrency: 'Moeda', faceValueBrl: 'Face (BRL)', effectiveRate: 'Taxa mensal (fração)', presentValueBrl: 'VP (BRL)', discountBrl: 'Deságio (BRL)', finalAmount: 'Pagamento na moeda indicada', exchangeRateUsed: 'BRL por USD' };

function recover(): { attempts: Attempt[]; error: string } {
  try {
    const attempts: Attempt[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const name = localStorage.key(i)!;
      if (!name.startsWith(prefix)) continue;
      const body = localStorage.getItem(name)!;
      const parsed = JSON.parse(body);
      if (typeof parsed.receivableId !== 'string' || !parsed.expectedConditions || typeof parsed.expectedConditions !== 'object') throw new Error();
      attempts.push({ key: name.slice(prefix.length), body });
    }
    return { attempts, error: '' };
  } catch { return { attempts: [], error: 'Não foi possível ler as tentativas locais. A liquidação está bloqueada; preserve os dados do navegador para investigar.' }; }
}

export default function SettlementPanel({ assignors, revision, onSettled }: { assignors: Assignor[]; revision: number; onSettled: () => void }) {
  const [page, setPage] = useState(0);
  const [basket, setBasket] = useState<Title[]>([]);
  const [batch, setBatch] = useState<BatchRow[]>([]);
  const [batchBusy, setBatchBusy] = useState(false);
  const batchRunning = useRef(false);
  const mounted = useRef(true);
  const [editing, setEditing] = useState<EditableTitle | null>(null);
  const [editBusy, setEditBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const [titles, setTitles] = useState<Page<Title> | null>(null);
  const [listError, setListError] = useState('');
  const [preview, setPreview] = useState<Preview | null>(null);
  const [previous, setPrevious] = useState<Conditions | null>(null);
  const [checked, setChecked] = useState(false);
  const [message, setMessage] = useState('');
  const [success, setSuccess] = useState<Settlement | null>(null);
  const [busy, setBusy] = useState(false);
  const [simulating, setSimulating] = useState(false);
  const [recovery, setRecovery] = useState(recover);
  const sending = useRef(false);
  const generation = useRef(0);

  useEffect(() => {
    mounted.current = true;
    const refresh = () => setRecovery(recover());
    window.addEventListener('storage', refresh);
    return () => { mounted.current = false; window.removeEventListener('storage', refresh); ++generation.current; };
  }, []);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setTitles(null); setListError('');
    get<Page<Title>>('/receivables?status=PENDING&size=20&page=' + page, controller.signal)
      .then(value => { if (active) setTitles(value); })
      .catch(() => { if (active) setListError('Não foi possível carregar os pendentes.'); });
    return () => { active = false; controller.abort(); };
  }, [page, reload, revision]);

  async function select(title: Title) {
    if (sending.current || batchRunning.current || recovery.error || recovery.attempts.length) return;
    setBatch([]); setBasket([]);
    setEditing(null);
    const current = ++generation.current;
    setPreview(null); setPrevious(null); setChecked(false); setMessage(''); setSuccess(null); setSimulating(true);
    try {
      const value = await post<Preview>('/receivables/' + title.id + '/simulations', undefined, AbortSignal.timeout(15000));
      if (generation.current === current) setPreview(value);
    } catch (error) { if (generation.current === current) setMessage(error instanceof Error ? error.message : 'Simulação indisponível.'); }
    finally { if (generation.current === current) setSimulating(false); }
  }

  async function edit(title: Title) {
    if (sending.current || batchRunning.current || editBusy || recovery.error || recovery.attempts.length) return;
    setBatch([]); setBasket([]);
    const current = ++generation.current;
    setEditing(null); setPreview(null); setPrevious(null); setChecked(false); setSuccess(null); setMessage(''); setSimulating(false);
    try {
      const value = await get<EditableTitle & { status: string }>('/receivables/' + title.id, AbortSignal.timeout(15000));
      if (current !== generation.current) return;
      if (value.status !== 'PENDING') { setMessage('Título já liquidado; atualize os pendentes.'); return; }
      setEditing(value);
    } catch { if (current === generation.current) setMessage('Não foi possível abrir a edição. Atualize os pendentes e tente novamente.'); }
  }

  async function send(attempt: Attempt): Promise<boolean> {
    if (sending.current) return false;
    const request = JSON.parse(attempt.body);
    const updateBatch = (update: Partial<BatchRow>) => setBatch(rows => rows.map(row => row.title.id === request.receivableId ? { ...row, ...update, selected: false } : row));
    sending.current = true; setBusy(true); setChecked(false); setMessage(''); setSuccess(null);
    try {
      // Persist before sending: a closed tab or a timeout does not prove rollback.
      localStorage.setItem(prefix + attempt.key, attempt.body);
      setRecovery(recover());
    } catch {
      setMessage('Não foi possível salvar a tentativa. Nenhum pedido foi enviado. Verifique o armazenamento do navegador.');
      updateBatch({ status: 'Não processado', detail: 'Armazenamento indisponível; nenhum pedido enviado.' });
      sending.current = false; setBusy(false); return false;
    }
    try {
      const response = await fetch('/api/settlements', { method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': attempt.key },
        body: attempt.body, signal: AbortSignal.timeout(15000) });
      const body = await response.json();
      if (response.status === 200 || response.status === 201) {
        if (!body.id || !body.calculation || body.receivableId !== JSON.parse(attempt.body).receivableId) throw new Error('Resposta incompleta');
        localStorage.removeItem(prefix + attempt.key);
        setRecovery(recover()); setSuccess(body); setPreview(null); setPrevious(null);
        setReload(value => value + 1); onSettled();
        updateBatch({ status: 'Liquidado', detail: `${body.id} · ${body.calculation.paymentCurrency} ${money(body.calculation.finalAmount)}` });
        return true;
      } else if (response.status === 409 && body.code === 'CONDITIONS_CHANGED' && body.current?.expectedConditions && body.current?.simulation && body.current?.receivable?.id === JSON.parse(attempt.body).receivableId) {
        localStorage.removeItem(prefix + attempt.key);
        setRecovery(recover()); setPrevious(JSON.parse(attempt.body).expectedConditions); setPreview(body.current);
        setMessage('As condições mudaram. Nenhuma liquidação foi realizada nesta tentativa. Revise antes de reconfirmar.');
        updateBatch({ status: 'Aguardando reconfirmação', previous: request.expectedConditions, preview: body.current, detail: 'Nenhuma liquidação nesta tentativa. Revise anterior/atual e selecione novamente.' });
        return true;
      } else if ((response.status === 400 && body.code === 'INVALID_INPUT') ||
        (response.status === 404 && body.code === 'NOT_FOUND') ||
        (response.status === 422 && ['EXCHANGE_RATE_UNAVAILABLE', 'EXCHANGE_RATE_EXPIRED'].includes(body.code)) ||
        (response.status === 409 && ['ALREADY_SETTLED', 'IDEMPOTENCY_CONFLICT'].includes(body.code))) {
        localStorage.removeItem(prefix + attempt.key);
        setRecovery(recover()); setPreview(null); setPrevious(null); setMessage(body.message);
        setReload(value => value + 1);
        updateBatch({ status: 'Erro de negócio', detail: body.message });
        return true;
      } else {
        setMessage('Resultado a confirmar ou operação em andamento. Repita manualmente a mesma tentativa quando desejar.');
        updateBatch({ status: 'Resultado a confirmar', detail: 'Use Consultar ou concluir tentativa. Os próximos itens não serão enviados.' });
        return false;
      }
    } catch {
      setMessage('Resultado a confirmar: a resposta não foi recebida com segurança. A tentativa foi preservada para consulta ou conclusão.');
      updateBatch({ status: 'Resultado a confirmar', detail: 'Resposta incerta; tentativa preservada para recuperação manual.' });
      return false;
    }
    finally { sending.current = false; setBusy(false); }
  }
  function confirm() {
    const saved = recover(); setRecovery(saved);
    if (!checked || !preview || sending.current || batchRunning.current || saved.error || saved.attempts.length) return;
    void send({ key: crypto.randomUUID(), body: JSON.stringify({ receivableId: preview.receivable.id, expectedConditions: preview.expectedConditions }) });
  }
  function add(title: Title) {
    if (sending.current || batchRunning.current || editBusy || recovery.error || recovery.attempts.length) return;
    ++generation.current; setSimulating(false); setEditing(null); setPreview(null); setPrevious(null); setChecked(false); setBatch([]); setMessage(''); setSuccess(null);
    setBasket(current => current.some(t => t.id === title.id) ? current.filter(t => t.id !== title.id) : [...current, title]);
  }
  async function reviewBatch() {
    const saved = recover(); setRecovery(saved);
    if (!basket.length || sending.current || batchRunning.current || saved.error || saved.attempts.length) return;
    batchRunning.current = true; setBatchBusy(true); ++generation.current;
    setPreview(null); setPrevious(null); setChecked(false); setEditing(null); setSimulating(false); setMessage(''); setSuccess(null);
    const rows: BatchRow[] = [];
    let stopped = false;
    try {
      for (const title of basket) {
        if (!mounted.current) break;
        const row: BatchRow = { title, selected: false, status: 'Não processado' };
        if (!stopped) {
          try {
            row.preview = await post<Preview>('/receivables/' + title.id + '/simulations', undefined, AbortSignal.timeout(15000));
            row.status = 'Pronto para revisão';
          } catch (error) {
            if (error instanceof ApiError && [400, 404, 409, 422].includes(error.status)) {
              row.status = 'Erro de negócio'; row.detail = error.message;
            } else { stopped = true; row.detail = 'Falha técnica na simulação. Nenhum pagamento enviado.'; }
          }
        } else row.detail = 'Consulta interrompida após falha técnica.';
        rows.push(row);
        if (mounted.current) setBatch([...rows]);
      }
    } finally { batchRunning.current = false; if (mounted.current) setBatchBusy(false); }
  }
  async function confirmBatch() {
    const saved = recover(); setRecovery(saved);
    const selected = batch.filter(row => row.selected && row.preview && ['Pronto para revisão', 'Aguardando reconfirmação', 'Não processado'].includes(row.status));
    if (!selected.length || sending.current || batchRunning.current || saved.error || saved.attempts.length) return;
    batchRunning.current = true; setBatchBusy(true);
    setBatch(rows => rows.map(row => ({ ...row, selected: false })));
    let stopped = false;
    try {
      for (const row of selected) {
        if (!mounted.current) break;
        const local = recover();
        if (stopped || local.error || local.attempts.length) {
          stopped = true;
          setBatch(rows => rows.map(r => r.title.id === row.title.id ? { ...r, status: 'Não processado', detail: 'Envio interrompido. Resolva a tentativa incerta antes de selecionar novamente.' } : r));
          continue;
        }
        stopped = !await send({ key: crypto.randomUUID(), body: JSON.stringify({ receivableId: row.title.id, expectedConditions: row.preview!.expectedConditions }) });
      }
    } finally { batchRunning.current = false; if (mounted.current) setBatchBusy(false); }
  }
  const blocked = busy || batchBusy || editBusy || Boolean(recovery.error) || recovery.attempts.length > 0;
  return <Paper component="section" variant="outlined" sx={{ p: 3, mb: 3 }} aria-label="Liquidação individual">
    <Typography variant="h5" component="h2">Liquidar um pendente</Typography>
    <Typography sx={{ my: 2 }}>Selecione um título, revise as condições e confirme. O servidor recalcula antes de registrar; não há transferência bancária real nesta demonstração.</Typography>
    {recovery.error && <Alert severity="error">{recovery.error}</Alert>}
    {recovery.attempts.map(attempt => <Alert key={attempt.key} severity="warning" sx={{ my: 2 }}>
      Tentativa sem resultado confirmado · chave {attempt.key}
      <p>A ação abaixo pode concluir a liquidação se ela ainda não ocorreu. Não há reenvio automático. Recuperação apenas neste navegador e nesta origem.</p>
      <details><summary>Pedido preservado</summary><pre>{attempt.body}</pre></details>
      <Button disabled={busy || batchBusy || editBusy} onClick={() => void send(attempt)}>Consultar ou concluir tentativa</Button>
    </Alert>)}
    <Button disabled={busy || batchBusy || editBusy} onClick={() => { ++generation.current; setEditing(null); setSimulating(false); setPreview(null); setPrevious(null); setChecked(false); setReload(v => v + 1); }}>Atualizar pendentes</Button>
    {listError && <Alert severity="error">{listError}</Alert>}
    {!titles && !listError && <p>Carregando pendentes…</p>}
    {titles && <>
      {titles.content.length === 0 && <p>Nenhum pendente nesta página.</p>}
      <ul>{titles.content.map(title => <li key={title.id} style={{ marginBlock: 12 }}>
        {title.titleCode} · {assignors.find(a => a.id === title.assignorId)?.name || title.assignorId} · BRL {money(title.faceValue)} · vence {title.dueDate} · pagamento {title.paymentCurrency}
        <Button disabled={blocked} onClick={() => void select(title)} aria-label={'Revisar ' + title.titleCode}>Revisar</Button>
        <Button disabled={blocked} onClick={() => void edit(title)} aria-label={'Editar ' + title.titleCode}>Editar</Button>
        <Button disabled={blocked || (basket.length >= 100 && !basket.some(t => t.id === title.id))} onClick={() => add(title)} aria-label={'Lote ' + title.titleCode}>{basket.some(t => t.id === title.id) ? 'Remover do lote' : 'Adicionar ao lote'}</Button>
      </li>)}</ul>
      <Button disabled={page === 0 || busy || batchBusy || editBusy} onClick={() => setPage(v => v - 1)}>Pendentes anteriores</Button>
      <span>Página {page + 1}</span>
      <Button disabled={page + 1 >= titles.totalPages || busy || batchBusy || editBusy} onClick={() => setPage(v => v + 1)}>Próximos pendentes</Button>
    </>}
    {simulating && <p role="status">Consultando condições do título…</p>}
    {basket.length > 0 && <section aria-label="Liquidação em lote">
      <h3>Lote · {basket.length} título(s)</h3>
      <p>Até 100 títulos entre páginas. Adicionar não autoriza pagamento. Revise e marque cada item que deseja liquidar. Fechar/recarregar interrompe os próximos envios; somente tentativas incertas são recuperadas.</p>
      <p>{basket.map(t => t.titleCode).join(' · ')}</p>
      <Button disabled={blocked} onClick={reviewBatch}>Revisar lote</Button>
      <Button disabled={busy || batchBusy} onClick={() => { setBasket([]); setBatch([]); setPreview(null); setPrevious(null); setChecked(false); }}>Limpar lote</Button>
      {batchBusy && <p role="status">Processando lote sequencialmente… não feche esta página.</p>}
      {batch.map(row => <div key={row.title.id} style={{ borderTop: '1px solid #dce3df', paddingBlock: 16 }}>
        <h4>{row.title.titleCode} · {row.status}</h4><p>{row.detail}</p>
        {row.preview && row.status !== 'Liquidado' && <details open={row.status === 'Aguardando reconfirmação'}><summary>Condições de {row.title.titleCode}</summary>
          <div className="table-scroll"><table><caption>Valores para revisão · {row.title.titleCode}</caption><thead><tr><th>Condição</th>{row.previous && <th>Anterior</th>}<th>Atual</th></tr></thead><tbody>
            {Object.entries(row.preview.expectedConditions).map(([key, value]) => <tr key={key}><th>{labels[key] || key}</th>{row.previous && <td>{String(row.previous[key] ?? 'Não se aplica')}</td>}<td>{String(value ?? 'Não se aplica')}</td></tr>)}
          </tbody></table></div>
        </details>}
        {row.preview && ['Pronto para revisão', 'Aguardando reconfirmação', 'Não processado'].includes(row.status) && <label style={{ flexDirection: 'row', marginBlock: 12 }}><input style={{ width: 'auto' }} type="checkbox" disabled={blocked} checked={row.selected} onChange={e => setBatch(rows => rows.map(r => r.title.id === row.title.id ? { ...r, selected: e.target.checked } : r))} />Autorizo {row.title.titleCode} · {row.preview.simulation.paymentCurrency} {money(row.preview.simulation.finalAmount)}</label>}
      </div>)}
      {batch.length > 0 && <>
        <p>Totais selecionados (sem conversão): {totals(batch.filter(r => r.selected && r.preview).map(r => r.preview!.simulation))}</p>
        <Button variant="contained" disabled={blocked || !batch.some(r => r.selected)} onClick={confirmBatch}>{batch.some(r => r.selected && r.status === 'Aguardando reconfirmação') ? 'Reconfirmar selecionados' : 'Liquidar selecionados'}</Button>
      </>}
    </section>}
    {editing && !recovery.error && !recovery.attempts.length && <EditReceivable key={editing.id} title={editing} onBusy={setEditBusy}
      onClose={() => { setEditing(null); setReload(v => v + 1); }}
      onSaved={() => { setEditing(null); setPreview(null); setPrevious(null); setChecked(false); setReload(v => v + 1); setMessage('Título atualizado. Clique em Revisar para obter uma nova simulação antes de liquidar.'); onSettled(); }} />}
    {message && <Alert severity="warning" sx={{ my: 2 }}>{message}</Alert>}
    {success && !batch.length && <Alert severity="success" sx={{ my: 2 }}>Liquidação confirmada: {success.id} · {success.calculation.paymentCurrency} {money(success.calculation.finalAmount)}.</Alert>}
    {preview && !batch.length && !blocked && <div aria-label="Condições para confirmação">
      <Typography variant="h6" component="h3">{previous ? 'Revisar novas condições' : 'Revisar condições'} · {preview.receivable.titleCode}</Typography>
      <div className="table-scroll"><table><caption>Condições enviadas ao servidor para comparação</caption><thead><tr><th>Condição</th>{previous && <th>Anterior</th>}<th>Atual</th></tr></thead>
        <tbody>{Object.entries(preview.expectedConditions).map(([key, value]) => <tr key={key}><th scope="row">{labels[key] || key}</th>{previous && <td>{String(previous[key] ?? 'Não se aplica')}</td>}<td>{String(value ?? 'Não se aplica')}</td></tr>)}</tbody></table></div>
      <p>Pagamento: {preview.simulation.paymentCurrency} {money(preview.simulation.finalAmount)}. Valores decimais da comparação usam ponto; taxa em fração mensal.</p>
      <label style={{ display: 'flex', flexDirection: 'row', alignItems: 'center' }}><input style={{ width: 'auto' }} type="checkbox" checked={checked} onChange={e => setChecked(e.target.checked)} />Revisei as condições e autorizo a liquidação integral deste título.</label>
      <Button variant="contained" disabled={!checked || busy} sx={{ mt: 2 }} onClick={confirm}>{previous ? 'Reconfirmar liquidação' : 'Confirmar liquidação'}</Button>
    </div>}
  </Paper>;
}
