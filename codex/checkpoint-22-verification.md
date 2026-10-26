# Checkpoint 22 verification: minimal Compose environment

Date: 2026-10-26
Baseline: 4ea1ecff439e835faf66ef8c29d2c6650fb4b672

Compose now loads app settings from .env and retains four explicit connection settings for Docker-internal Kafka/PostgreSQL. .env.example contains only Basic username/password and PostgreSQL password. Application defaults remain in application.yml; optional Kafka/JWT settings in .env are passed through automatically. Removed unused transaction-state tuning and initial group delay tuning from the local broker. Updated the README configuration source and removed the obsolete group-variable forwarding finding from the study guide. Existing user .env is unchanged.

Verification: Compose config parses; rendered Basic configuration has the three required credential values and correct service connections; JWT without Basic credentials and custom Kafka group/max-poll overrides render correctly; missing PostgreSQL password fails with the expected interpolation error. All checks capture configuration internally without printing credentials. .env remains ignored. git diff --check passes.

Complete Maven verification on the unchanged Java source baseline: mvn verify PASS,85unit/27integration tests, zero failures/errors/skips; /tmp/nordea-checkpoint-22-code-verify.log. No Maven-consumed files change in this checkpoint. Independent checkpoint_22_review found no Critical or Important findings. Standard Compose env_file precedence applies: edit .env for app settings; shell-only exports are not automatically forwarded.

Push/compare checkpoint, merge no-ff into clean synchronized main, rerun Compose rendering checks, recreate the local app using the verified application artifact, check public readiness/Swagger and protected/authenticated API access, then push/compare main. Preserve branch and existing data/services.
