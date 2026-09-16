import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Alert, Button, Paper, Typography } from '@mui/material';
import { ApiError, money, post, type Assignor } from './api';

const initial = { assignorId: '', titleCode: '', faceValue: '', dueDate: '', type: 'DUPLICATA_MERCANTIL', paymentCurrency: 'BRL' };
type Draft = typeof initial;
type Calculation = { finalAmount: string; paymentCurrency: string; presentValueBrl: string; discountBrl: string; termMonths: number; referenceDate: string; effectiveRate: string; exchangeRateUsed: string | null };

export function validDraft(draft: Draft): boolean {
  const amount = draft.faceValue.replace(',', '.');
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const date = new Date(draft.dueDate + 'T12:00:00Z');
  return Boolean(draft.assignorId && draft.titleCode.trim() && draft.titleCode.trim().length <= 100
    && /^\d{1,17}(\.\d{1,2})?$/.test(amount) && /[1-9]/.test(amount)
    && /^\d{4}-\d{2}-\d{2}$/.test(draft.dueDate) && !Number.isNaN(date.getTime())
    && date.toISOString().slice(0, 10) === draft.dueDate && draft.dueDate >= today);
}

export default function Registration({ assignors, onRegistered }: { assignors: Assignor[]; onRegistered?: () => void }) {
  const [draft, setDraft] = useState(initial);
  const [preview, setPreview] = useState<{ fingerprint: string; value: Calculation } | null>(null);
  const [previewError, setPreviewError] = useState('');
  const [message, setMessage] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const generation = useRef(0);
  const valid = validDraft(draft);
  const fingerprint = JSON.stringify(draft);

  useEffect(() => {
    const current = ++generation.current;
    const controller = new AbortController();
    setPreview(null); setPreviewError('');
    if (!valid || saved) return;
    const timer = setTimeout(() => {
      const { faceValue, type, dueDate, paymentCurrency } = draft;
      post<Calculation>('/simulations', { faceValue: faceValue.replace(',', '.'), type, dueDate, paymentCurrency }, controller.signal)
        .then(value => { if (generation.current === current) setPreview({ fingerprint, value }); })
        .catch(error => { if (generation.current === current) setPreviewError(error instanceof ApiError ? error.message : 'Simulação indisponível. Confira a conexão e altere um campo para tentar novamente.'); });
    }, 400);
    return () => { ++generation.current; clearTimeout(timer); controller.abort(); };
  }, [draft, fingerprint, valid, saved]);

  function update(name: keyof Draft, value: string) {
    ++generation.current;
    setPreview(null); setPreviewError(''); setMessage(''); setFields({});
    setDraft(current => ({ ...current, [name]: value }));
  }
  async function register(event: FormEvent) {
    event.preventDefault();
    if (!valid || sending.current || saved) return;
    sending.current = true; setBusy(true); setMessage(''); setFields({});
    try {
      const result = await post<{ id: string }>('/receivables', { ...draft, titleCode: draft.titleCode.trim(), faceValue: draft.faceValue.replace(',', '.') });
      setSaved(result.id);
      onRegistered?.();
    } catch (error) {
      if (error instanceof ApiError && error.status < 500) {
        setMessage(error.message); setFields(Object.fromEntries((error.detail.fieldErrors || []).map(item => [item.field, item.message])));
      } else {
        setMessage('Resultado do cadastro incerto. Não troque o cedente/código para repetir: o cadastro pode já existir. Uma repetição com a mesma identificação não cria outro título; duplicidade será informada pelo servidor.');
      }
    } finally { sending.current = false; setBusy(false); }
  }
  const calculation = preview?.fingerprint === fingerprint ? preview.value : null;
  return <Paper component="section" variant="outlined" sx={{ p: 3, mb: 3 }} aria-label="Cadastro individual">
    <Typography component="h2" variant="h5" sx={{ mb: 1 }}>Cadastrar recebível</Typography>
    <Typography sx={{ mb: 2 }}>Cadastro não liquida o título. A simulação é informativa e usa as condições atuais do servidor.</Typography>
    <form onSubmit={register}>
      <fieldset disabled={busy || Boolean(saved)} style={{ border: 0, padding: 0, margin: 0 }}>
        <div className="filters">
          <label>Cedente do título<select required value={draft.assignorId} onChange={e => update('assignorId', e.target.value)}><option value="">Selecione</option>{assignors.map(a => <option key={a.id} value={a.id}>{a.code} · {a.name}</option>)}</select></label>
          <label>Código do título<input required maxLength={100} value={draft.titleCode} onChange={e => update('titleCode', e.target.value)} /></label>
          <label>Valor de face (BRL, sem milhar)<input required inputMode="decimal" placeholder="100000,00" value={draft.faceValue} onChange={e => update('faceValue', e.target.value)} /></label>
          <label>Vencimento<input required type="date" value={draft.dueDate} onChange={e => update('dueDate', e.target.value)} /></label>
          <label>Tipo do título<select value={draft.type} onChange={e => update('type', e.target.value)}><option value="DUPLICATA_MERCANTIL">Duplicata mercantil</option><option value="CHEQUE_PRE_DATADO">Cheque pré-datado</option></select></label>
          <label>Moeda para pagamento<select value={draft.paymentCurrency} onChange={e => update('paymentCurrency', e.target.value)}><option>BRL</option><option>USD</option></select></label>
        </div>
        <Typography variant="body2" sx={{ mt: 2 }}>Valor positivo, até 17 dígitos inteiros e 2 decimais; use ponto ou vírgula, sem milhar. Vencimento não pode ser anterior ao dia de negócio em São Paulo. O backend valida o prazo máximo.</Typography>
        <Button type="submit" variant="contained" disabled={!valid || busy || Boolean(saved)} sx={{ mt: 2 }}>{busy ? 'Cadastrando…' : 'Cadastrar como pendente'}</Button>
      </fieldset>
    </form>
    {message && <Alert severity="error" sx={{ mt: 2 }}>{message}{Object.entries(fields).map(([name, error]) => <div key={name}>{name}: {error}</div>)}</Alert>}
    {saved && <Alert severity="success" sx={{ mt: 2 }}>Recebível cadastrado como PENDING. ID: {saved}. Nenhuma liquidação foi realizada.</Alert>}
    {saved && <Button sx={{ mt: 2 }} onClick={() => { setSaved(null); setDraft(initial); setMessage(''); }}>Cadastrar outro título</Button>}
    {!saved && valid && !calculation && !previewError && <Typography role="status" sx={{ mt: 2 }}>Aguardando simulação atualizada…</Typography>}
    {!saved && previewError && <Alert severity="warning" sx={{ mt: 2 }}>{previewError} O cadastro como pendente independe de uma cotação disponível.</Alert>}
    {!saved && calculation && <div aria-label="Simulação atualizada">
      <Typography component="h3" variant="h6" sx={{ mt: 3 }}>Prévia · {calculation.paymentCurrency} {money(calculation.finalAmount)}</Typography>
      <p>VP: BRL {money(calculation.presentValueBrl)} · Deságio: BRL {money(calculation.discountBrl)}</p>
      <p>Referência: {calculation.referenceDate} · Prazo: {calculation.termMonths} mês(es) · Taxa efetiva mensal (fração): {calculation.effectiveRate}</p>
      {calculation.exchangeRateUsed && <p>Cotação: {calculation.exchangeRateUsed} BRL por USD</p>}
      <Typography variant="body2">Esta prévia não autoriza pagamento. Após cadastrar, selecione o título em Liquidar um pendente para revisar e confirmar.</Typography>
    </div>}
  </Paper>;
}
