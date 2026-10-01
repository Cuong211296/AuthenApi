package com.example.identifyservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate 6.4 maps an EnumType.STRING column to a native MySQL enum('GHTK','TABLE'), which ddl-auto update never
 * widens, so shipping_source is forced to VARCHAR (H2 tests cannot see the difference otherwise).
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderShippingSourceMappingTest {
    @Autowired EntityManager em;

    @Test
    void shippingSourceIsForcedToVarchar() throws Exception {
        var field = Order.class.getDeclaredField("shippingSource");
        assertThat(field.getAnnotation(JdbcTypeCode.class)).isNotNull();
        assertThat(field.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.VARCHAR);
        assertThat(field.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        assertThat(field.getAnnotation(Column.class).length()).isEqualTo(10);
    }

    @Test
    void hibernateMetadataUsesAVarcharJdbcTypeForTheColumn() {
        var persister = em.getEntityManagerFactory().unwrap(SessionFactory.class)
                .unwrap(SessionFactoryImplementor.class).getMappingMetamodel().getEntityDescriptor(Order.class);
        var attr = persister.findAttributeMapping("shippingSource");
        assertThat(attr.getSingleJdbcMapping().getJdbcType().getJdbcTypeCode()).isEqualTo(SqlTypes.VARCHAR);
    }
}
