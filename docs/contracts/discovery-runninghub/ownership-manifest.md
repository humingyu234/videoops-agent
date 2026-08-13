# Discovery RunningHub Single-Execution Ownership Manifest

## Canonical artifacts

- `workflow-form-1.schema.json` is the machine-readable user input wire schema.
- `workflow-form-1.example.json` is the valid canonical wire example.
- `user-wire-forbidden-fields.json` is the deny-list and task/error matrix for every user-visible wire, cache key, DOM value and copy string.

## Ownership boundary

The user contract exposes only discovery templates and creation configuration. The backend derives the sole current execution configuration for `(tenant_id, template_id)` and keeps all RunningHub identifiers, provider credentials and external task identifiers inside the service/infrastructure boundary. User requests provide only `templateId`, `schemaHash` and `inputs`; idempotency is the `Idempotency-Key` header.

## Change authority

Changing a control/value pairing, schema canonicalization, forbidden field, task matrix, error code, or execution ownership rule requires a coordinated update to `docs/API_CONTRACT.md`, `docs/DOMAIN_MODEL.md`, `docs/ASYNC_TASKS.md`, this fixture set and `WorkflowContractFixtureTest`.
