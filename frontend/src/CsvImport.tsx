import { useRef, useState } from 'react';
import { Alert, Button, Paper, Typography } from '@mui/material';
import { ApiError, post, type Assignor } from './api';

interface Row { line: number; values: string[]; request: Record<string, string> | null; errors: string[] }
interface Preview { maxRows: number; rows: Row[] }
export default function CsvImport({ assignors, onRegistered }: { assignors: Assignor[]; onRegistered: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [results, setResults] = useState<Record<number, string>>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [finished, setFinished] = useState(false);
  const running = useRef(false);

  async function read() {
    if (!file || running.current) return;
    running.current = true; setBusy(true); setError(''); setPreview(null); setSelected([]); setResults({}); setFinished(false);
    try {
      if (file.size > 262144) throw new Error('CSV excede 256 KB.');
      const body = new FormData(); body.append('file', file);
      const response = await fetch('/api/receivables/import-preview', { method: 'POST', body, signal: AbortSignal.timeout(15000) });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || `Falha ao ler CSV (HTTP ${response.status}).`);
      setPreview(data);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Não foi possível ler o CSV.'); }
    finally { running.current = false; setBusy(false); }
  }

  async function confirm() {
    if (!preview || !selected.length || running.current || finished) return;
    running.current = true; setBusy(true); setError('');
    let stopped = false;
    for (const row of preview.rows.filter(row => selected.includes(row.line) && row.request && !row.errors.length)) {
      let result = 'Não processado: execução interrompida. Reenvie o arquivo para nova prévia.';
      if (!stopped) {
        try {
          const saved = await post<{ id: string; status: string }>('/receivables', row.request, AbortSignal.timeout(15000));
          if (!saved.id || saved.status !== 'PENDING') throw new Error('Resposta inesperada');
          result = `Cadastrado como PENDING · ID: ${saved.id}`;
        } catch (cause) {
          if (cause instanceof ApiError && [400, 404, 409, 422].includes(cause.status)) {
            result = `Não cadastrado: ${cause.message}`;
          } else {
            result = 'Resultado a confirmar: consulte os pendentes ou reenvie o mesmo arquivo para verificar duplicidade. Não troque o código do título.';
            stopped = true;
          }
        }
      }
      setResults(current => ({ ...current, [row.line]: result }));
    }
    setSelected([]); setFinished(true); running.current = false; setBusy(false); onRegistered();
  }

  return <Paper component="section" variant="outlined" sx={{ p: 3, mb: 3 }} aria-label="Importação CSV">
    <Typography component="h2" variant="h5">Importar recebíveis por CSV</Typography>
    <p>UTF-8, ponto e vírgula, valor com vírgula decimal sem milhar e data DD/MM/AAAA. Até {preview?.maxRows ?? 100} títulos (configurável no servidor) e 256 KB.</p>
    <a href="/modelo-recebiveis.csv" download>Baixar modelo CSV</a>
    <details><summary>Códigos dos cedentes disponíveis</summary><ul>{assignors.map(a => <li key={a.id}>{a.code} · {a.name}</li>)}</ul></details>
    <label>Arquivo CSV<input type="file" accept=".csv,text/csv" disabled={busy} onChange={e => {
      setFile(e.target.files?.[0] ?? null); setPreview(null); setSelected([]); setResults({}); setError(''); setFinished(false);
    }} /></label>
    <Button disabled={!file || busy} onClick={read}>Gerar prévia</Button>
    {busy && <p role="status">Processando… aguarde antes de fechar esta página.</p>}
    {error && <Alert severity="error">{error}</Alert>}
    {preview && <>
      <Alert severity="info">Nenhuma liquidação será realizada. Selecione as linhas válidas e confirme o cadastro. A prévia não garante o cadastro; o servidor revalida cada título.</Alert>
      <p>Corrija os erros no arquivo e reenvie. As linhas indicam o início de cada registro no CSV. Não guardamos o arquivo nem o histórico da importação.</p>
      <div className="table-scroll"><table className="csv-preview"><caption>Prévia de importação · {preview.rows.length} título(s)</caption>
        <thead><tr>{['Selecionar', 'Linha', 'Título / cedente', 'Dados do título', 'Validação / resultado'].map(h => <th key={h}>{h}</th>)}</tr></thead>
        <tbody>{preview.rows.map(row => <tr key={row.line}>
          <td><input type="checkbox" aria-label={`Selecionar linha ${row.line}`} disabled={busy || finished || !row.request || !!row.errors.length} checked={selected.includes(row.line)} onChange={e => setSelected(current => e.target.checked ? [...current, row.line] : current.filter(n => n !== row.line))} /></td>
          <td>{row.line}</td><td><strong>{row.values[1]}</strong><br />{row.values[0]}</td>
          <td>{row.values[2]}<br />Face: BRL {row.values[3]}<br />Vencimento: {row.values[4]}<br />Pagamento: {row.values[5]}</td>
          <td>{results[row.line] || row.errors.join('; ') || 'Válida para cadastro'}</td>
        </tr>)}</tbody>
      </table></div>
      <Button disabled={busy || finished} onClick={() => setSelected(preview.rows.filter(r => r.request && !r.errors.length).map(r => r.line))}>Selecionar válidas</Button>
      <Button variant="contained" disabled={busy || finished || !selected.length} onClick={confirm}>Confirmar cadastro de {selected.length} título(s)</Button>
      {finished && <Alert severity="info">Processamento encerrado. Confira o resultado de cada linha. Gere uma nova prévia para verificar o estado atual antes de tentar novamente.</Alert>}
    </>}
  </Paper>;
}
