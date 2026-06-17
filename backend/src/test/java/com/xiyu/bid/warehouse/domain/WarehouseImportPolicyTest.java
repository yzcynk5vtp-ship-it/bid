package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseImportPolicyTest {

    @Test
    @DisplayName("validateHeader 对完全匹配的表头返回空错误列表")
    void exactMatchHeaderIsValid() {
        List<String> errors = WarehouseImportPolicy.validateHeader(WarehouseImportPolicy.TEMPLATE_HEADERS);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("validateHeader 容忍全角括号、多余空格和末尾 * 标记")
    void normalizedHeaderIsValid() {
        String[] relaxed = new String[WarehouseImportPolicy.TEMPLATE_HEADERS.length];
        for (int i = 0; i < relaxed.length; i++) {
            relaxed[i] = WarehouseImportPolicy.TEMPLATE_HEADERS[i]
                    .replace("(", "（").replace(")", "）")
                    + "  ";
        }
        List<String> errors = WarehouseImportPolicy.validateHeader(relaxed);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("validateHeader 对列数不足返回错误")
    void tooFewColumnsReturnsError() {
        List<String> errors = WarehouseImportPolicy.validateHeader(new String[]{"仓库名称"});
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("列数不足");
    }

    @Test
    @DisplayName("validateHeader 对真正不匹配的列名返回错误")
    void mismatchedHeaderReturnsError() {
        String[] bad = new String[WarehouseImportPolicy.TEMPLATE_HEADERS.length];
        System.arraycopy(WarehouseImportPolicy.TEMPLATE_HEADERS, 0, bad, 0, bad.length);
        bad[0] = "完全错误的列名";
        List<String> errors = WarehouseImportPolicy.validateHeader(bad);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("第 1 列");
    }

    @Test
    @DisplayName("normalizeHeader 去除空格、统一全角符号、转小写、去末尾 *")
    void normalizeHeaderBehavior() {
        assertThat(WarehouseImportPolicy.normalizeHeader("  仓库名称*  ")).isEqualTo("仓库名称");
        assertThat(WarehouseImportPolicy.normalizeHeader("仓库名称")).isEqualTo("仓库名称");
        assertThat(WarehouseImportPolicy.normalizeHeader("仓库名称***")).isEqualTo("仓库名称");
        assertThat(WarehouseImportPolicy.normalizeHeader("是否有产权证（是/否）"))
                .isEqualTo("是否有产权证(是/否)");
        assertThat(WarehouseImportPolicy.normalizeHeader("AREA")).isEqualTo("area");
    }
}
