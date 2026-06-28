package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DbBackupController {

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @GetMapping("/db/backup")
    public ResponseEntity<?> backupDatabase() {
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String filename = "fund_tracker_" + dateStr + ".sql.gz";

        File tempFile = null;
        try {
            // 临时文件
            tempFile = File.createTempFile("backup_", ".sql.gz");
            String outPath = tempFile.getAbsolutePath();

            // 构建 mysqldump 命令（使用 sed 去除 DEFINER）
            ProcessBuilder pb = new ProcessBuilder(
                "bash", "-c",
                "mysqldump --single-transaction --default-character-set=utf8mb4 " +
                "--add-drop-table --max-allowed-packet=512M --tz-utc --routines --triggers " +
                "-u'root' -p'" + dbPassword + "' 'fund_tracker' " +
                "2>/dev/null | sed 's/\\/\\*!50013 DEFINER[^*]*\\*\\///g; s/ DEFINER=[^ ]* / /g; s/DEFINER=[^ ]*//g' " +
                "| gzip > '" + outPath + "'"
            );

            Process process = pb.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return ApiResponse.error(500, "备份超时");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return ApiResponse.error(500, "mysqldump 执行失败，退出码: " + exitCode);
            }

            // 检查文件大小
            long fileSize = Files.size(tempFile.toPath());
            if (fileSize < 100) {
                return ApiResponse.error(500, "备份文件异常（太小），请检查数据库连接");
            }

            // 返回文件流
            InputStreamResource resource = new InputStreamResource(new FileInputStream(tempFile));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            headers.setContentLength(fileSize);

            return new ResponseEntity<>(resource, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ApiResponse.error(500, "备份失败: " + e.getMessage());
        } finally {
            // 注意：这里不能删，因为 response 还没写完；由 JVM 的 File.deleteOnExit 或临时目录清理
            if (tempFile != null) {
                tempFile.deleteOnExit();
            }
        }
    }
}
