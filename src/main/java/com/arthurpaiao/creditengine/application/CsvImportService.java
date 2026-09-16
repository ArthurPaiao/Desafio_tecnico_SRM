package com.arthurpaiao.creditengine.application;

import com.arthurpaiao.creditengine.api.Contracts.CreateReceivable;
import com.arthurpaiao.creditengine.domain.*;
import com.arthurpaiao.creditengine.persistence.*;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;

@Service
public class CsvImportService {
    public static final List<String> HEADERS = List.of("cedente_codigo", "titulo_codigo", "tipo", "valor_face", "vencimento", "moeda_pagamento");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final AssignorRepository assignors;
    private final ReceivableRepository receivables;
    private final CreditService credit;
    private final Validator validator;
    private final int maxRows;

    public CsvImportService(AssignorRepository assignors, ReceivableRepository receivables, CreditService credit,
                            Validator validator, @Value("${credit.import.max-rows:100}") int maxRows) {
        if (maxRows < 1) throw new IllegalArgumentException("Limite CSV deve ser positivo");
        this.assignors = assignors; this.receivables = receivables; this.credit = credit;
        this.validator = validator; this.maxRows = maxRows;
    }

    public record Row(long line, List<String> values, CreateReceivable request, List<String> errors) {}
    public record Preview(int maxRows, List<Row> rows) {}

    public Preview preview(byte[] bytes) {
        if (bytes.length > 262144) throw new IllegalArgumentException("CSV excede 256 KB");
        var rows = new ArrayList<Row>();
        try {
            String csv = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
            try (var parser = CSVFormat.RFC4180.builder().setDelimiter(';').get().parse(new StringReader(csv))) {
                var iterator = parser.iterator();
                if (!iterator.hasNext()) throw new IllegalArgumentException("CSV vazio");
                var header = iterator.next().toList();
                if (header.size() != HEADERS.size() || !new HashSet<>(header).equals(new HashSet<>(HEADERS))) {
                    throw new IllegalArgumentException("Cabeçalhos obrigatórios: " + String.join(";", HEADERS));
                }
                long nextLine = parser.getCurrentLineNumber() + 1;
                while (iterator.hasNext()) {
                    var record = iterator.next();
                    if (rows.size() == maxRows) throw new IllegalArgumentException("CSV excede o limite de " + maxRows + " títulos; nenhum foi cadastrado");
                    if (record.size() != header.size()) throw new IllegalArgumentException("Número de colunas inválido na linha " + nextLine);
                    var values = HEADERS.stream().map(h -> record.get(header.indexOf(h)).strip()).toList();
                    rows.add(validate(nextLine, values));
                    nextLine = parser.getCurrentLineNumber() + 1;
                }
            }
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalArgumentException("CSV ilegível: use UTF-8 e confira as aspas e os delimitadores");
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV sem títulos");
        var counts = new HashMap<List<String>, Integer>();
        rows.forEach(row -> counts.merge(row.values().subList(0, 2), 1, Integer::sum));
        return new Preview(maxRows, rows.stream().map(row -> {
            if (counts.get(row.values().subList(0, 2)) == 1) return row;
            var errors = new ArrayList<>(row.errors()); errors.add("Cedente/título repetido no arquivo");
            return new Row(row.line(), row.values(), null, errors);
        }).toList());
    }

    private Row validate(long line, List<String> values) {
        var errors = new ArrayList<String>();
        var assignor = assignors.findByCode(values.get(0));
        if (assignor.isEmpty()) errors.add("cedente_codigo: código desconhecido");
        ReceivableType type = null;
        PaymentCurrency currency = null;
        LocalDate due = null;
        try { type = ReceivableType.valueOf(values.get(2)); }
        catch (IllegalArgumentException e) { errors.add("tipo: use DUPLICATA_MERCANTIL ou CHEQUE_PRE_DATADO"); }
        if (!values.get(3).matches("[0-9]{1,17}(,[0-9]{1,2})?")) errors.add("valor_face: use até 17 inteiros e 2 decimais com vírgula, sem milhar");
        try { due = LocalDate.parse(values.get(4), DATE); if (due.getYear() < 1 || due.getYear() > 9999) throw new java.time.DateTimeException("Ano inválido"); }
        catch (java.time.DateTimeException e) { errors.add("vencimento: data inválida; use DD/MM/AAAA"); }
        try { currency = PaymentCurrency.valueOf(values.get(5)); }
        catch (IllegalArgumentException e) { errors.add("moeda_pagamento: use BRL ou USD"); }
        if (values.get(1).isBlank() || values.get(1).length() > 100) errors.add("titulo_codigo: obrigatório, até 100 caracteres");
        CreateReceivable request = null;
        if (errors.isEmpty()) {
            request = new CreateReceivable(assignor.orElseThrow().getId(), values.get(1), type, values.get(3).replace(',', '.'), due, currency);
            validator.validate(request).forEach(v -> errors.add(v.getPropertyPath() + ": " + v.getMessage()));
            if (errors.isEmpty()) {
                try { credit.validateRegistration(request); }
                catch (IllegalArgumentException | BusinessException e) { errors.add(e.getMessage()); }
                var existing = receivables.findByAssignorIdAndTitleCode(request.assignorId(), request.titleCode());
                if (existing.isPresent()) {
                    var old = existing.get();
                    boolean same = old.getType() == type && old.getPaymentCurrency() == currency
                            && old.getDueDate().equals(due) && old.getFaceValue().compareTo(new java.math.BigDecimal(request.faceValue())) == 0;
                    errors.add(same ? "Duplicado: título já cadastrado" : "Conflito: título já cadastrado com dados diferentes; nada será sobrescrito");
                }
            }
        }
        return new Row(line, values, errors.isEmpty() ? request : null, errors);
    }
}
