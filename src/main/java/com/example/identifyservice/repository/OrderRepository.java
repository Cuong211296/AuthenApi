package com.example.identifyservice.repository;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    /** Locking current read (sees committed rows even under REPEATABLE READ snapshots). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ShopOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") String id);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(String id);

    Optional<Order> findByCode(String code);
    Page<Order> findByUser(User user, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant time);

    /** Atomic "pay once": succeeds only while the order is still PENDING_PAYMENT. Returns rows changed (0 or 1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ShopOrder o
            set o.status = com.example.identifyservice.enums.OrderStatus.PENDING_CONFIRM,
                o.paymentStatus = com.example.identifyservice.enums.PaymentStatus.PAID,
                o.paidAt = :now
            where o.id = :id
              and o.status = com.example.identifyservice.enums.OrderStatus.PENDING_PAYMENT
            """)
    int markPaid(@Param("id") String id, @Param("now") Instant now);

    /** Atomic "close unpaid order": succeeds only while the order is still PENDING_PAYMENT. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ShopOrder o
            set o.status = com.example.identifyservice.enums.OrderStatus.CANCELLED,
                o.paymentStatus = :paymentStatus
            where o.id = :id
              and o.status = com.example.identifyservice.enums.OrderStatus.PENDING_PAYMENT
            """)
    int cancelPending(@Param("id") String id, @Param("paymentStatus") PaymentStatus paymentStatus);

    /** Atomic status change: succeeds only while the order is still in {@code from}. Returns rows changed (0 or 1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShopOrder o set o.status = :to where o.id = :id and o.status = :from")
    int transitionStatus(@Param("id") String id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);

    /** Marks a COD order paid exactly once (only while UNPAID). Returns rows changed (0 or 1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ShopOrder o
            set o.paymentStatus = :paid, o.paidAt = :now
            where o.id = :id and o.paymentMethod = :method and o.paymentStatus = :unpaid
            """)
    int markCodPaid(@Param("id") String id, @Param("now") Instant now,
                    @Param("method") com.example.identifyservice.enums.PaymentMethod method,
                    @Param("paid") PaymentStatus paid, @Param("unpaid") PaymentStatus unpaid);
}
