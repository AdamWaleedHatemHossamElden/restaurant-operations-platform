package com.adam.restaurantoperations.common.demo;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DemoDataInitializerTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DemoDataInitializer initializer = new DemoDataInitializer(dataSource, jdbcTemplate);

    @Test
    void existingMarkerMakesInitializationIdempotent() throws Exception {
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).willReturn(1);

        initializer.run(mock(org.springframework.boot.ApplicationArguments.class));

        verifyNoInteractions(dataSource);
    }

    @Test
    void missingAdministratorFailsBeforeRunningTheScript() {
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).willReturn(0);

        assertThatThrownBy(() -> initializer.run(mock(org.springframework.boot.ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Development showcase data requires an enabled bootstrap administrator");
        verifyNoInteractions(dataSource);
    }
}
