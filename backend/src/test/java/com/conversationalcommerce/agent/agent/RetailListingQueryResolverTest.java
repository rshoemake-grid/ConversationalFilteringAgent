package com.conversationalcommerce.agent.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetailListingQueryResolverTest {

    @Test
    void preferUserWhenOrthogonalToStaleRefinedQuery() {
        assertThat(RetailListingQueryResolver.preferUserOrApiExpandedQuery("uncle bens", "rice", "SIMPLE_PRODUCT_SEARCH"))
                .isEqualTo("uncle bens");
    }

    @Test
    void preferApiRefinedWhenUserWrapsRefinedInPurchaseIntent() {
        assertThat(RetailListingQueryResolver.preferUserOrApiExpandedQuery(
                        "I want to buy some rice", "rice", "SIMPLE_PRODUCT_SEARCH"))
                .isEqualTo("rice");
        assertThat(RetailListingQueryResolver.preferUserOrApiExpandedQuery(
                        "I'd like to buy some rice", "rice", "SIMPLE_PRODUCT_SEARCH"))
                .isEqualTo("rice");
        assertThat(RetailListingQueryResolver.preferUserOrApiExpandedQuery(
                        "i want rice", "rice", "INTENT_REFINEMENT"))
                .isEqualTo("rice");
    }

    @Test
    void preferUserWhenWordsBeyondRefinedAreNotFiller() {
        assertThat(RetailListingQueryResolver.preferUserOrApiExpandedQuery("jumbo shrimp", "shrimp", "SIMPLE_PRODUCT_SEARCH"))
                .isEqualTo("jumbo shrimp");
    }

    @Test
    void preferApiWhenApiExpandsUserQuery() {
        assertThat(RetailListingQueryResolver.preferUserOrApiExpandedQuery("shrimp", "fresh jumbo shrimp", "SIMPLE_PRODUCT_SEARCH"))
                .isEqualTo("fresh jumbo shrimp");
    }

    @Test
    void preferUserForProductDetailsWhenContained() {
        assertThat(RetailListingQueryResolver.preferUserOrApiExpandedQuery("uncle bens rice", "rice", "PRODUCT_DETAILS"))
                .isEqualTo("uncle bens rice");
    }

    @Test
    void chooseSearchQuery_keepsPreviousRefinedWhenStorageRecovery() {
        assertThat(RetailListingQueryResolver.chooseSearchQuery("S", "rice", true, false, "SIMPLE_PRODUCT_SEARCH"))
                .isEqualTo("rice");
    }

    @Test
    void chooseSearchQuery_mergesSingleTokenRefinementWithSessionCategoryWhenRetailFilterActive() {
        String filter = "attributes.stockType: ANY(\"DRY_STORAGE\", \"D\")";
        assertThat(RetailListingQueryResolver.chooseSearchQuery(
                        "Asian",
                        "rice",
                        false,
                        false,
                        "SIMPLE_PRODUCT_SEARCH",
                        "rice",
                        filter))
                .isEqualTo("rice Asian");
    }

    @Test
    void chooseSearchQuery_skipsMergeWhenNoRetailSessionFilter() {
        assertThat(RetailListingQueryResolver.chooseSearchQuery(
                        "Asian",
                        "rice",
                        false,
                        false,
                        "SIMPLE_PRODUCT_SEARCH",
                        "rice",
                        null))
                .isEqualTo("Asian");
    }

    @Test
    void chooseSearchQuery_skipsMergeForMultiTokenUserMessage() {
        String filter = "attributes.stockType: ANY(\"DRY_STORAGE\", \"D\")";
        assertThat(RetailListingQueryResolver.chooseSearchQuery(
                        "Asian style",
                        "rice",
                        false,
                        false,
                        "SIMPLE_PRODUCT_SEARCH",
                        "rice",
                        filter))
                .isEqualTo("Asian style");
    }

    @Test
    void chooseSearchQuery_skipsMergeWhenApiRefinedOrthogonalToSessionCategory() {
        String filter = "attributes.stockType: ANY(\"DRY_STORAGE\", \"D\")";
        assertThat(RetailListingQueryResolver.chooseSearchQuery(
                        "Asian",
                        "whole milk",
                        false,
                        false,
                        "SIMPLE_PRODUCT_SEARCH",
                        "rice",
                        filter))
                .isEqualTo("Asian");
    }
}
