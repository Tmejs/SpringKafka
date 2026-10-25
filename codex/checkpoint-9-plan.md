# Checkpoint 9: integrate user simplifications

Preserve the three user edits in their own commit. Remove the raw publication size limit and codec timestamp policy as requested. Update unit/integration tests and current API documentation accordingly.

Keep poison-message recovery: translate PostgreSQL SQLSTATE 22008 (datetime overflow) during insertion into InvalidEventException. Other integrity failures and outages retain their retry behavior. This avoids broad classification of all database integrity errors as bad messages.

Verify the new mapping first with a failing service test, then run all unit and real Kafka/PostgreSQL integration tests. Preserve batch-tail, offsets, and failed-DLQ tests. Generate coverage in target/site/jacoco. Obtain independent review, record evidence, push checkpoint, merge --no-ff, verify merged main, push and compare SHAs.
