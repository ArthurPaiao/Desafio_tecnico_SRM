import { useEffect, useState, type FormEvent } from 'react';
import { Alert, Button, Chip, Container, CssBaseline, Paper, Stack, ThemeProvider, Typography, createTheme } from '@mui/material';
import { get, money, type Assignor, type Page, type Settlement } from './api';
import './style.css';
import Registration from './Registration';
import SettlementPanel from './SettlementPanel';
import CsvImport from './CsvImport';

const theme = createTheme({
  palette: { primary: { main: '#12584c' }, background: { default: '#f4f6f5' } },
  typography: { fontFamily: 'Arial, sans-serif' }, shape: { borderRadius: 12 },
});
const empty = { from: '', to: '', assignorId: '', currency: '' };

export default function App() {
  const [filters, setFilters] = useState(empty);
  const [query, setQuery] = useState('page=0&size=20');
  const [revision, setRevision] = useState(0);
  const [assignors, setAssignors] = useState<Assignor[]>([]);
  const [assignorError, setAssignorError] = useState('');
  const [data, setData] = useState<Page<Settlement> | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    get<Page<Assignor>>('/assignors?size=100', controller.signal)
      .then(page => { if (active) {
        setAssignors(page.content);
        if (page.totalElements > 100) setAssignorError('Exibindo os primeiros 100 cedentes.');
      } })
      .catch(() => { if (active) setAssignorError('Cedentes indisponíveis. Recarregue a página para tentar novamente.'); });
    return () => { active = false; controller.abort(); };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true); setError(''); setData(null);
    get<Page<Settlement>>('/settlements?' + query, controller.signal)
      .then(page => { if (active) setData(page); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : 'Falha de conexão com a API.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [query, revision]);

  function apply(event: FormEvent) {
    event.preventDefault();
    const params = new URLSearchParams({ page: '0', size: '20' });
    Object.entries(filters).forEach(([key, value]) => { if (value.trim()) params.set(key, value.trim()); });
    setQuery(params.toString()); setRevision(value => value + 1);
  }
  function page(number: number) {
    const params = new URLSearchParams(query); params.set('page', String(number)); setQuery(params.toString());
  }
  const update = (name: keyof typeof empty, value: string) => setFilters(current => ({ ...current, [name]: value }));

  return <ThemeProvider theme={theme}><CssBaseline />
    <header className="topbar"><strong>SRM <span>Credit Engine</span></strong><Chip label="Demonstração local" size="small" /></header>
    <Container maxWidth="lg" component="main" sx={{ py: 5 }}>
      <Typography variant="overline" color="primary">OPERAÇÕES · CONSULTA</Typography>
      <Typography variant="h3" component="h1" sx={{ fontWeight: 700, mb: 1 }}>Operações de recebíveis</Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>Consulte os valores registrados na confirmação. O histórico não é recalculado.</Typography>
      <Alert severity="info" sx={{ mb: 3 }}>Cadastre, revise e confirme individualmente ou adicione títulos ao lote. O extrato preserva os valores registrados.</Alert>
      <Registration assignors={assignors} onRegistered={() => setRevision(v => v + 1)} />
      <CsvImport assignors={assignors} onRegistered={() => setRevision(v => v + 1)} />
      <SettlementPanel assignors={assignors} revision={revision} onSettled={() => setRevision(v => v + 1)} />
      <Paper component="section" variant="outlined" sx={{ p: 3, mb: 3 }} aria-label="Filtros do extrato">
        <form onSubmit={apply}>
          <div className="filters">
            <label>Início inclusivo (ISO com fuso)<input name="from" value={filters.from} placeholder="2026-09-14T00:00:00-03:00" onChange={e => update('from', e.target.value)} /></label>
            <label>Fim exclusivo (ISO com fuso)<input name="to" value={filters.to} placeholder="2026-09-15T00:00:00-03:00" onChange={e => update('to', e.target.value)} /></label>
            <label>Cedente<select value={filters.assignorId} onChange={e => update('assignorId', e.target.value)}><option value="">Todos os cedentes</option>{assignors.map(a => <option key={a.id} value={a.id}>{a.code} · {a.name}</option>)}</select></label>
            <label>Moeda de pagamento<select value={filters.currency} onChange={e => update('currency', e.target.value)}><option value="">BRL e USD</option><option>BRL</option><option>USD</option></select></label>
          </div>
          <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
            <Button type="submit" variant="contained">Aplicar filtros</Button>
            <Button type="button" onClick={() => { setFilters(empty); setQuery('page=0&size=20'); setRevision(v => v + 1); }}>Limpar filtros</Button>
          </Stack>
        </form>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>Filtros opcionais. Informe horário e fuso; por exemplo, -03:00 ou Z. O período se refere à liquidação, não ao vencimento.</Typography>
        {assignorError && <Alert severity="warning" sx={{ mt: 2 }}>{assignorError}</Alert>}
      </Paper>
      {loading && <Typography role="status">Carregando liquidações…</Typography>}
      {error && <Alert severity="error" action={<Button color="inherit" onClick={() => setRevision(v => v + 1)}>Tentar novamente</Button>}>{error}</Alert>}
      {!loading && data && <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <div className="result-heading"><Typography component="h2" variant="h6">Liquidações registradas</Typography><span>{data.totalElements} registro(s)</span></div>
        {data.content.length === 0 ? <p className="empty" role="status">Nenhuma liquidação encontrada para esta consulta.</p> :
          <div className="table-scroll"><table><caption>Valores históricos por título — sem soma entre moedas</caption>
            <thead><tr><th>Título / cedente</th><th>Liquidação (UTC)</th><th>VP em BRL</th><th>Deságio em BRL</th><th>Pagamento</th><th>Snapshot</th></tr></thead>
            <tbody>{data.content.map(row => <tr key={row.id}>
              <td><strong>{row.titleCode}</strong><br /><small>{row.assignorName}</small></td>
              <td>{row.settledAt}</td><td>BRL {money(row.calculation.presentValueBrl)}</td>
              <td>BRL {money(row.calculation.discountBrl)}</td><td><strong>{row.calculation.paymentCurrency} {money(row.calculation.finalAmount)}</strong></td>
              <td><details><summary>Detalhes de {row.titleCode}</summary><pre>{JSON.stringify(row, null, 2)}</pre></details></td>
            </tr>)}</tbody>
          </table></div>}
        <Stack direction="row" spacing={2} sx={{ p: 2, alignItems: 'center', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
          <Button disabled={data.page === 0} onClick={() => page(data.page - 1)}>Anterior</Button>
          <span>Página {data.page + 1} · {data.totalPages} página(s) com resultados</span>
          <Button disabled={data.page + 1 >= data.totalPages} onClick={() => page(data.page + 1)}>Próxima</Button>
        </Stack>
      </Paper>}
      <Typography variant="body2" color="text.secondary" sx={{ mt: 3 }}>Mais recentes primeiro · 20 registros por página · Novas liquidações podem deslocar as páginas.</Typography>
    </Container>
  </ThemeProvider>;
}
