package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.event.OrderMailListener;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.testsupport.RecordingMailSender;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderMailServiceTest {
    @Autowired OrderMailService mailService;
    @Autowired RecordingMailSender sender;
    @Autowired TestDataFactory data;
    @Autowired OrderRepository orders;

    Order order;

    @BeforeEach
    void setUp() {
        sender.reset();
        var product = data.product("mail-tee", 150_000, true);
        ProductVariant v = data.variant(product, "M", "navy", 5, null);
        order = data.pendingMomoOrder(data.user("mailer"), v, 2, Instant.now());
        order.setReceiverName("<script>alert(1)</script> An");
        orders.save(order);
    }

    @Test
    void buildsVietnameseEmailWithOrderDetailsAndEscapesUserText() throws Exception {
        mailService.sendOrderConfirmation(order.getId());

        assertThat(sender.sent).hasSize(1);
        MimeMessage message = sender.sent.get(0);
        assertThat(message.getSubject()).contains(order.getCode());
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("test@example.com");
        String body = message.getContent().toString();
        assertThat(body).contains("Product mail-tee").contains("M").contains("navy")
                .contains("325.000")           // 2 x 150.000 + 25.000 shipping
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void sendFailurePropagatesFromServiceButListenerSwallowsIt() {
        sender.failing = true;
        assertThatThrownBy(() -> mailService.sendOrderConfirmation(order.getId())).isInstanceOf(RuntimeException.class);

        OrderMailService broken = mock(OrderMailService.class);
        doThrow(new RuntimeException("boom")).when(broken).sendOrderConfirmation("x");
        assertThatCode(() -> new OrderMailListener(broken).on(new OrderConfirmedEvent("x"))).doesNotThrowAnyException();
    }
}
