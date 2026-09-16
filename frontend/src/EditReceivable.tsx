import { useRef, useState, type FormEvent } from 'react';
import { Alert, Button } from '@mui/material';
import { validDraft } from './Registration';
export interface EditableTitle { id: string; assignorId: string; titleCode: string; faceValue: string; type: string; dueDate: string; paymentCurrency: string }

export default function EditReceivable({ title, onClose, onSaved, onBusy }: {
  title: EditableTitle; onClose: () => void; onSaved: () => void; onBusy: (busy: boolean) => void;
}) {
  const [draft, setDraft] = useState(title);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [uncertain, setUncertain] = useState(false);
  const sending = useRef(false);
  const update = (name: keyof EditableTitle, value: string) => setDraft(current => ({ ...current, [name]: value }));
  async function save(event: FormEvent) {
    event.preventDefault();
    if (sending.current || uncertain || !validDraft(draft)) return;
    sending.current = true; setBusy(true); onBusy(true); setMessage('');
    try {
      const { faceValue, type, dueDate, paymentCurrency } = draft;
      const response = await fetch('/api/receivables/' + title.id, { method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ faceValue: faceValue.replace(',', '.'), type, dueDate, paymentCurrency }),
        signal: AbortSignal.timeout(15000) });
      const body = await response.json();
      if (response.ok) {
        if (body.id !== title.id || body.status !== 'PENDING') throw new Error('Resposta inesperada');
        onSaved();
      } else if ([400, 404, 409, 422].includes(response.status)) {
        setMessage((body.message || 'Alteração rejeitada.') + ' ' + (body.fieldErrors || []).map((e: { field: string; message: string }) => `${e.field}: ${e.message}`).join('; '));
      } else throw new Error('Falha técnica');
    } catch {
      setUncertain(true);
      setMessage('Resultado da edição incerto. Feche e abra Editar novamente para consultar o cadastro atual antes de repetir. Nenhuma liquidação foi solicitada.');
    } finally { sending.current = false; setBusy(false); onBusy(false); }
  }
  return <form onSubmit={save} aria-label="Editar recebível">
    <h3>Editar · {title.titleCode}</h3>
    <p>Cedente e código permanecem fixos. Salvar não liquida; será necessário revisar uma nova simulação.</p>
    <fieldset disabled={busy || uncertain} style={{ border: 0, padding: 0 }}>
      <div className="filters">
        <label>Editar valor de face<input required inputMode="decimal" value={draft.faceValue} onChange={e => update('faceValue', e.target.value)} /></label>
        <label>Editar vencimento<input required type="date" value={draft.dueDate} onChange={e => update('dueDate', e.target.value)} /></label>
        <label>Editar tipo<select value={draft.type} onChange={e => update('type', e.target.value)}><option value="DUPLICATA_MERCANTIL">Duplicata mercantil</option><option value="CHEQUE_PRE_DATADO">Cheque pré-datado</option></select></label>
        <label>Editar moeda<select value={draft.paymentCurrency} onChange={e => update('paymentCurrency', e.target.value)}><option>BRL</option><option>USD</option></select></label>
      </div>
      <p>Valor positivo, até 17 inteiros e 2 decimais, sem milhar. Vencimento a partir do dia de negócio em São Paulo; prazo máximo validado no servidor.</p>
      <Button type="submit" variant="contained" disabled={!validDraft(draft) || busy || uncertain}>Salvar alterações</Button>
    </fieldset>
    <Button disabled={busy} onClick={onClose}>Fechar edição</Button>
    {message && <Alert severity="warning">{message}</Alert>}
  </form>;
}
