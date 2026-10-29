package eightjbbm.keepgo.recommendation.outingcriteria;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public record OutingCriteriaProperties(String name) {}
