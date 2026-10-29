package eightjbbm.keepgo.outingwishlist.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddOutingCourseRequest(@NotNull @Positive Long outingCourseId) {}
