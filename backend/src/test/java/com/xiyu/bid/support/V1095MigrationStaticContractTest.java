package com.xiyu.bid.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V1095MigrationStaticContractTest {

    @Test
    void v1095MustGuardExistingEmployeeNumberColumnAndMissingIndex() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration-mysql/V1095__add_users_employee_number.sql"
        ));

        assertThat(sql)
                .contains("information_schema.COLUMNS")
                .contains("COLUMN_NAME = 'employee_number'")
                .contains("information_schema.STATISTICS")
                .contains("INDEX_NAME = 'idx_users_employee_number'");
    }

    @Test
    void u1095MustGuardMissingEmployeeNumberIndexAndColumn() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/rollback/migration-mysql/U1095__add_users_employee_number.sql"
        ));

        assertThat(sql)
                .contains("information_schema.STATISTICS")
                .contains("INDEX_NAME = 'idx_users_employee_number'")
                .contains("information_schema.COLUMNS")
                .contains("COLUMN_NAME = 'employee_number'");
    }
}
