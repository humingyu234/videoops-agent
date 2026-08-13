package org.dromara.aivideo.user.discovery.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;

import java.time.LocalDateTime;
import java.util.List;

/** 用户端发现页工作流模板卡片。 */
public record WorkflowTemplateCardVo(
    String templateId,
    String title,
    String summary,
    String channel,
    CategoryVo category,
    List<TagVo> tags,
    MediaVo cover,
    @JsonInclude(JsonInclude.Include.NON_NULL) MediaVo preview,
    @JsonInclude(JsonInclude.Include.NON_NULL) String usageCount,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer estimatedDurationSeconds,
    String enabledAt
) {
    public static WorkflowTemplateCardVo from(WorkflowTemplateDTOs.PublicCard source) {
        return new WorkflowTemplateCardVo(
            source.templateId(), source.title(), source.summary(), source.channel(),
            categoryFrom(source.category()), tagsFrom(source.tags()), mediaFrom(source.cover()),
            mediaFrom(source.preview()), source.usageCount(), source.estimatedDurationSeconds(),
            dateTimeFrom(source.enabledAt()));
    }

    static CategoryVo categoryFrom(WorkflowTemplateDTOs.Category source) {
        return source == null ? null : new CategoryVo(source.categoryCode(), source.label());
    }

    static List<TagVo> tagsFrom(List<WorkflowTemplateDTOs.Tag> source) {
        return source == null ? List.of() : source.stream()
            .map(tag -> new TagVo(tag.tagCode(), tag.label()))
            .toList();
    }

    static MediaVo mediaFrom(WorkflowTemplateDTOs.Media source) {
        return source == null ? null : new MediaVo(
            source.mediaId(), source.mediaType(), source.url(), source.posterUrl(),
            source.width(), source.height(), source.alt());
    }

    static List<MediaVo> mediaFrom(List<WorkflowTemplateDTOs.Media> source) {
        return source == null ? List.of() : source.stream().map(WorkflowTemplateCardVo::mediaFrom).toList();
    }

    static String dateTimeFrom(LocalDateTime source) {
        return source == null ? null : source.toString();
    }

    public record CategoryVo(String categoryCode, String label) {
    }

    public record TagVo(String tagCode, String label) {
    }

    public record MediaVo(
        String mediaId,
        String mediaType,
        String url,
        @JsonInclude(JsonInclude.Include.NON_NULL) String posterUrl,
        int width,
        int height,
        String alt
    ) {
    }
}
