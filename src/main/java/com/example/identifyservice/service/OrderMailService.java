package com.example.identifyservice.service;

import com.example.identifyservice.configuration.ShopProperties;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.OrderItem;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.OrderRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderMailService {
    JavaMailSender mailSender;
    OrderRepository orderRepository;
    ShopProperties shopProperties;

    @Transactional(readOnly = true)
    public void sendOrderConfirmation(String orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(shopProperties.mailFrom());
            helper.setTo(order.getEmail());
            helper.setSubject("Xác nhận đơn hàng " + order.getCode());
            helper.setText(buildHtml(order), true);
            mailSender.send(message);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Cannot build confirmation email", e);
        }
    }

    private String buildHtml(Order o) {
        StringBuilder rows = new StringBuilder();
        for (OrderItem i : o.getItems()) {
            rows.append("<tr><td>").append(h(i.getProductName())).append("</td><td>").append(h(i.getSize()))
                    .append(" / ").append(h(i.getColor())).append("</td><td>").append(i.getQuantity())
                    .append("</td><td>").append(vnd(i.getUnitPrice() * i.getQuantity())).append("</td></tr>");
        }
        String payment = o.getPaymentMethod() == PaymentMethod.MOMO
                ? "Đã thanh toán qua MoMo" : "Thanh toán khi nhận hàng (COD)";
        return "<h2>Cảm ơn bạn đã đặt hàng!</h2>"
                + "<p>Xin chào " + h(o.getReceiverName()) + ", đơn hàng <b>" + h(o.getCode()) + "</b> đã được ghi nhận.</p>"
                + "<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\"><tr><th>Sản phẩm</th><th>Size / Màu</th>"
                + "<th>SL</th><th>Thành tiền</th></tr>" + rows + "</table>"
                + "<p>Tạm tính: " + vnd(o.getSubtotal()) + "<br>Phí vận chuyển: " + vnd(o.getShippingFee())
                + "<br><b>Tổng cộng: " + vnd(o.getTotal()) + "</b></p>"
                + "<p>" + payment + "</p>"
                + "<p>Giao đến: " + deliveryLine(o) + " - SĐT " + h(o.getPhone()) + "</p>";
    }

    /** address, ward, district, province, skipping missing parts (old orders have no ward or district). */
    private static String deliveryLine(Order o) {
        return java.util.stream.Stream.of(o.getAddress(), o.getWard(), o.getDistrict(), o.getProvince())
                .filter(s -> s != null && !s.isBlank()).map(OrderMailService::h)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String h(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }

    private static String vnd(long amount) {
        return NumberFormat.getInstance(new Locale("vi", "VN")).format(amount) + " ₫";
    }
}
