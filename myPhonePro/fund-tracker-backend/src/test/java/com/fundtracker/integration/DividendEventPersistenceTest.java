package com.fundtracker.integration;

import com.fundtracker.model.entity.DividendEvent;
import com.fundtracker.model.enums.EventStatus;
import com.fundtracker.model.enums.EventType;
import com.fundtracker.repository.DividendEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分红事件持久化回归测试（2026-11-10）。
 * <p>
 * 背景：10-29 给 dividend_events 加 {@code @Version} 乐观锁后，所有「新建」路径都
 * 手动设置了 {@code @Id @GeneratedValue} 的 ID，导致 Hibernate 6 在 persist 时判定为
 * 游离实体（Detached entity with generated id ... uninitialized version value 'null'）
 * 并抛异常。表现为：新分红事件一条都建不出来（008163 的 2026-11-05 分红丢失），
 * 且手动添加分红事件、新建持仓同样 500。
 * <p>
 * 本测试锁死两件事：
 * <ol>
 *   <li>不手动设 ID 时，事件能正常持久化，id/version 由 JPA 回填；</li>
 *   <li>手动设 ID 的写法确实会被 Hibernate 拒绝——防止有人「修复」时又加回去。</li>
 * </ol>
 * 用 H2 内存库（application-test.yml），不依赖本机 MySQL。
 */
@SpringBootTest
@ActiveProfiles("test")
class DividendEventPersistenceTest {

    @Autowired
    private DividendEventRepository dividendEventRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    private DividendEvent newEvent() {
        return DividendEvent.builder()
                .userId("user-test")
                .holdingId("holding-test")
                .holdingName("测试持仓")
                .type(EventType.payout)
                .date(LocalDate.of(2026, 11, 6))
                .amount(new BigDecimal("12.34"))
                .status(EventStatus.pending)
                .description("分红发放 · 每份 0.0050 元")
                .participated(true)
                .converted(false)
                .build();
    }

    @Test
    @DisplayName("新建分红事件：不手动设 ID 时能持久化，id 与 version 由 JPA 回填")
    void persistNewEventWithoutManualId() {
        DividendEvent saved = dividendEventRepository.saveAndFlush(newEvent());

        assertNotNull(saved.getId(), "id 应由 @GeneratedValue 生成");
        assertNotNull(saved.getVersion(), "version 应由 @Version 初始化为 0");
        assertEquals(0L, saved.getVersion());

        DividendEvent reloaded = dividendEventRepository.findById(saved.getId()).orElseThrow();
        assertEquals(EventType.payout, reloaded.getType());
        assertEquals(0, new BigDecimal("12.34").compareTo(reloaded.getAmount()));
    }

    @Test
    @DisplayName("回归：手动设置 @GeneratedValue 的 ID + version=null 会被 Hibernate 拒绝")
    void persistNewEventWithManualIdFails() {
        DividendEvent event = newEvent();
        event.setId(UUID.randomUUID().toString());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> dividendEventRepository.saveAndFlush(event),
                "手动设 ID 的写法必须失败，否则说明这条回归防线失效了");

        String message = deepestMessage(ex);
        assertTrue(message.contains("version") || message.contains("Detached"),
                "异常信息应指向 version/游离实体问题，实际为：" + message);
    }

    /** 取异常链最深层的 message（Hibernate 的 PropertyValueException 常被 Spring 包装） */
    private String deepestMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return String.valueOf(cur.getMessage());
    }
}
