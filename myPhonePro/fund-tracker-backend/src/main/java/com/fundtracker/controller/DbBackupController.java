package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.service.AdminAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DbBackupController {

    private final AdminAccessService adminAccessService;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @GetMapping("/db/backup")
    public ResponseEntity<?> backupDatabase(HttpServletRequest request) {
        // 安全加固（2026-08）：仅允许管理员访问，应用级 Token（userId=null）或非管理员一律拒绝
        adminAccessService.check((String) request.getAttribute("userId"));

        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String filename = "fund_tracker_" + dateStr + ".sql.gz";

        File tempFile = null;
        File mysqlCnf = null;
        try {
            // 临时 gz 文件
            tempFile = File.createTempFile("backup_", ".sql.gz");
            String outPath = tempFile.getAbsolutePath();

            // 临时 MySQL 配置文件（避免密码在 ps aux 中明文泄露）
            mysqlCnf = File.createTempFile(".my_backup_", ".cnf");
            String cnfContent = "[client]\nuser=" + dbUser + "\npassword=" + dbPassword + "\n";
            Files.writeString(mysqlCnf.toPath(), cnfContent, StandardCharsets.UTF_8);
            // 仅所有者可读写（Unix），Windows 下效果有限但已优于命令行明文
            mysqlCnf.setReadable(true, true);
            mysqlCnf.setWritable(true, true);
            mysqlCnf.setExecutable(false);

            // 构建 mysqldump 命令（使用 --defaults-extra-file 避免密码在进程列表泄露）
            ProcessBuilder pb = new ProcessBuilder(
                "bash", "-c",
                "mysqldump --defaults-extra-file='" + mysqlCnf.getAbsolutePath() + "' " +
                "--single-transaction --default-character-set=utf8mb4 " +
                "--add-drop-table --max-allowed-packet=512M --tz-utc --routines --triggers " +
                "'fund_tracker' " +
                "2>/dev/null | sed 's/\\/\\*!50013 DEFINER[^*]*\\*\\///g; s/ DEFINER=[^ ]* / /g; s/DEFINER=[^ ]*//g' " +
                "| gzip > '" + outPath + "'"
            );

            Process process = pb.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return ResponseEntity.status(500).body(ApiResponse.error(500, "备份超时"));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return ResponseEntity.status(500).body(ApiResponse.error(500, "mysqldump 执行失败，退出码: " + exitCode));
            }

            // 检查文件大小
            long fileSize = Files.size(tempFile.toPath());
            if (fileSize < 100) {
                return ResponseEntity.status(500).body(ApiResponse.error(500, "备份文件异常（太小），请检查数据库连接"));
            }

            // 返回文件流
            InputStreamResource resource = new InputStreamResource(new FileInputStream(tempFile));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            headers.setContentLength(fileSize);

            return new ResponseEntity<>(resource, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(500, "备份失败: " + e.getMessage()));
        } finally {
            if (tempFile != null) tempFile.deleteOnExit();
            if (mysqlCnf != null) mysqlCnf.deleteOnExit();
        }
    }
}
