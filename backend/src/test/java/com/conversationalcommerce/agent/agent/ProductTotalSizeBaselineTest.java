package com.conversationalcommerce.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTotalSizeBaselineTest {

    @Test
    void leavesUnknownTotalUnchanged() {
        var ctx = ctxWithFilter();
        ctx.put("previousProductTotalSize", 5608L);
        var r = ProductTotalSizeBaseline.adjustNarrowingDisplayTotal(-1, false, ctx);
        assertThat(r.totalSize()).isEqualTo(-1);
        assertThat(r.totalSizeIsApproximate()).isFalse();
    }

    @Test
    void noPreviousFilter_doesNotSubstitute() {
        var ctx = new HashMap<String, Object>();
        ctx.put("previousProductTotalSize", 5608L);
        var r = ProductTotalSizeBaseline.adjustNarrowingDisplayTotal(508, false, ctx);
        assertThat(r.totalSize()).isEqualTo(508);
    }

    @Test
    void noPreviousTotal_doesNotSubstitute() {
        var ctx = ctxWithFilter();
        var r = ProductTotalSizeBaseline.adjustNarrowingDisplayTotal(508, false, ctx);
        assertThat(r.totalSize()).isEqualTo(508);
    }

    @Test
    void replacesWithLargerPriorTotalWhenNarrowing() {
        var ctx = ctxWithFilter();
        ctx.put("previousProductTotalSize", 5608L);
        var r = ProductTotalSizeBaseline.adjustNarrowingDisplayTotal(508, false, ctx);
        assertThat(r.totalSize()).isEqualTo(5608L);
        assertThat(r.totalSizeIsApproximate()).isFalse();
    }

    @Test
    void mergesApproximateFlagFromPrior() {
        var ctx = ctxWithFilter();
        ctx.put("previousProductTotalSize", 5608L);
        ctx.put("previousProductTotalSizeIsApproximate", true);
        var r = ProductTotalSizeBaseline.adjustNarrowingDisplayTotal(508, false, ctx);
        assertThat(r.totalSize()).isEqualTo(5608L);
        assertThat(r.totalSizeIsApproximate()).isTrue();
    }

    @Test
    void doesNotIncreaseDisplayTotalWhenRetailReturnsLarger() {
        var ctx = ctxWithFilter();
        ctx.put("previousProductTotalSize", 100L);
        var r = ProductTotalSizeBaseline.adjustNarrowingDisplayTotal(508, false, ctx);
        assertThat(r.totalSize()).isEqualTo(508);
    }

    private static Map<String, Object> ctxWithFilter() {
        var m = new HashMap<String, Object>();
        m.put("previousProductFilter", "attributes.stockType: ANY(\"DRY_STORAGE\", \"D\")");
        return m;
    }
}
