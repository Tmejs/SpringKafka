# Checkpoint 10: interview demonstration and contract validation

Preserve the remaining user import cleanup separately. Add a curl-based Bash demo script with environment credentials, unique event IDs, bounded asynchronous reads, explicit expected HTTP statuses, and commands for the main successful/failure scenarios. Document local Compose startup, a short presentation sequence, DLQ inspection, real batch proof and limitations.

Validate OpenAPI 3.0.3 and AsyncAPI 2.6.0 using schema-aware tools, record versions/results, and correct contract defects if found. Verify scripts against a separately named disposable Compose stack on unused ports; do not disturb the older stack using 8080/9092. Run the complete Maven gate and independent review before the usual checkpoint push, no-ff merge, merged verification and main push. No new hosted CI or application features.
