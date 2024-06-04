package com.desertgm.app.Services.FileService;


import com.desertgm.app.Models.CompanyModel;
import com.desertgm.app.Repositories.CompanyRepository;
import com.desertgm.app.Services.Imports.CompanyService;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileCompanyService {
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    CompanyService companyService;

    private final ConcurrentHashMap<Long, String> taskStatusMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(FileCompanyService.class);

    @Async
    public void readCsvFile(String csvFile, Long taskId) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        int batchSize = 10000;
        int linecount = 0;
        List<CompanyModel> batchList = new ArrayList<>(batchSize);

        logger.info("Iniciando leitura do arquivo CSV: {}", csvFile);

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            CSVReader reader = new CSVReaderBuilder(br)
                    .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                    .build();
            taskStatusMap.put(taskId, "Processando");



            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                linecount++;


                CompanyModel model = companyService.parseLine(nextLine);

                if (model != null) {
                    batchList.add(model);
                }

                if (batchList.size() >= batchSize) {
                    companyService.saveAll(batchList);
                    logger.info("Linhas processadas: {} - Memória utilizada: {} bytes", linecount, Runtime.getRuntime().totalMemory());
                    batchList.clear();
                }
            }

            // Salvar qualquer restante
            if (!batchList.isEmpty()) {
                companyService.saveAll(batchList);
                logger.info("Linhas processadas (restante): {} - Memória utilizada: {} bytes", linecount, Runtime.getRuntime().totalMemory());
            }

            taskStatusMap.put(taskId, "Concluído");
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
            taskStatusMap.put(taskId, "Erro: " + e.getMessage());
        } catch (OutOfMemoryError e) {
            logger.error("Erro de memória após processar {} linhas", linecount);
            taskStatusMap.put(taskId, "Erro: Out of Memory");
        }
    }
}