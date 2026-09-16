package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.application.CsvImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
public class CsvImportController {
    private final CsvImportService service;
    public CsvImportController(CsvImportService service) { this.service = service; }

    @PostMapping(value = "/receivables/import-preview", consumes = "multipart/form-data")
    public CsvImportService.Preview preview(@RequestParam("file") MultipartFile file) throws IOException {
        return service.preview(file.getBytes());
    }
}
