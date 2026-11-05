package eightjbbm.keepgo.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "recommendation")
public record RecommendationProperties(@DefaultValue("5") double candidateRadiusKm) {}
