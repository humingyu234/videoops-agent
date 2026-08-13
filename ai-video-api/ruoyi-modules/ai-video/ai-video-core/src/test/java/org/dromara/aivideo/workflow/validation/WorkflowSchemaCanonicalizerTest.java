package org.dromara.aivideo.workflow.validation;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class WorkflowSchemaCanonicalizerTest {

    private final WorkflowSchemaCanonicalizer canonicalizer =
        new WorkflowSchemaCanonicalizer(JsonMapper.builder().build());

    @Test
    void canonicalizesOnlyFrozenSchemaFieldsAndDerivesRequiredInputs() {
        WorkflowSchemaCanonicalizer.CanonicalSchema result = canonicalizer.canonicalize("""
            {
              "fields": [
                {
                  "required": true,
                  "valueType": "string",
                  "control": "text",
                  "label": "提示词",
                  "inputKey": "prompt"
                },
                {
                  "inputKey": "style",
                  "label": "风格",
                  "control": "select",
                  "valueType": "string",
                  "required": false,
                  "options": [
                    { "label": "电影感", "value": "cinematic" },
                    { "value": "clean", "label": "简洁" }
                  ]
                }
              ],
              "schemaVersion": "workflow-form-1"
            }
            """);

        assertThat(result.canonicalJson()).isEqualTo(
            "{\"fields\":[{\"control\":\"text\",\"inputKey\":\"prompt\",\"label\":\"提示词\",\"required\":true,\"valueType\":\"string\"},{\"control\":\"select\",\"inputKey\":\"style\",\"label\":\"风格\",\"options\":[{\"label\":\"电影感\",\"value\":\"cinematic\"},{\"label\":\"简洁\",\"value\":\"clean\"}],\"required\":false,\"valueType\":\"string\"}],\"schemaVersion\":\"workflow-form-1\"}");
        assertThat(result.schemaHash()).matches("sha256:[0-9a-f]{64}");
        assertThat(result.requiredInputs()).containsExactly(
            new WorkflowSchemaCanonicalizer.RequiredInput(null, "提示词", "string", null, true),
            new WorkflowSchemaCanonicalizer.RequiredInput(null, "风格", "string", null, false));
    }

    @Test
    void rejectsUnknownFieldsDuplicateKeysInvalidPairsAndUnsafeFileDefaults() {
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[{
              "inputKey":"prompt","label":"提示词","control":"text","valueType":"string",
              "required":true,"providerKind":"runninghub_workflow"
            }]}
            """, "未知属性");
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[
              {"inputKey":"prompt","label":"A","control":"text","valueType":"string","required":true},
              {"inputKey":"prompt","label":"B","control":"text","valueType":"string","required":false}
            ]}
            """, "inputKey 重复");
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[{
              "inputKey":"count","label":"数量","control":"integer","valueType":"string","required":true
            }]}
            """, "控件和值类型不匹配");
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[{
              "inputKey":"style","label":"风格","control":"select","valueType":"string","required":true,
              "options":[]
            }]}
            """, "options");
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[{
              "inputKey":"image","label":"图片","control":"image","valueType":"asset_array","required":true,
              "defaultValue":[{"assetId":"1"}],"constraints":{"assetType":"image","maxItems":1}
            }]}
            """, "默认值");
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[{
              "inputKey":"image","label":"图片","control":"image","valueType":"asset_array","required":true,
              "constraints":{"assetType":"video"}
            }]}
            """, "assetType");
    }

    @Test
    void sortsObjectKeysByUnicodeCodeUnitsButPreservesArrayOrder() {
        WorkflowSchemaCanonicalizer.CanonicalSchema result = canonicalizer.canonicalize("""
            {"schemaVersion":"workflow-form-1","fields":[
              {"label":"😀","inputKey":"z","required":false,"valueType":"boolean","control":"boolean"},
              {"label":"","inputKey":"a","required":false,"valueType":"string","control":"textarea"}
            ]}
            """);

        assertThat(result.canonicalJson()).contains("\"label\":\"😀\"")
            .contains("\"label\":\"\"");
        assertThat(result.canonicalJson().indexOf("\"inputKey\":\"z\""))
            .isLessThan(result.canonicalJson().indexOf("\"inputKey\":\"a\""));
    }

    @Test
    void rejectsSelectionDefaultsOutsideDeclaredOptions() {
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[{
              "inputKey":"style","label":"Style","control":"select","valueType":"string","required":false,
              "options":[{"value":"clean","label":"Clean"}],"defaultValue":"cinematic"
            }]}
            """, "defaultValue");
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[{
              "inputKey":"styles","label":"Styles","control":"multi_select","valueType":"string_array",
              "required":false,"options":[{"value":"clean","label":"Clean"}],
              "defaultValue":["clean","cinematic"]
            }]}
            """, "defaultValue");
    }

    @Test
    void fileControlsRequireExplicitSingleItemConstraint() {
        assertInvalid("""
            {"schemaVersion":"workflow-form-1","fields":[{
              "inputKey":"image","label":"Image","control":"image","valueType":"asset_array","required":true,
              "constraints":{"assetType":"image"}
            }]}
            """, "maxItems");
    }

    private void assertInvalid(String json, String message) {
        assertThatThrownBy(() -> canonicalizer.canonicalize(json))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining(message);
    }
}
