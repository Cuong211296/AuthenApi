package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.configuration.ShopProperties;
import com.example.identifyservice.entity.*;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.CartRepository;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter CODE_DATE =
            DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    OrderRepository orderRepository;
    CartRepository cartRepository;
    ProductVariantRepository variantRepository;
    ShippingService shippingService;
    CurrentUserService currentUserService;
    ShopProperties shopProperties;
    ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        User user = currentUserService.requireUser();
        Cart cart = cartRepository.findByUser(user).orElseThrow(() -> new AppException(ErrorCode.CART_EMPTY));
        if (cart.getItems().isEmpty()) throw new AppException(ErrorCode.CART_EMPTY);
        ShippingRate rate = shippingService.requireRate(request.province());

        Instant now = Instant.now();
        boolean momo = request.paymentMethod() == PaymentMethod.MOMO;
        Order order = Order.builder()
                .code(newCode(now))
                .user(user)
                .status(momo ? OrderStatus.PENDING_PAYMENT : OrderStatus.PENDING_CONFIRM)
                .paymentMethod(request.paymentMethod())
                .paymentStatus(PaymentStatus.UNPAID)
                .receiverName(request.receiverName().trim())
                .phone(request.phone().trim())
                .email(request.email().trim())
                .address(request.address().trim())
                .province(rate.getProvince())
                .note(request.note() == null || request.note().isBlank() ? null : request.note().trim())
                .shippingFee(rate.getFee())
                .expiresAt(momo ? now.plus(shopProperties.orderExpiryMinutes(), ChronoUnit.MINUTES) : null)
                .build();

        long subtotal = 0;
        for (CartItem cartItem : cart.getItems()) {
            ProductVariant variant = cartItem.getVariant();
            Product product = variant.getProduct();
            if (!variant.isActive() || !product.isActive()) throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
            if (variantRepository.decrementStock(variant.getId(), cartItem.getQuantity()) == 0)
                throw new AppException(ErrorCode.OUT_OF_STOCK);
            long unitPrice = variant.effectivePrice();
            subtotal += unitPrice * cartItem.getQuantity();
            order.getItems().add(OrderItem.builder().order(order).variantId(variant.getId())
                    .productName(product.getName()).size(variant.getSize()).color(variant.getColor())
                    .unitPrice(unitPrice).quantity(cartItem.getQuantity()).build());
        }
        order.setSubtotal(subtotal);
        order.setTotal(subtotal + rate.getFee());
        orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        if (!momo) eventPublisher.publishEvent(new OrderConfirmedEvent(order.getId()));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> myOrders(int page, int size) {
        User user = currentUserService.requireUser();
        return PageResponse.of(orderRepository.findByUser(user, pageable(page, size)).map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(String code) {
        return OrderResponse.from(requireOwnedOrder(code));
    }

    @Transactional(readOnly = true)
    public Order requireOwnedOrder(String code) {
        User user = currentUserService.requireUser();
        Order order = orderRepository.findByCode(code).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUser().getId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        return order;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> adminList(OrderStatus status, int page, int size) {
        var pageable = pageable(page, size);
        var result = status == null ? orderRepository.findAll(pageable) : orderRepository.findByStatus(status, pageable);
        return PageResponse.of(result.map(OrderResponse::from));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse adminUpdateStatus(String code, OrderStatus to) {
        Order order = orderRepository.findByCode(code).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        OrderStatus from = order.getStatus();
        if (!from.canTransitionTo(to)) throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        String id = order.getId();

        if (from == OrderStatus.PENDING_PAYMENT) {
            // only PENDING_PAYMENT -> CANCELLED is allowed; atomic and restocks exactly once
            if (!cancelPendingPayment(id, PaymentStatus.UNPAID)) throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        } else {
            List<OrderItem> lines = List.copyOf(order.getItems()); // load before the clearing update
            if (orderRepository.transitionStatus(id, from, to) == 0)
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
            if (to == OrderStatus.CANCELLED)
                lines.forEach(i -> variantRepository.incrementStock(i.getVariantId(), i.getQuantity()));
            if (to == OrderStatus.COMPLETED)
                orderRepository.markCodPaid(id, Instant.now(), PaymentMethod.COD, PaymentStatus.PAID, PaymentStatus.UNPAID);
        }
        return OrderResponse.from(orderRepository.findByCode(code).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND)));
    }

    /**
     * Closes an unpaid MoMo order and returns its stock. Returns false when the order was no longer
     * PENDING_PAYMENT (already paid or cancelled), so it is safe to call repeatedly.
     */
    @Transactional
    public boolean cancelPendingPayment(String orderId, PaymentStatus finalPaymentStatus) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return false;
        List<OrderItem> lines = List.copyOf(order.getItems());
        if (orderRepository.cancelPending(orderId, finalPaymentStatus) == 0) return false;
        lines.forEach(i -> variantRepository.incrementStock(i.getVariantId(), i.getQuantity()));
        return true;
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String newCode(Instant now) {
        StringBuilder sb = new StringBuilder("DH").append(CODE_DATE.format(now));
        for (int i = 0; i < 6; i++)
            sb.append(CODE_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }
}
