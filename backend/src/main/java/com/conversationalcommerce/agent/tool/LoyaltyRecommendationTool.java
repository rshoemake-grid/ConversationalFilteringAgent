package com.conversationalcommerce.agent.tool;

import com.google.adk.tools.Annotations.Schema;

import java.util.Map;

/**
 * Example specialist tool for Approach B: Provides loyalty and recommendation tips.
 * The ADK orchestrator can use this to enhance responses with cross-sell suggestions.
 */
public class LoyaltyRecommendationTool {

    @Schema(description = "Get loyalty program benefits and personalized recommendations for a customer. Use when the user asks about rewards, loyalty points, or recommendations.")
    public static Map<String, Object> getLoyaltyRecommendations(
            @Schema(description = "Optional: product category or interest for personalized recommendations", name = "category")
            String category) {
        String tips = "As a valued customer, you earn 1 point per dollar spent. ";
        if (category != null && !category.isEmpty()) {
            tips += "For " + category + ", we recommend checking our bestsellers - members get 10% off! ";
        }
        tips += "Sign up for our loyalty program to unlock exclusive deals.";
        return Map.of(
                "status", "success",
                "tips", tips,
                "memberDiscount", "10%"
        );
    }
}
