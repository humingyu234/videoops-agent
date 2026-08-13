package org.dromara.aivideo.user.studio.domain.bo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class QuestionnaireAnswerBoValidationTest {

    @Test
    void rejectsEmptyAnswerValuesAndLabels() {
        QuestionnaireAnswerBo answer = new QuestionnaireAnswerBo(
            "audience", "主要面向谁？", List.of(), List.of());

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(answer);

            assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("selectedValues", "selectedLabels");
        }
    }

    @Test
    void rejectsAnswerValuesAndLabelsWithDifferentCounts() {
        QuestionnaireAnswerBo answer = new QuestionnaireAnswerBo(
            "audience", "主要面向谁？", List.of("students", "parents"), List.of("学生"));

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(answer);

            assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("已选答案值与标签必须一一对应");
        }
    }
}
